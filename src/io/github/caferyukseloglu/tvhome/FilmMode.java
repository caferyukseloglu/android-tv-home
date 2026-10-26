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
 */
final class FilmMode {
    // Apps that declare themselves as video get it too (ApplicationInfo.CATEGORY_VIDEO); many big ones don't.
    private static final Set<String> VIDEO_APPS = new HashSet<>(Arrays.asList(
            "com.stremio.one", "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream", "com.disney.disneyplus", "com.dsmart.blu.android", "com.upst.hayu", "tv.twitch.android.app",
            "com.kick.mobile", "ar.tvplayer.tv", "com.plexapp.android", "org.xbmc.kodi", "com.google.android.videos"));
    // Full brightness by choice of the owner: dim it from the light panel when the room is dark.
    private static final JSONObject BIAS = LightPanel.json("on", true, "ct", 153, "bri", 254); // 6500 K
    private static final long FLOW_MS = 3000; // one soft step every 3 s, faded over the same 3 s
    private static final double VIVID = 1.5; // cover colors are often pastel; Hue looks best saturated
    private static final int MIN_BRI = 127; // 50%: a dark poster still lights the room
    private static final int MAX_BRI = 254;
    private static final String TAG = "FilmMode";

    /** package|title whose colors are on the lights; MediaWatch skips it, end() clears it. */
    static volatile String shown;
    private static FilmMode instance;

    private final Context context;
    private final Hue hue;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable flow = this::flowStep;
    private volatile int[] palette; // the colors drifting now, null when nothing plays
    private volatile int bri = MAX_BRI;
    private int step;

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
        if (id == null || !isVideo(pkg)) return;
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
                main.removeCallbacks(flow);
                flowStep();
            });
            return null;
        }, FilmMode::log);
    }

    /** One step of the drift: the strip's points move along by one, each lamp takes the next color. */
    private void flowStep() {
        int[] colors = palette;
        String id = prefs.getString("savedLight", null);
        if (colors == null || id == null) return;
        int[] now = Colors.rotate(colors, step++);
        hue.async(() -> {
            List<String> area = area();
            for (int i = 0; i < area.size(); i++) {
                String l = area.get(i);
                if (l.equals(id)) {
                    new HueV2(context).gradient(l, now, bri * 100.0 / 254, (int) FLOW_MS);
                } else {
                    int c = now[i % now.length];
                    hue.putState(l, LightPanel.json("on", true, "bri", bri, "transitiontime", (int) (FLOW_MS / 100),
                            "xy", Colors.rgbToXy((c >> 16) & 255, (c >> 8) & 255, c & 255)));
                }
            }
            return null;
        }, FilmMode::log);
        main.postDelayed(flow, FLOW_MS);
    }

    /**
     * Pause and resume follow the player: a paused video or a TV gone to standby stops the drift,
     * so the lights are not changed all night behind a dark screen.
     */
    void playing(boolean isPlaying) {
        main.post(() -> {
            main.removeCallbacks(flow);
            if (isPlaying && palette != null) main.postDelayed(flow, FLOW_MS);
        });
    }

    /** Back on Home: stop the drift and put every light back exactly once. */
    void end() {
        palette = null;
        main.removeCallbacks(flow);
        hue.async(() -> {
            String saved = prefs.getString("saved", null);
            if (saved == null) return null;
            JSONObject states = new JSONObject(saved);
            if (states.has("on")) { // saved by the single-strip version: one state, not a map of them
                states = new JSONObject().put(prefs.getString("savedLight", ""), states);
            }
            for (Iterator<String> it = states.keys(); it.hasNext(); ) {
                String l = it.next();
                hue.putState(l, restore(states.getJSONObject(l)));
            }
            prefs.edit().remove("saved").remove("savedLight").remove("area").commit();
            shown = null; // the same video played again after Home gets its colors again
            return null;
        }, FilmMode::log);
    }

    /**
     * Remembers every light of the strip's entertainment area once per video session (a second video
     * keeps the first memory) and returns them. Blocking: runs on Hue's network thread.
     */
    private List<String> saveOnce(String id) throws Exception {
        if (!prefs.contains("saved")) {
            List<String> area = hue.entertainmentArea(id);
            JSONObject states = new JSONObject();
            for (String l : area) states.put(l, hue.lightState(l));
            prefs.edit().putString("saved", states.toString()).putString("savedLight", id)
                    .putString("area", new JSONArray(area).toString()).commit();
        }
        return area();
    }

    private List<String> area() throws Exception {
        // A session saved by the single-strip version has no area: it drives just the strip.
        JSONArray a = new JSONArray(prefs.getString("area", new JSONArray().put(prefs.getString("savedLight", "")).toString()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        return out;
    }

    private Bitmap load(String uri) throws java.io.IOException {
        if (uri == null) return null;
        java.io.InputStream in = uri.startsWith("http")
                ? new java.net.URL(uri).openStream()
                : context.getContentResolver().openInputStream(android.net.Uri.parse(uri));
        try (java.io.InputStream stream = in) {
            return BitmapFactory.decodeStream(stream);
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
