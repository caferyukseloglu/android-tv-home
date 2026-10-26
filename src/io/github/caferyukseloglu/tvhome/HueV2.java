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
 * Hue's v2 API, which can set each color point of a gradient strip. It is HTTPS only, with a
 * certificate the bridge signs itself, so the certificate is pinned on first use. Blocking calls:
 * run them off the main thread.
 */
final class HueV2 {
    private final SharedPreferences prefs;
    private final String base;
    private final String key;
    private final SSLSocketFactory tls;
    private String lightId;
    private JSONObject original; // the strip's state before we took over, put back on stop

    HueV2(Context context) throws Exception {
        prefs = context.getSharedPreferences("hue", Context.MODE_PRIVATE);
        base = "https://" + prefs.getString("ip", "") + "/clip/v2/resource/light";
        key = prefs.getString("user", "");
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[] {new PinnedTrust()}, null);
        tls = ssl.getSocketFactory();
    }

    /** Finds the strip by its v1 id and remembers how it looked. False when it is not a gradient light. */
    boolean takeOver(String v1Id) throws Exception {
        JSONArray lights = new JSONObject(https("GET", "", null)).getJSONArray("data");
        for (int i = 0; i < lights.length(); i++) {
            JSONObject l = lights.getJSONObject(i);
            if (!l.has("gradient") || !l.optString("id_v1").equals("/lights/" + v1Id)) continue;
            lightId = l.getString("id");
            original = new JSONObject()
                    .put("on", l.getJSONObject("on"))
                    .put("dimming", l.getJSONObject("dimming"));
            JSONArray points = l.getJSONObject("gradient").getJSONArray("points");
            if (points.length() > 0) original.put("gradient", new JSONObject().put("points", points));
            else if (l.has("color")) original.put("color", new JSONObject().put("xy", l.getJSONObject("color").getJSONObject("xy")));
            return true;
        }
        return false;
    }

    /** colors: 0xRRGGBB in strip order. brightness: 0-100. */
    void show(int[] colors, double brightness, int fadeMs) throws Exception {
        JSONArray points = new JSONArray();
        for (int c : colors) {
            double[] xy = Colors.rgbToXy((c >> 16) & 255, (c >> 8) & 255, c & 255);
            points.put(new JSONObject().put("color", new JSONObject().put("xy", new JSONObject().put("x", xy[0]).put("y", xy[1]))));
        }
        https("PUT", "/" + lightId, new JSONObject()
                .put("on", new JSONObject().put("on", true))
                .put("gradient", new JSONObject().put("points", points))
                .put("dimming", new JSONObject().put("brightness", brightness))
                .put("dynamics", new JSONObject().put("duration", fadeMs))
                .toString());
    }

    void restore() throws Exception {
        if (lightId != null && original != null) https("PUT", "/" + lightId, original.toString());
    }

    private String https(String method, String path, String body) throws IOException {
        // No disconnect(): the kept-alive connection is reused by the next frame.
        HttpsURLConnection c = (HttpsURLConnection) new URL(base + path).openConnection();
        c.setSSLSocketFactory(tls);
        c.setHostnameVerifier((host, session) -> true); // the certificate names the bridge, not its IP; the pin covers identity
        c.setRequestMethod(method);
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        c.setRequestProperty("hue-application-key", key);
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
