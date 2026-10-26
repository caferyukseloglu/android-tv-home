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
    private final String base;
    private final String key;
    private final SSLSocketFactory tls;

    HueV2(Context context) throws Exception {
        prefs = context.getSharedPreferences("hue", Context.MODE_PRIVATE);
        base = "https://" + prefs.getString("ip", "") + "/clip/v2/resource/light";
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

    /** v2 lights are keyed by UUID; the v1 id the rest of the app uses is in id_v1. */
    private JSONObject find(String v1Id) throws Exception {
        JSONArray lights = new JSONObject(https("GET", "", null)).getJSONArray("data");
        for (int i = 0; i < lights.length(); i++) {
            JSONObject l = lights.getJSONObject(i);
            if (l.optString("id_v1").equals("/lights/" + v1Id)) return l;
        }
        return null;
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
