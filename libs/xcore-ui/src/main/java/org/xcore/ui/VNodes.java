package org.xcore.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Tree walking utilities over the {@link VNode} AST. */
public final class VNodes {

    private VNodes() {
    }

    /** Collects all nodes matching the predicate, pre-order. */
    public static List<VNode> collect(VNode root, Predicate<VNode> filter) {
        Map<String, VNode> sink = new LinkedHashMap<>();
        collectInto(root, filter, sink);
        return List.copyOf(sink.values());
    }

    /** Finds the first slot with the given key, or null. */
    public static VSlot findSlot(VNode root, String slotKey) {
        for (VNode node : walk(root)) {
            if (node instanceof VSlot slot && slot.slotKey().equals(slotKey)) {
                return slot;
            }
        }
        return null;
    }

    /** All slots in the tree keyed by their path. Nested slots included. */
    public static Map<String, VSlot> slotsByKey(VNode root) {
        Map<String, VSlot> map = new LinkedHashMap<>();
        collectInto(root, n -> n.isSlot(), new LinkedHashMap<>());
        for (VNode node : walk(root)) {
            if (node instanceof VSlot slot) {
                map.putIfAbsent(slot.slotKey(), slot);
            }
        }
        return map;
    }

    /** Pre-order traversal as a lazy iterable. */
    public static Iterable<VNode> walk(VNode root) {
        return () -> new java.util.Iterator<>() {
            private final java.util.Deque<VNode> stack = new java.util.ArrayDeque<>(List.of(root));

            @Override
            public boolean hasNext() {
                return !stack.isEmpty();
            }

            @Override
            public VNode next() {
                VNode node = stack.pop();
                List<VNode> children = node.children();
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
                return node;
            }
        };
    }

    private static void collectInto(VNode root, Predicate<VNode> filter, Map<String, VNode> sink) {
        for (VNode node : walk(root)) {
            if (filter.test(node)) {
                sink.put(node.id() != null ? node.id() : "#" + sink.size(), node);
            }
        }
    }
}
