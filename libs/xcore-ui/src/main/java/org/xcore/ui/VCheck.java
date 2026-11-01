package org.xcore.ui;

/** Interactive checkbox. Compiles to {@code CheckBuilder}. */
public record VCheck(
        String id, UiLayout layout,
        Text text, Boolean checked, String style, String group, boolean disabled
) implements VNode {

    public VCheck {
        if (id != null) VNode.requireSafeId(id, "check");
        if (text == null) text = Text.empty();
    }

    @Override
    public Kind kind() {
        return Kind.CHECK;
    }
}
