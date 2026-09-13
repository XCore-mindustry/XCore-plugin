package org.xcore.ui;

import org.xcore.ui.component.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent entry points for constructing {@link VNode} trees.
 *
 * <pre>{@code
 * Ui.table(t -> {
 *     t.label(Text.t("title"), l -> l.growX().wrap());
 *     t.row();
 *     t.slot(SLOT_TAB, tab -> tab.grow());
 * });
 * }</pre>
 */
public final class Ui {

    private Ui() {
    }

    public static UiLayout.Builder layout() {
        return new UiLayout.Builder();
    }

    // ------------------------------------------------------------------
    // Containers
    // ------------------------------------------------------------------

    /** Builds a table; children and {@code row()} markers are added inside the consumer. */
    public static VTable table(Consumer<TableBuilder> consumer) {
        TableBuilder b = new TableBuilder();
        consumer.accept(b);
        return b.build();
    }

    /** Builds a pane (scrollable). */
    public static VPane pane(Consumer<PaneBuilder> consumer) {
        PaneBuilder b = new PaneBuilder();
        consumer.accept(b);
        return b.build();
    }

    /** Builds a stack (layered). */
    public static VStack stack(Consumer<StackBuilder> consumer) {
        StackBuilder b = new StackBuilder();
        consumer.accept(b);
        return b.build();
    }

    // ------------------------------------------------------------------
    // Leaves
    // ------------------------------------------------------------------

    public static VLabel label(Text text, Consumer<UiLayout.Builder> layout) {
        return new VLabel(null, buildLayout(layout), text, false, null, null);
    }

    public static VLabel label(Text text) {
        return label(text, null);
    }

    public static VImage image(String region, Consumer<UiLayout.Builder> layout) {
        return new VImage(null, buildLayout(layout), region, null, null);
    }

    public static VImage image(String region) {
        return image(region, null);
    }

    public static VButton button(Text text, String clicked, Consumer<ButtonSpec> spec) {
        ButtonSpec s = new ButtonSpec();
        if (spec != null) spec.accept(s);
        return s.toNode(text, clicked);
    }

    public static VImageButton imageButton(String icon, String clicked, Consumer<ImageButtonSpec> spec) {
        ImageButtonSpec s = new ImageButtonSpec();
        if (spec != null) spec.accept(s);
        return s.toNode(icon, clicked);
    }

    public static VField field(String id, Consumer<FieldSpec> spec) {
        FieldSpec s = new FieldSpec();
        if (spec != null) spec.accept(s);
        return s.toNode(id);
    }

    public static VCheck check(Text text, Consumer<CheckSpec> spec) {
        CheckSpec s = new CheckSpec();
        if (spec != null) spec.accept(s);
        return s.toNode(text);
    }

    public static VSlider slider(float min, float max, float step, Consumer<SliderSpec> spec) {
        SliderSpec s = new SliderSpec();
        if (spec != null) spec.accept(s);
        return s.toNode(min, max, step);
    }

    public static VSpace space(Consumer<UiLayout.Builder> layout) {
        return new VSpace(null, buildLayout(layout));
    }

    /** Embeds a component with fixed props. */
    public static <P> VNode component(Component<P> component, P props) {
        return component.with(props);
    }
    private static UiLayout buildLayout(Consumer<UiLayout.Builder> consumer) {
        if (consumer == null) return UiLayout.DEFAULT;
        UiLayout.Builder b = new UiLayout.Builder();
        consumer.accept(b);
        return b.build();
    }

    // ==================================================================
    // Builders
    // ==================================================================

    /** Mutable builder for {@link VTable}; rows carry explicit {@code row()} markers. */
    public static final class TableBuilder {
        private String id;
        private String background;
        private Float margin;
        private Boolean wrap;
        private UiLayout.Builder layout = new UiLayout.Builder();
        private final List<Object> entries = new ArrayList<>();

        public TableBuilder id(String id) { this.id = id; return this; }
        public TableBuilder background(String bg) { this.background = bg; return this; }
        public TableBuilder margin(float m) { this.margin = m; return this; }
        public TableBuilder wrap() { this.wrap = true; return this; }
        public TableBuilder layout(Consumer<UiLayout.Builder> l) { l.accept(layout); return this; }

        /** Ends the current row; subsequent children go on a new row. */
        public TableBuilder row() {
            entries.add(VTable.RowMarker.ROW);
            return this;
        }

