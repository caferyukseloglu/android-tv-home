package io.github.caferyukseloglu.tvhome;

import java.util.Random;

/** A mountain skyline for the scene themes. Pure Java, tested by tools/RidgesTest. */
final class Ridges {
    private Ridges() {}

    /**
     * points heights in 0..1 (points = 2^n + 1), by midpoint displacement: each halving of the
     * span moves the middle by a random amount that shrinks by roughness. Same seed, same ridge.
     */
    static float[] line(long seed, int points, float roughness) {
        Random r = new Random(seed);
        float[] h = new float[points];
        h[0] = r.nextFloat();
        h[points - 1] = r.nextFloat();
        float amount = 1f;
        for (int step = points - 1; step > 1; step /= 2) {
            for (int i = step / 2; i < points; i += step) {
                h[i] = (h[i - step / 2] + h[i + step / 2]) / 2 + (r.nextFloat() - 0.5f) * amount;
            }
            amount *= roughness;
        }
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float v : h) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        float range = Math.max(1e-6f, max - min);
        for (int i = 0; i < points; i++) h[i] = (h[i] - min) / range * Math.min(1f, range); // keep flat ridges flat
        return h;
    }
}
