package io.github.caferyukseloglu.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Plain-Java test, no Android needed: ./home.sh test */
public final class AppOrderTest {
    public static void main(String[] args) {
        List<String> byLabel = Arrays.asList("hbo", "netflix", "stremio", "youtube");
        Map<String, Long> used = new HashMap<>();
        used.put("youtube", 200L);
        used.put("netflix", 100L);

        check(AppOrder.order(byLabel, Collections.emptyMap(), Collections.emptySet()),
                byLabel, "never used: name order");
        check(AppOrder.order(byLabel, used, Collections.emptySet()),
                Arrays.asList("youtube", "netflix", "hbo", "stremio"), "most recent first, rest by name");
        check(AppOrder.order(byLabel, used, new HashSet<>(Arrays.asList("youtube"))),
                Arrays.asList("netflix", "hbo", "stremio", "youtube"), "hidden go last even if recent");
        System.out.println("AppOrderTest: 3 passed");
    }

    private static void check(List<String> got, List<String> want, String name) {
        if (!got.equals(want)) throw new AssertionError(name + ": got " + got + ", want " + want);
    }
}
