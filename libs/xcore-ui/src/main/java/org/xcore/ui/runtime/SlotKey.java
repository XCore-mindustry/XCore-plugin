package org.xcore.ui.runtime;

import org.xcore.ui.VNode;

/**
 * Addressable partial-update boundary. Slot keys form hierarchical paths
 * separated by {@code '/'} so the {@link SlotPruner} can detect containment
 * (an ancestor patch supersedes a nested one).
 *
 * <p>Type parameter {@code <T>} is the logical content type of the slot; it is
 * documentation only and keeps different slots distinguishable at the call site.
 */
public record SlotKey<T>(String path) {

    public SlotKey {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("slot path must not be null or empty");
        }
        VNode.requireSafeId(path, "slot");
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
            throw new IllegalArgumentException("slot path must not have empty segments: " + path);
        }
    }

    public static <T> SlotKey<T> of(String path) {
        return new SlotKey<>(path);
    }

    /** Creates a child slot key under this one. */
    public <C> SlotKey<C> child(String segment) {
        return new SlotKey<>(path + "/" + segment);
    }

    public boolean isAncestorOf(SlotKey<?> other) {
        return other != null && other != this && other.path.startsWith(path + "/");
    }

    @Override
    public String toString() {
        return path;
    }
}
