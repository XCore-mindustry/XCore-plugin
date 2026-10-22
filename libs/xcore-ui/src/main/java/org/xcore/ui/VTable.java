package org.xcore.ui;

import java.util.List;

/**
 * Grid container. Rows are expressed explicitly: children are grouped into rows
 * at build time via {@link RowMarker} sentinels produced by {@code Ui.table(row -> ...)}.
 */
public record VTable(
        String id, UiLayout layout,
        String background, Float margin, Boolean wrap,
        List<Object> entries
) implements VNode {

    public VTable {
        if (id != null) VNode.requireSafeId(id, "table");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public Kind kind() {
        return Kind.TABLE;
    }

    @Override
    public List<VNode> children() {
        return entries.stream().filter(e -> e instanceof VNode).map(e -> (VNode) e).toList();
    }

    /**
     * Cell defaults inside a {@link VTable#entries} list: the client applies them to every cell
     * added after them in the same table (not to nested tables).
     */
    public record Defaults(UiLayout layout) {
        public Defaults {
            java.util.Objects.requireNonNull(layout, "layout");
        }
    }

    /** Sentinel row break inside a {@link VTable#entries} list. */
    public record RowMarker() {
        public static final RowMarker ROW = new RowMarker();
    }
}
