package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * A look for Home: the room's colors, whether apps light it in their color, and the festive extras.
 * "auto" follows {@link Season}, so Halloween week turns itself on and off.
 */
final class Theme {
    static final String[] IDS = {"auto", "default", "night", "dusk", "dawn", "halloween", "midnight", "aurora"};

    final String id;
    final int[] room;      // radial backdrop, center to edge
    final boolean glow;    // the focused app lights the room in its color
    final int fallback;    // glow color for apps without one, 0 = none
    final boolean festive; // bats, the pumpkin and the Halloween greeting

    private Theme(String id, int[] room, boolean glow, int fallback, boolean festive) {
        this.id = id;
        this.room = room;
        this.glow = glow;
        this.fallback = fallback;
        this.festive = festive;
    }

    static Theme of(String id) {
        switch (id) {
            case "halloween": return new Theme(id, new int[] {0xFF2B1236, 0xFF150A1D, 0xFF07040A}, true, 0xFF7A1C, true);
            case "midnight": return new Theme(id, new int[] {0xFF000000, 0xFF000000, 0xFF000000}, false, 0, false);
            case "aurora": return new Theme(id, new int[] {0xFF0E2E36, 0xFF0F1530, 0xFF06070E}, true, 0x3FD6C0, false);
            // Scenes: the room colors are for Settings; Home draws the landscape (Scene).
            case "night": return new Theme(id, new int[] {0xFF223154, 0xFF0D1733, 0xFF04060F}, true, 0, false);
            case "dusk": return new Theme(id, new int[] {0xFF5E2A57, 0xFF2C1534, 0xFF150A1B}, true, 0, false);
            case "dawn": return new Theme(id, new int[] {0xFF4A7A89, 0xFF234252, 0xFF0D2636}, true, 0, false);
            default: return new Theme("default", new int[] {0xFF1C1E24, 0xFF0E0F12, 0xFF08090B}, true, 0, false);
        }
    }

    /** The theme in effect today: the user's pick, or the season's when the pick is Auto. */
    static Theme current(Context c) {
        Calendar now = Calendar.getInstance();
        return of(Season.resolve(prefs(c).getString("theme", "auto"), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)));
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static int label(String id) {
        switch (id) {
            case "default": return R.string.theme_default;
            case "halloween": return R.string.theme_halloween;
            case "midnight": return R.string.theme_midnight;
            case "aurora": return R.string.theme_aurora;
            case "night": return R.string.theme_night;
            case "dusk": return R.string.theme_dusk;
            case "dawn": return R.string.theme_dawn;
            default: return R.string.theme_auto;
        }
    }
}
