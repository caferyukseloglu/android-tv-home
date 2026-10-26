package io.github.caferyukseloglu.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Opens the lights panel from anywhere with one remote key. An accessibility service is the only
 * way a normal app sees keys while another app is in front; this one asks for key events and
 * nothing else (no window content, no events). Turned on by `./home.sh panel-key`.
 */
public final class PanelKey extends AccessibilityService {
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
