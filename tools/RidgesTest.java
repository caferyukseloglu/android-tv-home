package io.github.caferyukseloglu.tvhome;

/** Plain-Java test, no Android needed: ./home.sh test */
public final class RidgesTest {
    public static void main(String[] args) {
        float[] a = Ridges.line(7, 65, 0.55f);
        check(a.length == 65, "one height per point");
        for (float h : a) check(h >= 0f && h <= 1f, "heights stay in 0..1: " + h);
        check(java.util.Arrays.equals(a, Ridges.line(7, 65, 0.55f)), "the same seed draws the same ridge");
        check(!java.util.Arrays.equals(a, Ridges.line(8, 65, 0.55f)), "another seed draws another ridge");
        float flatSpread = spread(Ridges.line(3, 65, 0.1f)), roughSpread = spread(Ridges.line(3, 65, 0.9f));
        check(roughSpread > flatSpread, "more roughness, bigger peaks: " + flatSpread + " vs " + roughSpread);
        System.out.println("RidgesTest: 5 passed");
    }

    private static float spread(float[] h) {
        float min = 1, max = 0;
        for (float v : h) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
