package io.github.caferyukseloglu.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Four bars that dance while music plays and rest low when it is paused, like a live "now playing" mark. */
final class Equalizer extends View {
    private static final float[] SPEED = {1.0f, 1.6f, 1.25f, 1.9f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
    private float t;
    private boolean playing;

    Equalizer(Context c) {
        super(c);
        paint.setColor(Ui.TEXT);
        clock.setDuration(1000);
        clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.addUpdateListener(a -> {
            t += 0.016f;
            invalidate();
        });
    }

    void setPlaying(boolean on) {
        playing = on;
        if (on && !Ui.reduced) clock.start();
        else clock.cancel();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        clock.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth() / 7f, h = getHeight(), r = w / 2;
        for (int i = 0; i < 4; i++) {
            float level = playing ? 0.3f + 0.7f * Math.abs((float) Math.sin(t * 6 * SPEED[i] + i)) : 0.22f;
            float x = i * w * 2;
            c.drawRoundRect(x, h * (1 - level), x + w, h, r, r, paint);
        }
    }
}
