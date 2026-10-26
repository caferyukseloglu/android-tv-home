package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The TV's light engine. While a video app is open, every light in the TV's Hue entertainment area
 * (or just the chosen strip when there is none) follows what is playing: 6500 K white at once, then
 * the cover's colors, made more vivid, drifting slowly along the strip and across the lamps with soft
 * fades. Back on Home every light returns to how it was. One instance per process: get(context).
 *
 * Threads: bridge work runs in order on Hue's network thread; the drift is scheduled on the main
 * thread, one step after the last one finished, so a slow or missing bridge never piles work up.
 */
final class FilmMode {
    // Apps that declare themselves as video get it too (ApplicationInfo.CATEGORY_VIDEO); many big ones don't.
    private static final Set<String> VIDEO_APPS = new HashSet<>(Arrays.asList(
            "com.stremio.one", "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream", "com.disney.disneyplus", "com.dsmart.blu.android", "com.upst.hayu", "tv.twitch.android.app",
            "com.kick.mobile", "ar.tvplayer.tv", "com.plexapp.android", "org.xbmc.kodi", "com.google.android.videos"));
    // Full brightness by choice of the owner: dim it from the light panel when the room is dark.
    private static final JSONObject BIAS = LightPanel.json("on", true, "ct", 153, "bri", 254); // 6500 K
    private static final long FLOW_MS = 1500; // one soft step every 1.5 s, faded over the same 1.5 s
    private static final double VIVID = 1.5; // cover colors are often pastel; Hue looks best saturated
    private static final int MIN_BRI = 127; // 50%: a dark poster still lights the room
    private static final int MAX_BRI = 254;
    private static final int COVER_PX = 160; // decode covers near this size; only 40x20 pixels are used
    private static final String TAG = "FilmMode";

    /** package|title|has-cover already on the lights; MediaWatch skips it, end() clears it. */
    static volatile String shown;
    /** Home is in front: media playing now is background music, not a film to light. */
    static volatile boolean homeVisible;
    private static FilmMode instance;

    private final Context context;
    private final Hue hue;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int[] palette; // the colors drifting now, null when nothing plays
    private volatile int bri = MAX_BRI;
    // Main thread only: the drift chain.
    private int generation; // a new chain or end() bumps it; an older chain stops at its next step
    private boolean drifting;
    private boolean isPlaying = true;
    private int step;
    // Network thread only: one bridge connection and the strip's v2 id per video session.
    private HueV2 v2;
    private String stripV2;

    static synchronized FilmMode get(Context context) {
        if (instance == null) instance = new FilmMode(context.getApplicationContext());
        return instance;
    }

    private FilmMode(Context context) {
        this.context = context;
        this.hue = new Hue(context);
        this.prefs = context.getSharedPreferences("film", Context.MODE_PRIVATE);
    }

    /** The lamp film mode is set on (the TV strip), or null when it is off. */
    String light() {
        return prefs.getString("light", null);
    }

    void setLight(String lightId) {
        prefs.edit().putString("light", lightId).apply();
    }

    /** Before a video app opens: remember the lights, then 6500 K white until the cover arrives. */
    void start(String pkg) {
        String id = light();
        if (id == null || !isVideo(pkg)) return;
        hue.async(() -> {
            for (String l : saveOnce(id)) hue.putState(l, BIAS);
            return null;
        }, FilmMode::log);
    }

    /** A video app is playing with this cover: its colors start drifting over the lights. */
    void content(String pkg, Bitmap art, String uri) {
        String id = light();
        if (id == null || !isVideo(pkg) || homeVisible) return;
        hue.async(() -> {
            saveOnce(id);
            Bitmap cover = art != null ? art : load(uri);
            if (cover == null) {
                Log.i(TAG, pkg + ": no cover, staying white");
                return null;
            }
            Bitmap small = Bitmap.createScaledBitmap(cover, 40, 20, true);
            int[] px = new int[40 * 20];
            small.getPixels(px, 0, 40, 0, 0, 40, 20);
            int[] colors = Colors.palette(px, 40, 20, 5);
            for (int i = 0; i < colors.length; i++) colors[i] = Colors.saturate(colors[i], VIVID);
            bri = (int) Math.round(MIN_BRI + (MAX_BRI - MIN_BRI) * Math.min(1, Colors.luminance(px) * 2));
            Log.i(TAG, pkg + ": cover " + (art != null ? "bitmap" : uri) + " -> " + hex(colors) + " bri " + bri);
            palette = colors;
            main.post(() -> {
                if (!drifting) startDrift();
            });
            return null;
        }, FilmMode::log);
    }

    /**
     * Pause and resume follow the player: a paused video or a TV gone to standby stops the drift,
     * so the lights are not changed all night behind a dark screen.
     */
    void playing(boolean nowPlaying) {
        main.post(() -> {
            isPlaying = nowPlaying;
            if (nowPlaying && !drifting && palette != null) startDrift();
        });
    }

    private void startDrift() {
        drifting = true;
        int chain = ++generation;
        main.postDelayed(() -> flowStep(chain), FLOW_MS);
    }

