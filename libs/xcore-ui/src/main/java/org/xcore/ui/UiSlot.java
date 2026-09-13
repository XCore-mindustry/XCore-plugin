package org.xcore.ui;

import java.util.List;
import java.util.function.Consumer;

/** Helper namespace for slot construction inside the fluent DSL. */
final class UiSlot {

    private UiSlot() {
    }

    static VSlot slot(String slotKey, Consumer<Ui.TableBuilder> body) {
        Ui.TableBuilder b = new Ui.TableBuilder();
        body.accept(b);
        VTable table = b.build();
        // A slot body with explicit rows or table-level props must stay a table;
        // otherwise children are lifted directly into the slot.
        if (table.background() != null || table.margin() != null || table.wrap() != null
                || table.entries().stream().anyMatch(e -> e instanceof VTable.RowMarker)) {
            VTable wrapper = new VTable(null, UiLayout.DEFAULT, table.background(), table.margin(), table.wrap(), table.entries());
            return new VSlot(slotKey, table.layout(), List.of(wrapper));
        }
        List<VNode> children = table.entries().stream()
                .map(e -> e instanceof VNode n ? n : null)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new VSlot(slotKey, table.layout(), children);
    }
}
