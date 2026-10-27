package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Philips Hue Bridge over its local HTTP API (v1). Results arrive on the main thread. */
final class Hue {
    static final class Light {
        final String id;
        final String name;
        final boolean on;
        final boolean reachable; // false when the lamp is cut off at the wall switch
        final int bri; // 1-254
        final int rgb; // the lamp's current color, for its card
        final boolean color; // false for white-only lamps
        final boolean gradient; // Play gradient strips can follow the screen

        Light(String id, String name, boolean on, boolean reachable, int bri, int rgb, boolean color, boolean gradient) {
            this.id = id;
            this.name = name;
            this.on = on;
            this.reachable = reachable;
            this.bri = bri;
            this.rgb = rgb;
            this.color = color;
            this.gradient = gradient;
        }
    }

    interface Result<T> {
        void done(T value, Exception error);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final long DISCOVERY_MS = 10_000;
    private static final int PAIR_TRIES = 30; // one a second: time to walk to the bridge

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    Hue(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("hue", Context.MODE_PRIVATE);
    }

    boolean paired() {
        return prefs.getString("user", null) != null;
    }

    /** Finds the bridge with mDNS (_hue._tcp) and remembers its address. */
    void discover(Result<String> cb) {
        NsdManager nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        AtomicBoolean finished = new AtomicBoolean();
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override public void onServiceFound(NsdServiceInfo service) {
                NsdManager.DiscoveryListener discovery = this;
                nsd.resolveService(service, new NsdManager.ResolveListener() {
                    @Override public void onServiceResolved(NsdServiceInfo info) {
                        // IPv4 only: an IPv6 address would need brackets in every URL.
                        if (!(info.getHost() instanceof Inet4Address) || !finished.compareAndSet(false, true)) return;
                        stop(nsd, discovery);
                        String ip = info.getHost().getHostAddress();
                        prefs.edit().putString("ip", ip).apply();
                        main.post(() -> cb.done(ip, null));
                    }

                    @Override public void onResolveFailed(NsdServiceInfo info, int code) {}
                });
            }

            @Override public void onStartDiscoveryFailed(String type, int code) {
                if (finished.compareAndSet(false, true)) {
                    main.post(() -> cb.done(null, new IOException("mDNS failed: " + code)));
                }
            }

            @Override public void onDiscoveryStarted(String type) {}
            @Override public void onDiscoveryStopped(String type) {}
            @Override public void onServiceLost(NsdServiceInfo service) {}
            @Override public void onStopDiscoveryFailed(String type, int code) {}
        };
        nsd.discoverServices("_hue._tcp", NsdManager.PROTOCOL_DNS_SD, listener);
        main.postDelayed(() -> {
            if (finished.compareAndSet(false, true)) {
                stop(nsd, listener);
                cb.done(null, new IOException("No Hue Bridge found"));
            }
        }, DISCOVERY_MS);
    }

    private static void stop(NsdManager nsd, NsdManager.DiscoveryListener listener) {
        try {
            nsd.stopServiceDiscovery(listener);
        } catch (IllegalArgumentException alreadyStopped) {
            // discovery already ended on its own
        }
    }

    /** Waits for the bridge's link button, then stores the key it hands out. */
    void pair(Result<Void> cb) {
        async(() -> {
            String device = ("tv#" + Build.MODEL).replaceAll("[^A-Za-z0-9#_ ]", "");
            String body = new JSONObject().put("devicetype", device.substring(0, Math.min(device.length(), 40))).toString();
            for (int i = 0; i < PAIR_TRIES; i++) {
                JSONObject first = new JSONArray(http("POST", "/api", body)).getJSONObject(0);
                JSONObject success = first.optJSONObject("success");
                if (success != null) {
                    prefs.edit().putString("user", success.getString("username")).remove("pin").apply(); // a new bridge has a new certificate
                    return null;
                }
                Thread.sleep(1000);
            }
            throw new IOException("Link button not pressed");
        }, cb);
    }

