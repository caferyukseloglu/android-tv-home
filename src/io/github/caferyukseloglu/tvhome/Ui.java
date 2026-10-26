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

    private Ui() {}

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

    /** Focused cards grow, lift and get a white ring, like tvOS. */
    static void focusable(View v, GradientDrawable bg, View.OnFocusChangeListener also) {
        focusable(v, bg, also, 1.08f);
    }

    /** zoom 1 for wide controls (a full-width bar) that would grow past their panel. */
    static void focusable(View v, GradientDrawable bg, View.OnFocusChangeListener also, float zoom) {
        Context c = v.getContext();
        // The ring is a foreground so it draws over app banners; on the background a banner would hide it.
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(bg.getShape());
        if (bg.getShape() == GradientDrawable.RECTANGLE) ring.setCornerRadius(dp(c, 14));
        v.setFocusable(true);
        v.setBackground(bg);
        v.setForeground(ring);
        v.setClipToOutline(true);
        v.setOnFocusChangeListener((view, has) -> {
            float scale = has ? zoom : 1f; // 1.08: a lift, not a jump
            view.animate().scaleX(scale).scaleY(scale).translationZ(has ? dp(c, 16) : 0).setDuration(180).start();
            ring.setStroke(has ? dp(c, 2) : 0, 0xCCFFFFFF);
            if (also != null) also.onFocusChange(view, has);
        });
    }
}
