package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Bias lighting: while a video app is open, the chosen lamp (the strip behind the TV) glows
 * 6500 K white, the white video is mastered for; back on Home it returns to how it was.
 * Both steps run on Hue's single network thread, so a quick return can never overtake the start.
 */
final class FilmMode {
    // Apps that declare themselves as video get it too (ApplicationInfo.CATEGORY_VIDEO); many big ones don't.
    private static final Set<String> VIDEO_APPS = new HashSet<>(Arrays.asList(
            "com.stremio.one", "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.wbd.stream", "com.disney.disneyplus", "com.dsmart.blu.android", "com.upst.hayu", "tv.twitch.android.app",
            "com.kick.mobile", "ar.tvplayer.tv", "com.plexapp.android", "org.xbmc.kodi", "com.google.android.videos"));
    // Full brightness by choice of the owner: dim it from the light panel when the room is dark.
    private static final JSONObject BIAS = LightPanel.json("on", true, "ct", 153, "bri", 254); // 6500 K

    private final Context context;
    private final Hue hue;
    private final SharedPreferences prefs;

    FilmMode(Context context) {
        this.context = context;
        this.hue = new Hue(context);
        this.prefs = context.getSharedPreferences("film", Context.MODE_PRIVATE);
    }

    /** The lamp film mode drives, or null when it is off. */
    String light() {
        return prefs.getString("light", null);
    }

    void setLight(String lightId) {
        prefs.edit().putString("light", lightId).apply();
    }

    /** Before a video app opens: remember the lamp, then dim it. A second video app keeps the first memory. */
    void start(String pkg) {
        String id = light();
        if (id == null || !isVideo(pkg)) return;
        hue.async(() -> {
            if (!prefs.contains("saved")) {
                prefs.edit().putString("saved", hue.lightState(id).toString()).putString("savedLight", id).commit();
            }
            hue.putState(id, BIAS);
            return null;
        }, FilmMode::log);
    }

    /** Back on Home: put the lamp back exactly once. */
    void end() {
        hue.async(() -> {
            String saved = prefs.getString("saved", null);
            if (saved == null) return null;
            hue.putState(prefs.getString("savedLight", ""), restore(new JSONObject(saved)));
            prefs.edit().remove("saved").remove("savedLight").commit();
            return null;
        }, FilmMode::log);
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

    private boolean isVideo(String pkg) {
        if (VIDEO_APPS.contains(pkg)) return true;
        try {
            return context.getPackageManager().getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_VIDEO;
        } catch (PackageManager.NameNotFoundException gone) {
            return false;
        }
    }

    private static void log(Void ignored, Exception error) {
        if (error != null) Log.w("FilmMode", "Hue not updated", error); // the lamp just stays as it is
    }
}
