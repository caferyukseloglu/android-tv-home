package io.github.caferyukseloglu.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure ordering, so it can be tested without Android: the user's arrangement, new apps after it, hidden last. */
final class AppOrder {
    private AppOrder() {}

    /** byLabel: installed apps sorted by name. saved: the order the user arranged (may name uninstalled apps). */
    static List<String> order(List<String> byLabel, List<String> saved, Set<String> hidden) {
        Set<String> out = new LinkedHashSet<>();
        for (String pkg : saved) {
            if (byLabel.contains(pkg) && !hidden.contains(pkg)) out.add(pkg);
        }
        for (String pkg : byLabel) {
            if (!hidden.contains(pkg)) out.add(pkg);
        }
        out.addAll(byLabel); // whatever is left is hidden, kept in name order
        return new ArrayList<>(out);
    }
}
