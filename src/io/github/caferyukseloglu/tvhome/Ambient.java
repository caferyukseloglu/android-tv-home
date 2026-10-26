package io.github.caferyukseloglu.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Random;

/**
 * The screensaver: a large thin clock, the date and the weather over a slow, drifting glow.
 * The text moves to a new spot every minute so nothing stays lit in one place (no burn-in).
 */
public final class Ambient extends DreamService {
    private static final long MOVE_MS = 60_000;
    private static final int BLOCK_W = 460; // dp: room for "23:59" at 120 sp and a long weekday and month
    private static final int BLOCK_H = 300;
    // Deep, quiet colors the glow drifts through: night blue, violet, teal, ember.
    private static final int[] GLOW = {0xFF1B3A6B, 0xFF3D2466, 0xFF0E4B4F, 0xFF5A2E1C, 0xFF1B3A6B};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private LinearLayout block;
    private FrameLayout root;
    private ValueAnimator drift;
    private final Runnable move = this::moveBlock;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // any key wakes the TV
        setFullscreen(true);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(glow(), new FrameLayout.LayoutParams(dp(900), dp(900)));

        block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 120);
        time.setTextColor(0xFFF5F5F7);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        date.setTextColor(0xB3F5F5F7);
        TextView weather = Ui.text(this, 26, 0xFFF5F5F7, false);
        TextView range = Ui.text(this, 17, 0x99F5F5F7, false);
        block.addView(time);
        block.addView(date);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = dp(28);
        block.addView(weather, wp);
        block.addView(range);
        // A fixed width: the weather arrives later and must not push the text past the screen edge.
        root.addView(block, new FrameLayout.LayoutParams(dp(BLOCK_W), ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        new WeatherClient(this).load(w -> {
            if (w == null) return;
            weather.setText(WeatherClient.describe(this, w));
            range.setText(getString(R.string.weather_range, w.max, w.min));
        });
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        block.setAlpha(0f);
        root.post(this::moveBlock);
        drift.start();
    }

    @Override
    public void onDreamingStopped() {
        main.removeCallbacks(move);
        if (drift != null) drift.cancel();
        super.onDreamingStopped();
    }

    /** A soft colored light that wanders slowly across the screen and changes color as it goes. */
    private View glow() {
        GradientDrawable light = new GradientDrawable();
        light.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        light.setGradientRadius(dp(450));
        light.setColors(new int[] {GLOW[0], 0x00000000});
        View v = new View(this);
        v.setBackground(light);
        ArgbEvaluator mix = new ArgbEvaluator();
        drift = ValueAnimator.ofFloat(0f, 1f);
        drift.setDuration(120_000); // one full loop in two minutes: movement you notice, never watch
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new LinearInterpolator());
        drift.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            double angle = t * Math.PI * 2;
            v.setTranslationX((float) (root.getWidth() / 2.0 - dp(450) + Math.cos(angle) * root.getWidth() * 0.3));
            v.setTranslationY((float) (root.getHeight() / 2.0 - dp(450) + Math.sin(angle * 2) * root.getHeight() * 0.25));
            float seg = t * (GLOW.length - 1);
            int i = Math.min((int) seg, GLOW.length - 2);
            int color = (int) mix.evaluate(seg - i, GLOW[i], GLOW[i + 1]);
            light.setColors(new int[] {color, 0x00000000});
        });
        return v;
    }

    /** Fade out, move to a random spot inside the safe area, fade back in. */
    private void moveBlock() {
        int maxX = Math.max(0, root.getWidth() - dp(BLOCK_W) - dp(120));
        int maxY = Math.max(0, root.getHeight() - dp(BLOCK_H) - dp(80));
        block.animate().alpha(0f).setDuration(block.getAlpha() == 0f ? 0 : 1200).withEndAction(() -> {
            block.setTranslationX(dp(60) + random.nextInt(maxX + 1));
            block.setTranslationY(dp(40) + random.nextInt(maxY + 1));
            block.animate().alpha(1f).setDuration(1600).start();
        }).start();
        main.postDelayed(move, MOVE_MS);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
