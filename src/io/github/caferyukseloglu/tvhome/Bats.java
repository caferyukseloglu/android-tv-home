package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Halloween: a small colony of bats rises from the bottom of Home and flies off the top, once.
 * Each bat flaps (the wings squash on a sine), weaves sideways and fades in and out at the ends.
 * Drawn from one path, no bitmaps; it draws nothing once the flight is over.
 */
final class Bats extends View {
    private static final int COUNT = 9;
    private static final long FLIGHT_MS = 6500;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bat = shape();
    private final float[] x0 = new float[COUNT], drift = new float[COUNT], delay = new float[COUNT];
    private final float[] life = new float[COUNT], size = new float[COUNT], flapHz = new float[COUNT], phase = new float[COUNT];
    private long start = -1;

    Bats(Context c) {
        super(c);
        paint.setColor(0xF0050308);
        setVisibility(GONE); // no layer and no draw between flights
    }

    void fly() {
        Random r = new Random();
        for (int i = 0; i < COUNT; i++) {
            x0[i] = 0.1f + r.nextFloat() * 0.8f;
            drift[i] = (r.nextFloat() - 0.5f) * 0.5f;
            delay[i] = r.nextFloat() * 0.35f;
            life[i] = 0.45f + r.nextFloat() * 0.2f;
            size[i] = 26 + r.nextFloat() * 30;
            flapHz[i] = 5 + r.nextFloat() * 3;
            phase[i] = r.nextFloat() * 6.28f;
        }
        start = SystemClock.uptimeMillis();
        setVisibility(VISIBLE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (start < 0) return;
        float elapsed = (SystemClock.uptimeMillis() - start) / (float) FLIGHT_MS;
        if (elapsed > 1f) {
            start = -1;
            setVisibility(GONE);
            return;
        }
        float seconds = (SystemClock.uptimeMillis() - start) / 1000f;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < COUNT; i++) {
            float t = (elapsed - delay[i]) / life[i];
            if (t <= 0f || t >= 1f) continue;
            float s = size[i] * density;
            float y = getHeight() + s - (getHeight() + 2.5f * s) * t * (0.6f + 0.4f * t); // speeds up as it climbs
            float x = (x0[i] + drift[i] * t) * getWidth() + 18 * density * (float) Math.sin(seconds * 1.6 * Math.PI + phase[i]);
            float flap = 0.35f + 0.65f * Math.abs((float) Math.sin(seconds * flapHz[i] * Math.PI + phase[i]));
            paint.setAlpha((int) (240 * Math.min(1f, Math.min(t / 0.12f, (1f - t) / 0.2f))));
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(s, s * flap);
            canvas.drawPath(bat, paint);
            canvas.restore();
        }
        postInvalidateOnAnimation();
    }

    /** A bat in a 1 x 1 box around its center: scalloped wings, a round body, two ears. */
    private static Path shape() {
        Path p = new Path();
        p.moveTo(0f, -0.05f);
        p.quadTo(-0.25f, -0.35f, -0.5f, -0.2f);
        p.lineTo(-0.42f, 0.02f);
        p.quadTo(-0.36f, -0.04f, -0.3f, 0.08f);
        p.quadTo(-0.22f, 0.0f, -0.15f, 0.12f);
        p.quadTo(-0.07f, 0.04f, 0f, 0.16f);
        p.quadTo(0.07f, 0.04f, 0.15f, 0.12f);
        p.quadTo(0.22f, 0.0f, 0.3f, 0.08f);
        p.quadTo(0.36f, -0.04f, 0.42f, 0.02f);
        p.lineTo(0.5f, -0.2f);
        p.quadTo(0.25f, -0.35f, 0f, -0.05f);
        p.close();
        p.addCircle(0f, 0.0f, 0.07f, Path.Direction.CW);
        p.moveTo(-0.05f, -0.05f);
        p.lineTo(-0.035f, -0.14f);
        p.lineTo(-0.01f, -0.06f);
        p.close();
        p.moveTo(0.05f, -0.05f);
        p.lineTo(0.035f, -0.14f);
        p.lineTo(0.01f, -0.06f);
        p.close();
        return p;
    }
}
