package org.xcore.ui;

/** Text label. Compiles to {@code LabelBuilder}. */
public record VLabel(String id, UiLayout layout, Text text, boolean wrap, String style, String labelAlign) implements VNode {

    public VLabel {
        if (id != null) VNode.requireSafeId(id, "label");
        if (text == null) text = Text.empty();
    }

    @Override
    public Kind kind() {
        return Kind.LABEL;
    }
}
