package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Hue's v2 API, for the built-in effects (fire, candle, ...) that v1 cannot start. It is HTTPS only, with a
 * certificate the bridge signs itself, so the certificate is pinned on first use. Blocking calls:
 * run them off the main thread.
 */
final class HueV2 {
    private final SharedPreferences prefs;
    private final String host;
    private final String base;
    private final String key;
    private final SSLSocketFactory tls;

    HueV2(Context context) throws Exception {
        prefs = context.getSharedPreferences("hue", Context.MODE_PRIVATE);
        host = "https://" + prefs.getString("ip", "");
        base = host + "/clip/v2/resource/light";
        key = prefs.getString("user", "");
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[] {new PinnedTrust()}, null);
        tls = ssl.getSocketFactory();
    }

    /** One of the bridge's built-in effects ("fire", "candle", ...), or "no_effect" to stop. */
    void effect(String v1Id, String effect) throws Exception {
        JSONObject l = find(v1Id);
        if (l == null) throw new IOException("light " + v1Id + " not found");
        https("PUT", "/" + l.getString("id"), new JSONObject()
                .put("on", new JSONObject().put("on", true))
                .put("effects", new JSONObject().put("effect", effect))
                .toString());
    }

    /** colors: 0xRRGGBB along a gradient strip, start to end. Ends any running effect. */
    void gradient(String v1Id, int[] colors) throws Exception {
        gradient(v1Id, colors, -1, 400);
    }

    /** brightness 0-100 (negative keeps it), faded in over fadeMs. */
    void gradient(String v1Id, int[] colors, double brightness, int fadeMs) throws Exception {
        String id = v2Id(v1Id);
        if (id == null) throw new IOException("light " + v1Id + " is not a gradient strip");
        gradientById(id, colors, brightness, fadeMs);
    }

    /** The strip's v2 id when it is a gradient light, else null. Look it up once and keep it. */
    String v2Id(String v1Id) throws Exception {
        JSONObject l = find(v1Id);
        return l != null && l.has("gradient") ? l.getString("id") : null;
    }

    /** Straight to a known v2 id: one PUT, no light list, for a drift step every 1.5 s. */
    void gradientById(String v2Id, int[] colors, double brightness, int fadeMs) throws Exception {
        JSONArray points = new JSONArray();
        for (int c : colors) {
            double[] xy = Colors.rgbToXy((c >> 16) & 255, (c >> 8) & 255, c & 255);
            points.put(new JSONObject().put("color", new JSONObject().put("xy", new JSONObject().put("x", xy[0]).put("y", xy[1]))));
        }
        JSONObject body = new JSONObject()
                .put("on", new JSONObject().put("on", true))
                .put("effects", new JSONObject().put("effect", "no_effect"))
                .put("gradient", new JSONObject().put("points", points))
                .put("dynamics", new JSONObject().put("duration", fadeMs));
        if (brightness >= 0) body.put("dimming", new JSONObject().put("brightness", brightness));
        https("PUT", "/" + v2Id, body.toString());
    }

    /**
     * What v1 cannot save: the strip's gradient points and running effect, as a body that puts them
     * back. Null when the strip shows a single color and no effect (v1 restores that fully).
     */
    JSONObject look(String v2Id) throws Exception {
        JSONObject l = new JSONObject(https("GET", "/" + v2Id, null)).getJSONArray("data").getJSONObject(0);
        JSONObject body = new JSONObject();
        JSONArray points = l.optJSONObject("gradient") == null ? null : l.getJSONObject("gradient").optJSONArray("points");
        if (points != null && points.length() > 0) body.put("gradient", new JSONObject().put("points", points));
        String effect = l.optJSONObject("effects") == null ? "no_effect" : l.getJSONObject("effects").optString("status", "no_effect");
        if (!"no_effect".equals(effect)) body.put("effects", new JSONObject().put("effect", effect));
        return body.length() == 0 ? null : body;
    }

    void put(String v2Id, JSONObject body) throws Exception {
        https("PUT", "/" + v2Id, body.toString());
    }

    /** v2 lights are keyed by UUID; the v1 id the rest of the app uses is in id_v1. */
    private JSONObject find(String v1Id) throws Exception {
        JSONArray lights = new JSONObject(https("GET", "", null)).getJSONArray("data");
        for (int i = 0; i < lights.length(); i++) {
            JSONObject l = lights.getJSONObject(i);
            if (l.optString("id_v1").equals("/lights/" + v1Id)) return l;
        }
        return null;
    }

    /** The open live event stream; read it line by line, disconnect() to end it. */
    java.net.HttpURLConnection events() throws IOException {
        HttpsURLConnection c = connect(host + "/eventstream/clip/v2");
        c.setReadTimeout(0); // events come when something changes, which may be hours apart
        c.setRequestProperty("Accept", "text/event-stream");
        if (c.getResponseCode() != 200) throw new IOException("event stream " + c.getResponseCode());
        return c;
    }

    private HttpsURLConnection connect(String url) throws IOException {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setSSLSocketFactory(tls);
        c.setHostnameVerifier((h, session) -> true); // the certificate names the bridge, not its IP; the pin covers identity
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        c.setRequestProperty("hue-application-key", key);
        return c;
    }

    private String https(String method, String path, String body) throws IOException {
        // No disconnect(): while the same HueV2 is kept (film mode keeps one per video), the next call reuses the connection.
        HttpsURLConnection c = connect(base + path);
        c.setRequestMethod(method);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        String reply = Hue.read(code < 400 ? c.getInputStream() : c.getErrorStream());
        if (code >= 400) throw new IOException("Hue v2 " + code + ": " + reply);
        return reply;
    }

    /** Trusts the first certificate the bridge shows and only that one afterwards. */
    private final class PinnedTrust implements X509TrustManager {
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            String seen;
            try {
                StringBuilder hex = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded())) hex.append(String.format("%02x", b));
                seen = hex.toString();
            } catch (Exception e) {
                throw new CertificateException(e);
            }
            String pinned = prefs.getString("pin", null);
            if (pinned == null) prefs.edit().putString("pin", seen).apply();
            else if (!pinned.equals(seen)) throw new CertificateException("Hue Bridge certificate changed");
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("not a server");
        }

        @Override public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
