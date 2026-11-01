package org.xcore.ui;

/** Interactive text button. Compiles to {@code ButtonBuilder}. */
public record VButton(
        String id, UiLayout layout,
        Text text, String icon, String iconPlaceholder,
        String style, String clicked, String group, Boolean checked, boolean disabled
) implements VNode {

    public VButton {
        if (id != null) VNode.requireSafeId(id, "button");
    }

    @Override
    public Kind kind() {
        return Kind.BUTTON;
    }
}
