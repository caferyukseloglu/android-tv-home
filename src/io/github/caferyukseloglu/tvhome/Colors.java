package io.github.caferyukseloglu.tvhome;

/** Color math between screen sRGB and Hue's CIE xy. Pure Java, tested by tools/ColorsTest. */
final class Colors {
    static final double[] WHITE_POINT = {0.3127, 0.3290};
    private static final int WARM = 0xFFB46B; // 2000 K, Hue ct 500
    private static final int COOL = 0xDDE8FF; // 6500 K, Hue ct 153

    private Colors() {}

    /** sRGB (0-255) to CIE xy with Philips' wide-gamut matrix. Black has no hue, so it maps to white. */
    static double[] rgbToXy(int r, int g, int b) {
        double lr = linear(r / 255.0);
        double lg = linear(g / 255.0);
        double lb = linear(b / 255.0);
        double x = lr * 0.664511 + lg * 0.154324 + lb * 0.162028;
        double y = lr * 0.283881 + lg * 0.668433 + lb * 0.047685;
        double z = lr * 0.000088 + lg * 0.072310 + lb * 0.986039;
        double sum = x + y + z;
        return sum == 0 ? WHITE_POINT.clone() : new double[] {x / sum, y / sum};
    }

    /** CIE xy to a full-brightness sRGB 0xRRGGBB, for showing a lamp's color on its card. */
    static int xyToRgb(double x, double y) {
        if (y <= 0) return 0xFFFFFF;
        double bigX = x / y;
        double bigZ = (1 - x - y) / y;
        double r = bigX * 1.656492 - 0.354851 - bigZ * 0.255038;
        double g = -bigX * 0.707196 + 1.655397 + bigZ * 0.036152;
        double b = bigX * 0.051713 - 0.121364 + bigZ * 1.011530;
        double max = Math.max(r, Math.max(g, b));
        if (max <= 0) return 0xFFFFFF;
        return pack(gamma(Math.max(r, 0) / max), gamma(Math.max(g, 0) / max), gamma(Math.max(b, 0) / max));
    }

    /** Hue white temperature (mired 153-500) to a display color between cool and warm white. */
    static int ctToRgb(int mired) {
        double t = (Math.max(153, Math.min(500, mired)) - 153) / 347.0;
        return mix(COOL, WARM, t);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * n colors across an image, left to right, for a gradient strip: each vertical band averaged,
     * then brought to full brightness so a dark poster still lights the strip. Black bands become warm white.
     */
    static int[] palette(int[] argb, int w, int h, int n) {
        int[] out = new int[n];
        for (int band = 0; band < n; band++) {
            long r = 0, g = 0, b = 0;
            int count = 0;
            for (int y = 0; y < h; y++) {
                for (int x = band * w / n; x < (band + 1) * w / n; x++) {
                    int c = argb[y * w + x];
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                    count++;
                }
            }
            long max = Math.max(r, Math.max(g, b));
            out[band] = max == 0 ? WARM : (int) (r * 255 / max) << 16 | (int) (g * 255 / max) << 8 | (int) (b * 255 / max);
        }
        return out;
    }

    /** Pushes a color away from grey (factor 1 = unchanged); keeps hue and the brightest channel. */
    static int saturate(int rgb, double factor) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max == min) return rgb;
        double s = Math.min(1.0, (max - min) / (double) max * factor);
        double newMin = max * (1 - s);
        double scale = (max - newMin) / (max - min); // stretch each channel between the new min and max
        return channel(max - (max - r) * scale) << 16 | channel(max - (max - g) * scale) << 8 | channel(max - (max - b) * scale);
    }

    /** 0 = black frame, 1 = white frame. */
    static double luminance(int[] argb) {
        double sum = 0;
        for (int c : argb) sum += (0.2126 * ((c >> 16) & 255) + 0.7152 * ((c >> 8) & 255) + 0.0722 * (c & 255)) / 255;
        return argb.length == 0 ? 0 : sum / argb.length;
    }

    /** The colors moved `steps` places toward the start, wrapping: one step of the slow flow along a strip. */
    static int[] rotate(int[] colors, int steps) {
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) out[i] = colors[(i + steps) % colors.length];
        return out;
    }

    private static int channel(double v) {
        return (int) Math.round(Math.max(0, Math.min(255, v)));
    }

    private static double linear(double c) {
        return c > 0.04045 ? Math.pow((c + 0.055) / 1.055, 2.4) : c / 12.92;
    }

    private static int gamma(double c) {
        double v = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    private static int pack(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
