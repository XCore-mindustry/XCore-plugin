package org.xcore.ui;

import java.util.List;

/** Layered container; children render on top of each other. Compiles to {@code StackBuilder}. */
public record VStack(String id, UiLayout layout, List<VNode> children) implements VNode {

    public VStack {
        if (id != null) VNode.requireSafeId(id, "stack");
        children = VNode.copyChildren(children);
    }

    @Override
    public Kind kind() {
        return Kind.STACK;
    }

    @Override
    public List<VNode> children() {
        return children;
    }
}
