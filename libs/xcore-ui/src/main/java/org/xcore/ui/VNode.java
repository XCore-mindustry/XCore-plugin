package org.xcore.ui;

import org.xcore.ui.component.Component;

import java.util.List;
import java.util.Objects;

/**
 * Immutable virtual DOM node.
 *
 * <p>The entire interface is a sealed record hierarchy so that:
 * <ul>
 *   <li>ASTs are trivially comparable in tests (records give structural equality),</li>
 *   <li>every construction site is validated (compact constructors),</li>
 *   <li>rendering is a pure function of (model -> VNode) with zero hidden state.</li>
 * </ul>
 *
 * <p>A tree compiles into Mindustry v160 {@code mindustry.ui.builder.UiBuilder.NodeBuilder}
 * via {@link VNodeCompiler} right before transmission.
 */
public sealed interface VNode permits
        VTable, VPane, VStack, VLabel, VImage, VButton, VImageButton,
        VField, VCheck, VSlider, VSpace, VSlot, VComponentNode {

    /** Layout properties for a node's parent cell. Immutable; use {@link UiLayout#mutate()} to derive. */
    UiLayout layout();

    /** Unique element id within the dialog; used by {@code Call.menuBuilderUpdate} and {@link MenuResult} value maps. */
    String id();

    /** Node kinds recognized by the runtime. Mirrors {@code mindustry.ui.builder.UiKey} node types. */
    enum Kind {
        TABLE, PANE, STACK, LABEL, IMAGE, BUTTON, IMAGE_BUTTON, FIELD, CHECK, SLIDER, SPACE, SLOT, COMPONENT
    }

    Kind kind();

    // ------------------------------------------------------------------
    // VSlot / component helpers
    // ------------------------------------------------------------------

    /** Extracts children for container nodes, or {@code List.of()} for leaves. */
    default List<VNode> children() {
        return List.of();
    }

    /** Renders a {@link VComponentNode} subtree into plain nodes (component expansion happens at compile time). */
    default VNode normalized() {
        return this;
    }

    /** VSlot accessors. */
    default boolean isSlot() {
        return false;
    }

    default String slotKey() {
        return null;
    }

    // ------------------------------------------------------------------
    // Shared validation
    // ------------------------------------------------------------------

    static String requireSafeId(String id, String what) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException(what + " id must not be null or empty");
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/')) {
                throw new IllegalArgumentException(what + " id contains illegal character '" + c + "': " + id);
            }
        }
        return id;
    }

    static List<VNode> copyChildren(List<VNode> children) {
        return children == null ? List.of() : List.copyOf(children.stream().map(Objects::requireNonNull).toList());
    }

    /** Marker for nodes that act as partial-update boundaries. */
    static VNode requireSlot(VNode node) {
        if (node instanceof VSlot slot) return slot;
        throw new IllegalArgumentException("Expected a VSlot, got " + (node == null ? "null" : node.kind()));
    }

    /** Component prop type erased wrapper used by {@link Component} instances. */
    record ComponentProps(Class<?> type, Object value) {
        public ComponentProps {
            Objects.requireNonNull(type, "type");
        }
    }
}