        public TableBuilder add(VNode node) {
            entries.add(node);
            return this;
        }

        public TableBuilder label(Text text, Consumer<UiLayout.Builder> layout) {
            return add(Ui.label(text, layout));
        }

        public TableBuilder label(Text text) {
            return add(Ui.label(text));
        }

        public TableBuilder image(String region, Consumer<UiLayout.Builder> layout) {
            return add(Ui.image(region, layout));
        }

        public TableBuilder button(Text text, String clicked, Consumer<ButtonSpec> spec) {
            return add(Ui.button(text, clicked, spec));
        }

        public TableBuilder field(String id, Consumer<FieldSpec> spec) {
            return add(Ui.field(id, spec));
        }

        public TableBuilder check(Text text, Consumer<CheckSpec> spec) {
            return add(Ui.check(text, spec));
        }

        public TableBuilder slider(float min, float max, float step, Consumer<SliderSpec> spec) {
            return add(Ui.slider(min, max, step, spec));
        }

        public TableBuilder space(Consumer<UiLayout.Builder> layout) {
            return add(Ui.space(layout));
        }

        /** Declares a dynamic slot in this table. */
        public TableBuilder slot(String slotKey, Consumer<TableBuilder> body) {
            return add(UiSlot.slot(slotKey, body));
        }

        public TableBuilder stack(Consumer<StackBuilder> consumer) {
            return add(Ui.stack(consumer));
        }

        public TableBuilder pane(Consumer<PaneBuilder> consumer) {
            return add(Ui.pane(consumer));
        }

        public TableBuilder component(Component<?> component) {
            return add(new org.xcore.ui.VComponentNode(component, List.of()));
        }

        VTable build() {
            return new VTable(id, layout.build(), background, margin, wrap, entries);
        }
    }

    /** Mutable builder for {@link VPane}. */
    public static final class PaneBuilder {
        private String id;
        private String style;
        private UiLayout.Builder layout = new UiLayout.Builder();
        private final List<VNode> children = new ArrayList<>();

        public PaneBuilder id(String id) { this.id = id; return this; }
        public PaneBuilder style(String style) { this.style = style; return this; }
        public PaneBuilder layout(Consumer<UiLayout.Builder> l) { l.accept(layout); return this; }

        public PaneBuilder add(VNode node) { children.add(node); return this; }

        public PaneBuilder label(Text text, Consumer<UiLayout.Builder> layout) {
            return add(Ui.label(text, layout));
        }

        public PaneBuilder table(Consumer<TableBuilder> consumer) {
            return add(Ui.table(consumer));
        }

        VPane build() {
            return new VPane(id, layout.build(), style, children);
        }
    }

    /** Mutable builder for {@link VStack}. */
    public static final class StackBuilder {
        private String id;
        private UiLayout.Builder layout = new UiLayout.Builder();
        private final List<VNode> children = new ArrayList<>();

        public StackBuilder id(String id) { this.id = id; return this; }
        public StackBuilder layout(Consumer<UiLayout.Builder> l) { l.accept(layout); return this; }

        public StackBuilder add(VNode node) { children.add(node); return this; }

        public StackBuilder label(Text text, Consumer<UiLayout.Builder> layout) {
            return add(Ui.label(text, layout));
        }

        public StackBuilder image(String region, Consumer<UiLayout.Builder> layout) {
            return add(Ui.image(region, layout));
        }

        VStack build() {
            return new VStack(id, layout.build(), children);
        }
    }

    // ------------------------------------------------------------------
    // Leaf spec objects
    // ------------------------------------------------------------------

    /** Fluent spec for buttons. */
    public static final class ButtonSpec {
        String id;
        String icon;
        String iconPlaceholder;
        String style;
        String group;
        Boolean checked;
        boolean disabled;
        final UiLayout.Builder layout = new UiLayout.Builder();

        public ButtonSpec id(String id) { this.id = id; return this; }
        public ButtonSpec icon(String icon) { this.icon = icon; return this; }
        public ButtonSpec iconPlaceholder(String p) { this.iconPlaceholder = p; return this; }
        public ButtonSpec style(String style) { this.style = style; return this; }
        public ButtonSpec group(String group) { this.group = group; return this; }
        public ButtonSpec checked(boolean c) { this.checked = c; return this; }
        public ButtonSpec disabled() { this.disabled = true; return this; }
        public ButtonSpec layout(Consumer<UiLayout.Builder> l) { l.accept(layout); return this; }
        public ButtonSpec width(float w) { layout.width(w); return this; }
        public ButtonSpec height(float h) { layout.height(h); return this; }
        public ButtonSpec size(float s) { layout.size(s); return this; }
        public ButtonSpec size(float w, float h) { layout.width(w); layout.height(h); return this; }
        public ButtonSpec growX() { layout.growX(); return this; }

