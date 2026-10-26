package io.github.caferyukseloglu.tvhome;

import java.util.ArrayList;
import java.util.List;

/** The Hue app's scene palettes (res/raw/hue_palettes.txt). Pure Java, tested by tools/PalettesTest. */
final class Palettes {
    static final class Palette {
        final String name;
        final int[] colors; // 0xRRGGBB

        Palette(String name, int[] colors) {
            this.name = name;
            this.colors = colors;
        }
    }

    private Palettes() {}

    /** "name|RRGGBB,RRGGBB" per line; '#' comments, blank and malformed lines are skipped. */
    static List<Palette> parse(String text) {
        List<Palette> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] parts = line.trim().split("\\|");
            if (line.startsWith("#") || parts.length != 2) continue;
            try {
                String[] hex = parts[1].split(",");
                int[] colors = new int[hex.length];
                for (int i = 0; i < hex.length; i++) colors[i] = Integer.parseInt(hex[i].trim(), 16);
                out.add(new Palette(parts[0].trim(), colors));
            } catch (NumberFormatException skip) {
                // a bad color skips that one line, not the file
            }
        }
        return out;
    }

    /** n points from a palette, repeating it from the start when it has fewer colors. */
    static int[] spread(int[] colors, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = colors[i % colors.length];
        return out;
    }
}
