package org.xcore.ui;

/** Empty spacer cell. Compiles to {@code SpaceBuilder}. */
public record VSpace(String id, UiLayout layout) implements VNode {

    @Override
    public Kind kind() {
        return Kind.SPACE;
    }
}
