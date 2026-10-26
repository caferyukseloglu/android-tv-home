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

        // 12x6 frame: left edge red, right edge blue, top middle green, rest black.
        int w = 12, h = 6;
        int[] frame = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                frame[y * w + x] = x < 2 ? 0xFF0000 : x >= 10 ? 0x0000FF : (y < 2 && x >= 4 && x < 8) ? 0x00FF00 : 0;
            }
        }
        int[] zones = Colors.edgeColors(frame, w, h);
        check(zones.length == 5, "five strip points");
        check(zones[0] == 0xFF0000, "left zone is the left edge: " + Integer.toHexString(zones[0]));
        check(zones[4] == 0x0000FF, "right zone is the right edge: " + Integer.toHexString(zones[4]));
        check(((zones[2] >> 8) & 255) > 100, "top middle zone sees the green: " + Integer.toHexString(zones[2]));

        check(Colors.allBlack(new int[] {0xFF000000, 0}), "a DRM frame counts as black");
        check(!Colors.allBlack(new int[] {0, 0x010000}), "a dark scene is not a DRM frame");
        System.out.println("ColorsTest: 11 passed");
    }

    private static boolean near(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
