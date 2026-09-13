package org.xcore.ui;

/** Interactive text field. Compiles to {@code FieldBuilder}. */
public record VField(
        String id, UiLayout layout,
        Text text, Text hint, Integer maxLength, String style, String enter, boolean disabled
) implements VNode {

    public VField {
        if (id != null) VNode.requireSafeId(id, "field");
        if (text == null) text = Text.empty();
        if (hint == null) hint = Text.empty();
        if (maxLength != null && (maxLength < 1 || maxLength > 1000)) {
            throw new IllegalArgumentException("field maxLength must be in [1, 1000]: " + maxLength);
        }
    }

    @Override
    public Kind kind() {
        return Kind.FIELD;
    }
}
