package io.github.caferyukseloglu.tvhome;

/** Plain-Java test, no Android needed: ./home.sh test */
public final class ColorsTest {
    public static void main(String[] args) {
        double[] red = Colors.rgbToXy(255, 0, 0);
        check(near(red[0], 0.70, 0.02) && near(red[1], 0.30, 0.02), "red lands in Hue's red corner");
        double[] white = Colors.rgbToXy(255, 255, 255);
        check(near(white[0], 0.32, 0.02) && near(white[1], 0.33, 0.02), "white lands near D65");
        check(Colors.rgbToXy(0, 0, 0)[0] == Colors.WHITE_POINT[0], "black maps to the white point, not NaN");

        int back = Colors.xyToRgb(red[0], red[1]);
        check(((back >> 16) & 255) == 255 && ((back >> 8) & 255) < 60 && (back & 255) < 60, "red survives a round trip: " + Integer.toHexString(back));
        check(Colors.ctToRgb(500) == 0xFFB46B && Colors.ctToRgb(100) == 0xDDE8FF, "ct clamps to warm and cool");

        // 10x2 image: left half red, right half blue -> palette runs red ... blue, vivid.
        int[] img = new int[20];
        for (int i = 0; i < 20; i++) img[i] = (i % 10) < 5 ? 0xFF800000 : 0xFF000080;
        int[] pal = Colors.palette(img, 10, 2, 5);
        check(pal.length == 5, "five strip points");
        check(((pal[0] >> 16) & 255) > 200 && (pal[0] & 255) < 40, "left point is a bright red: " + Integer.toHexString(pal[0]));
        check((pal[4] & 255) > 200 && ((pal[4] >> 16) & 255) < 40, "right point is a bright blue: " + Integer.toHexString(pal[4]));
        int[] dark = Colors.palette(new int[20], 10, 2, 5);
        check(dark[2] != 0, "a black cover still gives a visible color, not off");
        System.out.println("ColorsTest: 9 passed");
    }

    private static boolean near(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