    void lights(Result<List<Light>> cb) {
        async(() -> {
            String body = http("GET", "/api/" + user() + "/lights", null);
            if (body.startsWith("[")) { // an error list, e.g. the key was deleted in the Hue app
                if (body.contains("\"type\":1,")) prefs.edit().remove("user").apply();
                throw new IOException(body);
            }
            return parseLights(new JSONObject(body));
        }, cb);
    }

    /** state: Hue v1 light state, e.g. {"on":true,"bri":200,"xy":[0.6,0.3]}. */
    void setState(String lightId, JSONObject state, Result<Void> cb) {
        async(() -> {
            putState(lightId, state);
            return null;
        }, cb);
    }

    /** Blocking; call inside async(). */
    void putState(String lightId, JSONObject state) throws IOException {
        http("PUT", "/api/" + user() + "/lights/" + lightId + "/state", state.toString());
    }

    /** Blocking; call inside async(). The lamp's v1 state object (on, bri, colormode, xy, ct, ...). */
    JSONObject lightState(String lightId) throws IOException, org.json.JSONException {
        return new JSONObject(http("GET", "/api/" + user() + "/lights/" + lightId, null)).getJSONObject("state");
    }

    static final class Scene {
        final String id;
        final String name;

        Scene(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** Scenes saved in the Hue app that include this lamp, in the bridge's order. */
    void scenes(String lightId, Result<List<Scene>> cb) {
        async(() -> {
            JSONObject all = new JSONObject(http("GET", "/api/" + user() + "/scenes", null));
            List<Scene> out = new ArrayList<>();
            for (Iterator<String> it = all.keys(); it.hasNext(); ) {
                String id = it.next();
                JSONObject sc = all.getJSONObject(id);
                JSONArray lights = sc.optJSONArray("lights");
                for (int i = 0; lights != null && i < lights.length(); i++) {
                    if (lights.getString(i).equals(lightId)) {
                        out.add(new Scene(id, sc.getString("name")));
                        break;
                    }
                }
            }
            return out;
        }, cb);
    }

    /** Group 0 is every light: the bridge applies the scene to the lamps it names. */
    void recall(String sceneId, Result<Void> cb) {
        async(() -> {
            http("PUT", "/api/" + user() + "/groups/0/action", new JSONObject().put("scene", sceneId).toString());
            return null;
        }, cb);
    }

    /** The TV strip first (it is the light people reach for while watching), then in the bridge's order. */
    static List<Light> parseLights(JSONObject lights) throws org.json.JSONException {
        List<Light> out = new ArrayList<>();
        for (Iterator<String> it = lights.keys(); it.hasNext(); ) {
            String id = it.next();
            JSONObject l = lights.getJSONObject(id);
            JSONObject s = l.getJSONObject("state");
            JSONArray xy = s.optJSONArray("xy");
            int rgb = "ct".equals(s.optString("colormode")) || xy == null
                    ? Colors.ctToRgb(s.optInt("ct", 366))
                    : Colors.xyToRgb(xy.getDouble(0), xy.getDouble(1));
            boolean gradient = l.optString("productname").toLowerCase().contains("gradient");
            Light light = new Light(id, l.getString("name"), s.optBoolean("on"), s.optBoolean("reachable", true),
                    s.optInt("bri", 254), rgb, l.optString("type").toLowerCase().endsWith("color light"), gradient);
            if (gradient) out.add(0, light);
            else out.add(light);
        }
        return out;
    }

    private String user() {
        return prefs.getString("user", "");
    }

    private String http(String method, String path, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + prefs.getString("ip", "") + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(3000);
            c.setReadTimeout(5000);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    static String read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int n; in != null && (n = in.read(chunk)) > 0; ) buf.write(chunk, 0, n);
            return buf.toString("UTF-8");
        }
    }

    /** Runs blocking bridge work on the shared network thread; the result comes back on the main thread. */
    <T> void async(Callable<T> work, Result<T> cb) {
        IO.execute(() -> {
            try {
                T value = work.call();
                main.post(() -> cb.done(value, null));
            } catch (Exception e) {
                main.post(() -> cb.done(null, e));
            }
        });
    }
}
