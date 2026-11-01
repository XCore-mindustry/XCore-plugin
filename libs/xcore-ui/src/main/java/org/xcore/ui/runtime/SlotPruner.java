package org.xcore.ui.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes nested dirty slots whose ancestor is also dirty.
 *
 * <p>When slot A and its descendant A/B are both flagged for update, patching A
 * re-creates A/B from scratch; sending the A/B patch would be redundant traffic
 * and could race the A patch on the client. The pruner keeps only the
 * outermost dirty slot per ancestry chain.
 *
 * <p>Algorithm (O(n^2) worst case, n = dirty slots, trivially small in practice):
 * <ol>
 *   <li>Sort candidates by path length ascending (ancestors first).</li>
 *   <li>For each candidate, if any retained slot is its ancestor, drop it.</li>
 *   <li>Otherwise retain it.</li>
 * </ol>
 */
public final class SlotPruner {

    private SlotPruner() {
    }

    /** Returns the minimal set of slots to patch, ancestors superseding descendants. */
    public static <T> List<SlotKey<T>> prune(List<SlotKey<T>> dirty) {
        if (dirty == null || dirty.size() <= 1) {
            return dirty == null ? List.of() : List.copyOf(dirty);
        }

        List<SlotKey<T>> sorted = new ArrayList<>(dirty);
        sorted.sort((a, b) -> Integer.compare(a.path().length(), b.path().length()));

        Set<SlotKey<T>> retained = new LinkedHashSet<>();
        outer:
        for (SlotKey<T> candidate : sorted) {
            for (SlotKey<T> kept : retained) {
                if (kept.isAncestorOf(candidate)) {
                    continue outer;
                }
            }
            retained.add(candidate);
        }
        return List.copyOf(retained);
    }
}
