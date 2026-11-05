package org.xcore.testkit.ui;

import arc.struct.Seq;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import mindustry.ui.builder.UiKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a client builds from a {@link NodeBuilder}, as {@code UiTreeBuilder} does it: the elements
 * in their tables, which of them carry an id, and in which build each was made. No geometry.
 *
 * <p>{@code UiBuilder} keeps a node's entries package-private, so they are read reflectively.
 */
final class ClientTree {

    private ClientTree() {
    }

    /** One build: a {@code menuBuilder} or a {@code menuBuilderUpdate}, with the ids it made. */
    static final class Pass {
        final Map<String, Element> ids = new LinkedHashMap<>();
    }

    /** An element the client built. */
    static final class Element {
        final UiKey type;
        final NodeBuilder<?> node;
        final Pass pass;
        final List<Element> children = new ArrayList<>();
        boolean attached = true;
        /**
         * Whether a table put it in a cell. A stack adds its children without cells, and the client
         * applies cell properties, {@code disabled} among them, only to a cell's element.
         */
        boolean inCell = true;
        /** What this input holds now: its initial value until the player enters another. */
        Object value;

        Element(UiKey type, NodeBuilder<?> node, Pass pass) {
            this.type = type;
            this.node = node;
            this.pass = pass;
            this.value = initialValue();
        }

        String id() {
            return str(node, UiKey.id);
        }

        /** The answer a press of this element sends, or {@code null} for one that sends none. */
        String clicked() {
            return switch (type) {
                case button, imageButton, buttonTable -> str(node, UiKey.clicked);
                default -> null;
            };
        }

        /** The answer pressing Enter in this field sends, or {@code null}. */
        String enter() {
            return type == UiKey.field ? str(node, UiKey.enter) : null;
        }

        /**
         * Whether the client disables it: {@code applyCellProp} runs for every entry of an element
         * in a cell, and each {@code disabled: true} disables it, so one true entry is enough
         * whatever else is there. A direct child of a stack is in no cell and stays enabled.
         */
        boolean disabled() {
            if (!inCell) return false;
            for (Object[] e : entries(node)) {
                if (e[0] == UiKey.disabled && Boolean.TRUE.equals(e[1])) return true;
            }
            return false;
        }

        /** A table the client clears and builds a patch into: a table, a button table, a pane's inner table. */
        boolean patchable() {
            return type == UiKey.table || type == UiKey.buttonTable || type == UiKey.pane;
        }

        /** What this element sends with a press as a form value, or {@code null} if it sends none. */
        private Object initialValue() {
            return switch (type) {
                case field -> str(node, UiKey.text) == null ? "" : str(node, UiKey.text);
                case check -> bool(node, UiKey.checked);
                case slider -> {
                    // new Slider(min, max, step) starts at min, then setValue(defaultValue) if there is one.
                    Float def = num(node, UiKey.defaultValue);
                    yield def == null ? sliderMin() : sliderValue(def);
                }
                default -> null;
            };
        }

        /**
         * Takes what the player entered, as the input would hold it: text for a field, a boolean
         * for a check, and for a slider a number set as {@code Slider.setValue} sets it.
         *
         * @throws IllegalArgumentException when this input cannot hold {@code entered}
         */
        Object accept(Object entered) {
            return switch (type) {
                case field -> entered instanceof String s ? s : reject(entered, "text");
                case check -> entered instanceof Boolean b ? b : reject(entered, "a boolean");
                case slider -> entered instanceof Number n ? sliderValue(n.floatValue()) : reject(entered, "a number");
                default -> reject(entered, "nothing");
            };
        }

        private Object reject(Object entered, String holds) {
            throw new IllegalArgumentException("'" + id() + "' is a " + type + " and holds " + holds
                    + "; a player cannot enter " + entered);
        }

        private float sliderMin() {
            Float min = num(node, UiKey.min);
            return min != null ? min : 0f;
        }

        /** {@code ProgressBar.setValue}: round to the step, then clamp to [min, max]. */
        private float sliderValue(float raw) {
            Float max = num(node, UiKey.max);
            Float step = num(node, UiKey.step);
            float hi = max != null ? max : 1f;
            float size = step != null ? step : 0.1f;
            float stepped = (float) Math.round(raw / size) * size;
            return Math.max(sliderMin(), Math.min(hi, stepped));
        }

        void detach() {
            attached = false;
            for (Element child : children) child.detach();
        }
    }

    /** Builds the entries of {@code node} into {@code parent}, recording ids in {@code pass}. */
    static void buildInto(Element parent, NodeBuilder<?> node, Pass pass, ClientScreen screen) {
        for (Object[] entry : entries(node)) {
            UiKey key = (UiKey) entry[0];
            if (key == UiKey.row || key == UiKey.defaults || !(entry[1] instanceof NodeBuilder<?> child)) {
                continue;
            }
            String condition = str(child, UiKey.condition);
            if (condition != null && !screen.matches(condition)) {
                continue;
            }
            Element element = new Element(key, child, pass);
            element.attached = parent.attached;
            element.inCell = parent.type != UiKey.stack;
            parent.children.add(element);
            if (key == UiKey.table || key == UiKey.buttonTable || key == UiKey.pane || key == UiKey.stack) {
                buildInto(element, child, pass, screen);
            }
            String id = str(child, UiKey.id);
            if (id != null) {
                pass.ids.put(id, element);
            }
        }
    }

    /** Every attached element under {@code root}, itself included, depth first. */
    static List<Element> attached(Element root) {
        List<Element> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Element element, List<Element> out) {
        if (!element.attached) return;
        out.add(element);
        for (Element child : element.children) collect(child, out);
    }

    // ------------------------------------------------------------------
    // Reading a NodeBuilder
    // ------------------------------------------------------------------

    private static final Field ENTRIES;
    private static final Field ENTRY_KEY;
    private static final Field ENTRY_VALUE;

    static {
        try {
            ENTRIES = NodeBuilder.class.getDeclaredField("entries");
            ENTRIES.setAccessible(true);
            Class<?> entry = Class.forName("mindustry.ui.builder.UiBuilder$Entry");
            ENTRY_KEY = entry.getDeclaredField("key");
            ENTRY_KEY.setAccessible(true);
            ENTRY_VALUE = entry.getDeclaredField("value");
            ENTRY_VALUE.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** The entries of {@code node} as {key, value} pairs, in order. */
    static List<Object[]> entries(NodeBuilder<?> node) {
        try {
            Seq<?> seq = (Seq<?>) ENTRIES.get(node);
            List<Object[]> out = new ArrayList<>(seq.size);
            for (Object e : seq) {
                out.add(new Object[]{ENTRY_KEY.get(e), ENTRY_VALUE.get(e)});
            }
            return out;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The first string under {@code key}, as {@code NodeBuilder.str} reads it. */
    static String str(NodeBuilder<?> node, UiKey key) {
        for (Object[] e : entries(node)) {
            if (e[0] == key && e[1] instanceof String s) return s;
        }
        return null;
    }

    static Float num(NodeBuilder<?> node, UiKey key) {
        for (Object[] e : entries(node)) {
            if (e[0] == key && e[1] instanceof Float f) return f;
        }
        return null;
    }

    static boolean bool(NodeBuilder<?> node, UiKey key) {
        for (Object[] e : entries(node)) {
            if (e[0] == key && e[1] instanceof Boolean b) return b;
        }
        return false;
    }
}
