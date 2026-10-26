package io.github.caferyukseloglu.tvhome;

import java.util.Arrays;
import java.util.List;

/** Plain-Java test, no Android needed: ./home.sh test */
public final class PalettesTest {
    public static void main(String[] args) {
        List<Palettes.Palette> list = Palettes.parse("# comment\n\nSunset|FF0000,00FF00\nbroken line\nWhite|FFFFFF\n");
        check(list.size() == 2, "comments, blanks and broken lines are skipped: " + list.size());
        check(list.get(0).name.equals("Sunset") && list.get(0).colors[1] == 0x00FF00, "name and colors read");

        check(Arrays.equals(Palettes.spread(new int[] {1, 2}, 5), new int[] {1, 2, 1, 2, 1}), "two colors cycle over five points");
        check(Arrays.equals(Palettes.spread(new int[] {1, 2, 3, 4, 5, 6}, 5), new int[] {1, 2, 3, 4, 5}), "extra colors are dropped");
        System.out.println("PalettesTest: 4 passed");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
