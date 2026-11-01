package org.xcore.ui;

import java.util.List;
import java.util.Objects;

/**
 * Partial update boundary. The compiled sub-tree is addressable by
 * {@code slotKey} for in-place patching via {@code Call.menuBuilderUpdate}.
 *
 * <p>On a full render the slot body is embedded as a nested table; on a patch
 * only the body is re-sent, so the surrounding dialog (scroll offsets, focus,
 * open state) stays intact.
 */
public record VSlot(String id, String slotKey, UiLayout layout, List<VNode> children) implements VNode {

    public VSlot {
        id = slotKey;
        Objects.requireNonNull(slotKey, "slotKey");
        VNode.requireSafeId(slotKey, "slot");
        children = VNode.copyChildren(children);
    }

    public VSlot(String slotKey, UiLayout layout, List<VNode> children) {
        this(slotKey, slotKey, layout, children);
    }

    @Override
    public Kind kind() {
        return Kind.SLOT;
    }

    @Override
    public List<VNode> children() {
        return children;
    }

    @Override
    public boolean isSlot() {
        return true;
    }

    @Override
    public String slotKey() {
        return slotKey;
    }
}
