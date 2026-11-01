package org.xcore.ui;

import java.util.List;

/**
 * Clickable container button (buttonTable).
 *
 * <p>Unlike a regular {@link VButton} which only contains text and an icon,
 * a {@code VButtonTable} is a full container (can have multiple rows, columns, labels, images, etc.)
 * where clicking anywhere inside triggers the button's action.
 */
public record VButtonTable(
        String id,
        UiLayout layout,
        String style,
        String clicked,
        Float margin,
        Boolean disabled,
        String group,
        Boolean checked,
        List<Object> entries
) implements VNode {

    public VButtonTable {
        if (id != null) VNode.requireSafeId(id, "buttonTable");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public Kind kind() {
        return Kind.BUTTON_TABLE;
    }

    @Override
    public List<VNode> children() {
        return entries.stream().filter(e -> e instanceof VNode).map(e -> (VNode) e).toList();
    }
}
