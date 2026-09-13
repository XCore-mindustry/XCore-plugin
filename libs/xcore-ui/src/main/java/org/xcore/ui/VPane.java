package org.xcore.ui;

import java.util.List;

/** Scrollable viewport wrapping an inner table. Compiles to {@code PaneBuilder}. */
public record VPane(String id, UiLayout layout, String style, List<VNode> children) implements VNode {

    public VPane {
        if (id != null) VNode.requireSafeId(id, "pane");
        children = VNode.copyChildren(children);
    }

    @Override
    public Kind kind() {
        return Kind.PANE;
    }

    @Override
    public List<VNode> children() {
        return children;
    }
}
