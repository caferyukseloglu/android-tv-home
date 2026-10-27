package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/** Colors, sizes and the one focus effect every card shares. */
final class Ui {
    static final int TEXT = 0xFFF2F2F4;
    static final int TEXT_DIM = 0xFF8E9099;
    static final int CARD = 0xFF1E2026;

    private static final float TILT = 7f; // degrees a card leans toward the way focus came in
    private static int lastDx; // direction of the last arrow key: -1, 0 or 1
    private static int lastDy;

    private Ui() {}

    /** Called for every key on screens with cards, so a newly focused card knows where focus came from. */
    static void noteKey(android.view.KeyEvent e) {
        if (e.getAction() != android.view.KeyEvent.ACTION_DOWN) return;
        switch (e.getKeyCode()) {
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT: lastDx = -1; lastDy = 0; break;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: lastDx = 1; lastDy = 0; break;
            case android.view.KeyEvent.KEYCODE_DPAD_UP: lastDx = 0; lastDy = -1; break;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: lastDx = 0; lastDy = 1; break;
            default: break;
        }
    }

    static final android.view.animation.Interpolator SPRING = new android.view.animation.OvershootInterpolator(1.6f);
    static final android.view.animation.Interpolator SETTLE = new android.view.animation.DecelerateInterpolator(1.8f);

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setSingleLine(true);
        return t;
    }

    static GradientDrawable rounded(Context c, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, 14));
        return d;
    }

    /**
     * tvOS-like focus: the card springs up a little past its size and settles, leans toward the
     * direction focus came from and straightens, lifts its shadow and shows a thin white ring.
     */
    static void focusable(View v, GradientDrawable bg, View.OnFocusChangeListener also) {
        focusable(v, bg, also, 1.08f);
    }

    /** zoom 1 for wide controls (a full-width bar) that would grow past their panel. */
    static void focusable(View v, GradientDrawable bg, View.OnFocusChangeListener also, float zoom) {
        Context c = v.getContext();
        // Over app banners the ring must be a foreground, or the banner would hide it. A plain text
        // button has nothing on top, and its padding would push a foreground ring inside: it uses the background.
        GradientDrawable ring = v instanceof TextView ? bg : new GradientDrawable();
        if (ring != bg) {
            ring.setShape(bg.getShape());
            if (bg.getShape() == GradientDrawable.RECTANGLE) ring.setCornerRadius(dp(c, 14));
            v.setForeground(ring);
        }
        v.setFocusable(true);
        v.setBackground(bg);
        v.setClipToOutline(true);
        // The Menu key does what holding OK does (Android TV's convention), for remotes that have one.
        v.setOnKeyListener((view, code, e) -> code == android.view.KeyEvent.KEYCODE_MENU
                && e.getAction() == android.view.KeyEvent.ACTION_UP && view.isLongClickable() && view.performLongClick());
        v.setCameraDistance(dp(c, 1200)); // a deep camera keeps the lean subtle instead of a warp
        v.setOnFocusChangeListener((view, has) -> {
            if (has && zoom > 1f) {
                view.setRotationY(lastDx * TILT);
                view.setRotationX(-lastDy * TILT);
            }
            view.animate()
                    .scaleX(has ? zoom : 1f).scaleY(has ? zoom : 1f)
                    .rotationX(0).rotationY(0)
                    .translationZ(has ? dp(c, 18) : 0)
                    .setInterpolator(has ? SPRING : SETTLE)
                    .setDuration(has ? 420 : 220)
                    .start();
            ring.setStroke(has ? dp(c, 2) : 0, 0xCCFFFFFF);
            if (also != null) also.onFocusChange(view, has);
        });
    }
}
