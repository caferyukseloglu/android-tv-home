package io.github.caferyukseloglu.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A jack-o'-lantern for the Halloween greeting: three lobes, a stem, and a candle that flickers behind the face. */
final class Pumpkin extends Drawable {
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stem = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path eyes = new Path();
    private final int size;
    private final ValueAnimator flicker = ValueAnimator.ofFloat(0f, 1f);
    private float light = 1f;

    Pumpkin(int sizePx, boolean still) {
        size = sizePx;
        stem.setColor(0xFF3E6B2A);
        flicker.setDuration(1400);
        flicker.setRepeatCount(ValueAnimator.INFINITE);
        flicker.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue() * 6.283f;
            light = 0.78f + 0.12f * (float) Math.sin(t * 3) + 0.1f * (float) Math.sin(t * 7.3f); // an uneven candle
            invalidateSelf();
        });
        if (!still) flicker.start();
    }

    @Override
    public void draw(Canvas c) {
        float s = size, cx = s / 2, cy = s * 0.58f;
        body.setShader(new RadialGradient(cx, cy - s * 0.08f, s * 0.55f, 0xFFFFA43A, 0xFFC4500C, Shader.TileMode.CLAMP));
        c.drawOval(cx - s * 0.46f, cy - s * 0.34f, cx - s * 0.06f, cy + s * 0.36f, body);
        c.drawOval(cx + s * 0.06f, cy - s * 0.34f, cx + s * 0.46f, cy + s * 0.36f, body);
        c.drawOval(cx - s * 0.26f, cy - s * 0.38f, cx + s * 0.26f, cy + s * 0.38f, body);
        c.drawRoundRect(cx - s * 0.05f, s * 0.08f, cx + s * 0.06f, s * 0.26f, s * 0.03f, s * 0.03f, stem);
        int glow = (int) (255 * light);
        face.setColor(0xFF000000 | (glow << 16) | ((int) (glow * 0.82f) << 8) | (int) (glow * 0.25f));
        eyes.rewind();
        triangle(cx - s * 0.17f, cy - s * 0.1f, s * 0.1f);
        triangle(cx + s * 0.17f, cy - s * 0.1f, s * 0.1f);
        eyes.moveTo(cx - s * 0.22f, cy + s * 0.08f); // a grin with two teeth
        eyes.quadTo(cx, cy + s * 0.3f, cx + s * 0.22f, cy + s * 0.08f);
        eyes.lineTo(cx + s * 0.12f, cy + s * 0.14f);
        eyes.lineTo(cx + s * 0.08f, cy + s * 0.08f);
        eyes.lineTo(cx - s * 0.08f, cy + s * 0.08f);
        eyes.lineTo(cx - s * 0.12f, cy + s * 0.14f);
        eyes.close();
        c.drawPath(eyes, face);
    }

    private void triangle(float x, float y, float r) {
        eyes.moveTo(x, y - r * 0.6f);
        eyes.lineTo(x + r * 0.7f, y + r * 0.5f);
        eyes.lineTo(x - r * 0.7f, y + r * 0.5f);
        eyes.close();
    }

    void stop() {
        flicker.cancel();
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int alpha) { body.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter f) { body.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
