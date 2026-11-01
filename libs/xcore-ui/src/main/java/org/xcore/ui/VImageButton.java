package org.xcore.ui;

/** Interactive icon button. Compiles to {@code ImageButtonBuilder}. */
public record VImageButton(
        String id, UiLayout layout,
        String icon, String iconPlaceholder,
        String style, String clicked, String group, Boolean checked, boolean disabled
) implements VNode {

    public VImageButton {
        if (id != null) VNode.requireSafeId(id, "imageButton");
        if (icon == null || icon.isEmpty()) {
            throw new IllegalArgumentException("imageButton icon must not be null or empty");
        }
    }

    @Override
    public Kind kind() {
        return Kind.IMAGE_BUTTON;
    }
}
