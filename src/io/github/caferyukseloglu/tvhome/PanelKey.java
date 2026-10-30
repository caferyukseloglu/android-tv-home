package io.github.caferyukseloglu.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Opens the control panel from anywhere with one remote key. An accessibility service is the only
 * way a normal app sees keys while another app is in front; this one asks for key events and
 * nothing else (no window content, no events). Turned on by `./home.sh panel-key`.
 * It also turns the screen off for the panel, now or when the sleep timer runs out.
 */
public final class PanelKey extends AccessibilityService {
    static final int[] SLEEP_STEPS = {0, 30, 60, 90, 120}; // minutes; Off, then half-hour steps
    private static PanelKey running; // null while the service is off
    private static int sleepMinutes;
    private static long sleepAt; // uptime the screen goes off, 0 = no timer
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sleep = () -> {
        sleepMinutes = 0;
        sleepAt = 0;
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    };
    // TCL remotes: the "123" number-pad key, which most people never use. Other TVs: the Bookmark key.
    private static final int KEY = firstKnown("KEYCODE_TCL_NUMBER", "TCL_NUMBER", "KEYCODE_BOOKMARK");

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KEY) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            startActivity(new Intent(this, LightsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true; // the TV's own number pad stays shut
    }

    private static int firstKnown(String... names) {
        for (String name : names) {
            int code = KeyEvent.keyCodeFromString(name);
            if (code != KeyEvent.KEYCODE_UNKNOWN) return code;
        }
        return KeyEvent.KEYCODE_BOOKMARK;
    }

    static boolean on() {
        return running != null;
    }

    /** Screen off (the TV's standby), as the power key does. False when the service is off. */
    static boolean screenOff() {
        return running != null && running.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Off -> 30 -> 60 -> 90 -> 120 -> Off; returns the new setting in minutes. */
    static int nextSleep() {
        if (running == null) return 0;
        int i = 0;
        while (i < SLEEP_STEPS.length && SLEEP_STEPS[i] != sleepMinutes) i++;
        sleepMinutes = SLEEP_STEPS[(i + 1) % SLEEP_STEPS.length];
        running.main.removeCallbacks(running.sleep);
        sleepAt = sleepMinutes == 0 ? 0 : android.os.SystemClock.uptimeMillis() + sleepMinutes * 60_000L;
        if (sleepAt > 0) running.main.postAtTime(running.sleep, sleepAt);
        android.util.Log.i("PanelKey", "sleep timer " + sleepMinutes + " min (was index " + i + ")");
        return sleepMinutes;
    }

    /** Minutes left on the sleep timer, 0 when none is set. */
    static int sleepLeft() {
        return sleepAt == 0 ? 0 : (int) Math.ceil((sleepAt - android.os.SystemClock.uptimeMillis()) / 60_000.0);
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        main.removeCallbacks(sleep);
        sleepAt = 0;
        sleepMinutes = 0;
        super.onDestroy();
    }

    @Override
    protected void onServiceConnected() {
        running = this;
        android.util.Log.i("PanelKey", "lights key is keycode " + KEY + " (" + KeyEvent.keyCodeToString(KEY) + ")");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
