package org.xcore.ui;

/** Empty spacer cell. Compiles to {@code SpaceBuilder}. */
public record VSpace(String id, UiLayout layout) implements VNode {

    public VSpace {
        if (id != null) VNode.requireSafeId(id, "space");
    }

    @Override
    public Kind kind() {
        return Kind.SPACE;
    }
}
