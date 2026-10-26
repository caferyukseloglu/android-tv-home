package io.github.caferyukseloglu.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Plain-Java test, no Android needed: ./home.sh test */
public final class AppOrderTest {
    public static void main(String[] args) {
        List<String> byLabel = Arrays.asList("hbo", "netflix", "stremio", "youtube");

        check(AppOrder.order(byLabel, Collections.emptyList(), Collections.emptySet()),
                byLabel, "nothing arranged yet: name order");
        check(AppOrder.order(byLabel, Arrays.asList("youtube", "netflix"), Collections.emptySet()),
                Arrays.asList("youtube", "netflix", "hbo", "stremio"), "arranged apps first, new ones after by name");
        check(AppOrder.order(byLabel, Arrays.asList("gone", "stremio"), Collections.emptySet()),
                Arrays.asList("stremio", "hbo", "netflix", "youtube"), "uninstalled apps drop out of the saved order");
        check(AppOrder.order(byLabel, Arrays.asList("youtube", "hbo"), new HashSet<>(Arrays.asList("youtube"))),
                Arrays.asList("hbo", "netflix", "stremio", "youtube"), "hidden go last even if arranged first");
        System.out.println("AppOrderTest: 4 passed");
    }

    private static void check(List<String> got, List<String> want, String name) {
        if (!got.equals(want)) throw new AssertionError(name + ": got " + got + ", want " + want);
    }
}