        VButton toNode(Text text, String clicked) {
            return new VButton(id, layout.build(), text, icon, iconPlaceholder, style, clicked, group, checked, disabled);
        }
    }

    /** Fluent spec for image buttons. */
    public static final class ImageButtonSpec {
        String id;
        String iconPlaceholder;
        String style;
        String group;
        Boolean checked;
        boolean disabled;
        final UiLayout.Builder layout = new UiLayout.Builder();

        public ImageButtonSpec id(String id) { this.id = id; return this; }
        public ImageButtonSpec placeholder(String p) { this.iconPlaceholder = p; return this; }
        public ImageButtonSpec style(String style) { this.style = style; return this; }
        public ImageButtonSpec group(String group) { this.group = group; return this; }
        public ImageButtonSpec checked(boolean c) { this.checked = c; return this; }
        public ImageButtonSpec disabled() { this.disabled = true; return this; }
        public ImageButtonSpec layout(Consumer<UiLayout.Builder> l) { l.accept(layout); return this; }
        public ImageButtonSpec size(float s) { layout.size(s); return this; }

        VImageButton toNode(String icon, String clicked) {
            return new VImageButton(id, layout.build(), icon, iconPlaceholder, style, clicked, group, checked, disabled);
        }
    }

    /** Fluent spec for text fields. */
    public static final class FieldSpec {
        Text value = Text.empty();
        Text hint = Text.empty();
        Integer maxLength;
        String style;
        String enter;
        boolean disabled;
        final UiLayout.Builder layout = new UiLayout.Builder();

        public FieldSpec value(Text value) { this.value = value; return this; }
        public FieldSpec value(String raw) { this.value = Text.raw(raw); return this; }
        public FieldSpec hint(Text hint) { this.hint = hint; return this; }
        public FieldSpec hint(String raw) { this.hint = Text.raw(raw); return this; }
        public FieldSpec maxLength(int len) { this.maxLength = len; return this; }
        public FieldSpec style(String style) { this.style = style; return this; }
        public FieldSpec enter(String result) { this.enter = result; return this; }
        public FieldSpec disabled() { this.disabled = true; return this; }
        public FieldSpec layout(Consumer<UiLayout.Builder> l) { l.accept(layout); return this; }
        public FieldSpec growX() { layout.growX(); return this; }
        public FieldSpec width(float w) { layout.width(w); return this; }

        VField toNode(String id) {
            return new VField(id, layout.build(), value, hint, maxLength, style, enter, disabled);
        }
    }

    /** Fluent spec for checkboxes. */
    public static final class CheckSpec {
        String id;
        Boolean checked;
        String style;
        String group;
        boolean disabled;
        final UiLayout.Builder layout = new UiLayout.Builder();

        public CheckSpec id(String id) { this.id = id; return this; }
        public CheckSpec checked(boolean c) { this.checked = c; return this; }
        public CheckSpec style(String style) { this.style = style; return this; }
        public CheckSpec group(String group) { this.group = group; return this; }
        public CheckSpec disabled() { this.disabled = true; return this; }
        public CheckSpec layout(Consumer<UiLayout.Builder> l) { l.accept(layout); return this; }

        VCheck toNode(Text text) {
            return new VCheck(id, layout.build(), text, checked, style, group, disabled);
        }
    }

    /** Fluent spec for sliders. */
    public static final class SliderSpec {
        String id;
        Float defaultValue;
        String style;
        boolean disabled;
        final UiLayout.Builder layout = new UiLayout.Builder();

        public SliderSpec id(String id) { this.id = id; return this; }
        public SliderSpec defaultValue(float v) { this.defaultValue = v; return this; }
        public SliderSpec style(String style) { this.style = style; return this; }
        public SliderSpec disabled() { this.disabled = true; return this; }
        public SliderSpec layout(Consumer<UiLayout.Builder> l) { l.accept(layout); return this; }
        public SliderSpec growX() { layout.growX(); return this; }

        VSlider toNode(float min, float max, float step) {
            return new VSlider(id, layout.build(), min, max, step, defaultValue, style, disabled);
        }
    }
}
