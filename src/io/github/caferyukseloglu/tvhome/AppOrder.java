package io.github.caferyukseloglu.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure ordering, so it can be tested without Android: recently used first, hidden last. */
final class AppOrder {
    private AppOrder() {}

    /** byLabel is already sorted by name; that order is kept for apps never opened (stable sort). */
    static List<String> order(List<String> byLabel, Map<String, Long> lastUsed, Set<String> hidden) {
        List<String> visible = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String pkg : byLabel) {
            (hidden.contains(pkg) ? rest : visible).add(pkg);
        }
        visible.sort((a, b) -> Long.compare(used(lastUsed, b), used(lastUsed, a)));
        visible.addAll(rest);
        return visible;
    }

    private static long used(Map<String, Long> lastUsed, String pkg) {
        Long t = lastUsed.get(pkg);
        return t == null ? 0 : t;
    }
}