    /** One step: the strip's points move along by one, each lamp takes the next color. The next step is planned when this one is done. */
    private void flowStep(int chain) {
        int[] colors = palette;
        if (chain != generation || colors == null || !isPlaying) {
            if (chain == generation) drifting = false;
            return;
        }
        int[] now = Colors.rotate(colors, step++);
        int level = bri;
        hue.async(() -> {
            List<String> area = area();
            for (int i = 0; i < area.size(); i++) {
                String l = area.get(i);
                if (i == 0 && stripV2 != null) { // the strip leads the area (saveOnce puts it first)
                    v2.gradientById(stripV2, now, level * 100.0 / 254, (int) FLOW_MS);
                } else {
                    int c = now[i % now.length];
                    hue.putState(l, LightPanel.json("on", true, "bri", level, "transitiontime", (int) (FLOW_MS / 100),
                            "xy", Colors.rgbToXy((c >> 16) & 255, (c >> 8) & 255, c & 255)));
                }
            }
            return null;
        }, (ok, error) -> {
            log(null, error);
            if (chain == generation) main.postDelayed(() -> flowStep(chain), FLOW_MS);
        });
    }

    /** Back on Home: stop the drift and put every light back exactly once. */
    void end() {
        palette = null;
        generation++;
        drifting = false;
        hue.async(() -> {
            palette = null; // a cover that finished loading after Home must not drift during the next video
            String saved = prefs.getString("saved", null);
            if (saved != null) {
                JSONObject states = new JSONObject(saved);
                for (Iterator<String> it = states.keys(); it.hasNext(); ) {
                    String l = it.next();
                    hue.putState(l, restore(states.getJSONObject(l)));
                }
                String look = prefs.getString("stripLook", null);
                if (look != null && v2 != null && stripV2 != null) v2.put(stripV2, new JSONObject(look));
                prefs.edit().remove("saved").remove("area").remove("stripLook").commit();
            }
            v2 = null;
            stripV2 = null;
            shown = null; // the same video played again after Home gets its colors again
            return null;
        }, FilmMode::log);
    }

    /**
     * Remembers every light of the strip's entertainment area once per video session (a second video
     * keeps the first memory), plus the strip's gradient and effect, which v1 cannot see. Blocking.
     */
    private List<String> saveOnce(String id) throws Exception {
        if (v2 == null) {
            v2 = new HueV2(context);
            stripV2 = v2.v2Id(id);
        }
        if (!prefs.contains("saved")) {
            List<String> area = hue.entertainmentArea(id);
            JSONObject states = new JSONObject();
            for (String l : area) states.put(l, hue.lightState(l));
            JSONObject look = stripV2 == null ? null : v2.look(stripV2);
            prefs.edit().putString("saved", states.toString()).putString("area", new JSONArray(area).toString())
                    .putString("stripLook", look == null ? null : look.toString()).commit();
        }
        return area();
    }

    private List<String> area() throws Exception {
        JSONArray a = new JSONArray(prefs.getString("area", "[]"));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        return out;
    }

    /**
     * A cover given as an address: bounded in time (it runs on the bridge's thread) and decoded
     * small, so a slow server cannot stall the lights and a huge poster cannot run out of memory.
     */
    private Bitmap load(String uri) {
        if (uri == null) return null;
        try {
            byte[] data;
            if (uri.startsWith("http")) {
                HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                try {
                    data = bytes(c.getInputStream());
                } finally {
                    c.disconnect();
                }
            } else {
                data = bytes(context.getContentResolver().openInputStream(android.net.Uri.parse(uri)));
            }
            BitmapFactory.Options size = new BitmapFactory.Options();
            size.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, size);
            BitmapFactory.Options small = new BitmapFactory.Options();
            small.inSampleSize = Math.max(1, Math.min(size.outWidth, size.outHeight) / COVER_PX);
            return BitmapFactory.decodeByteArray(data, 0, data.length, small);
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "cover not loaded: " + uri, e);
            return null;
        }
    }

    private static byte[] bytes(InputStream stream) throws java.io.IOException {
        try (InputStream in = stream) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int n; in != null && (n = in.read(chunk)) > 0; ) {
                out.write(chunk, 0, n);
                if (out.size() > 8 * 1024 * 1024) throw new java.io.IOException("cover larger than 8 MB");
            }
            return out.toByteArray();
        }
    }

    private static String hex(int[] colors) {
        StringBuilder s = new StringBuilder();
        for (int c : colors) s.append(String.format("#%06X ", c));
        return s.toString().trim();
    }

    /** v1 state as read -> the fields that put it back: off stays off, otherwise brightness and color. */
    static JSONObject restore(JSONObject s) {
        boolean on = s.optBoolean("on");
        if (!on) return LightPanel.json("on", false);
        String mode = s.optString("colormode");
        if ("xy".equals(mode) && s.optJSONArray("xy") != null) {
            return LightPanel.json("on", true, "bri", s.optInt("bri", 254), "xy",
                    new double[] {s.optJSONArray("xy").optDouble(0), s.optJSONArray("xy").optDouble(1)});
        }
        if ("hs".equals(mode)) {
            return LightPanel.json("on", true, "bri", s.optInt("bri", 254), "hue", s.optInt("hue"), "sat", s.optInt("sat"));
        }
        return LightPanel.json("on", true, "bri", s.optInt("bri", 254), "ct", s.optInt("ct", 366));
    }

    boolean isVideo(String pkg) {
        if (VIDEO_APPS.contains(pkg)) return true;
        try {
            return context.getPackageManager().getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_VIDEO;
        } catch (PackageManager.NameNotFoundException gone) {
            return false;
        }
    }

    private static void log(Void ignored, Exception error) {
        if (error != null) Log.w(TAG, "Hue not updated", error); // the lights just stay as they are
    }
}
