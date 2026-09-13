package org.xcore.ui;

import arc.util.Scaling;
import mindustry.ui.builder.UiBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Compiles the immutable {@link VNode} AST into Mindustry v160
 * {@code UiBuilder.NodeBuilder} trees ready for {@code Call.menuBuilder(...)}.
 *
 * <p>Text is resolved against the player's {@link LocalizerResolver} at this
 * point (the final stage before serialization) so localization stays lazy
 * yet the wire format is fully concrete.
 */
public final class VNodeCompiler {

    private final LocalizerResolver resolver;

    public VNodeCompiler() {
        this(LocalizerResolver.getDefault());
    }

    public VNodeCompiler(LocalizerResolver resolver) {
        this.resolver = resolver == null ? LocalizerResolver.getDefault() : resolver;
    }

    public LocalizerResolver resolver() {
        return resolver;
    }

    /** Compiles a full dialog body. The result is a table-level builder. */
    public UiBuilder.TableBuilder compile(VNode root) {
        Objects.requireNonNull(root, "root");
        if (root instanceof VTable t) {
            UiBuilder.TableBuilder builder = UiBuilder.table();
            if (t.background() != null) builder.background(t.background());
            if (t.margin() != null) builder.margin(t.margin());
            if (Boolean.TRUE.equals(t.wrap())) builder.wrap(true);
            applyCommon(builder, t);
            compileEntries(builder, t.entries());
            return builder;
        }
        UiBuilder.TableBuilder table = UiBuilder.table();
        compileChildren(table, List.of(root));
        return table;
    }

    /** Compiles a slot body for {@code Call.menuBuilderUpdate}: a table whose entries replace the slot content. */
    public UiBuilder.TableBuilder compileSlotBody(VSlot slot) {
        Objects.requireNonNull(slot, "slot");
        UiBuilder.TableBuilder table = UiBuilder.table();
        compileChildren(table, slot.children());
        return table;
    }

    private void compileChildren(UiBuilder.ContainerBuilder<?> parent, List<VNode> children) {
        for (VNode node : children) {
            compileNode(parent, node);
        }
    }

    private void compileNode(UiBuilder.ContainerBuilder<?> parent, VNode node) {
        switch (node) {
            case VTable t -> compileTable(parent, t);
            case VPane p -> compilePane(parent, p);
            case VStack s -> compileStack(parent, s);
            case VLabel l -> parent.add(compileLabel(l));
            case VImage i -> parent.add(compileImage(i));
            case VButton b -> parent.add(compileButton(b));
            case VImageButton ib -> parent.add(compileImageButton(ib));
            case VField f -> parent.add(compileField(f));
            case VCheck c -> parent.add(compileCheck(c));
            case VSlider sl -> parent.add(compileSlider(sl));
            case VSpace sp -> compileSpace(parent, sp);
            case VSlot slot -> compileSlot(parent, slot);
            case VComponentNode comp -> compileChildren(parent, comp.children());
            case null -> throw new IllegalArgumentException("null node");
        }
    }

    // ------------------------------------------------------------------
    // Containers
    // ------------------------------------------------------------------

    private void compileTable(UiBuilder.ContainerBuilder<?> parent, VTable table) {
        UiBuilder.TableBuilder builder = UiBuilder.table();
        if (table.background() != null) builder.background(table.background());
        if (table.margin() != null) builder.margin(table.margin());
        if (Boolean.TRUE.equals(table.wrap())) builder.wrap(true);
        applyCommon(builder, table);
        compileEntries(builder, table.entries());
        attach(parent, builder);
    }

    private void compileEntries(UiBuilder.TableBuilder builder, List<Object> entries) {
        for (Object entry : entries) {
            if (entry instanceof VTable.RowMarker) {
                builder.row();
            } else if (entry instanceof VNode node) {
                compileNode(builder, node);
            } else if (entry != null) {
                throw new IllegalStateException("Unknown table entry: " + entry);
            }
        }
    }

    private void compilePane(UiBuilder.ContainerBuilder<?> parent, VPane pane) {
        UiBuilder.PaneBuilder builder = UiBuilder.pane();
        if (pane.style() != null) builder.style(pane.style());
        applyCommon(builder, pane);
        compileChildren(builder, pane.children());
        attach(parent, builder);
    }

    private void compileStack(UiBuilder.ContainerBuilder<?> parent, VStack stack) {
        UiBuilder.StackBuilder builder = UiBuilder.stack();
        applyCommon(builder, stack);
        compileChildren(builder, stack.children());
        attach(parent, builder);
    }

    private void compileSlot(UiBuilder.ContainerBuilder<?> parent, VSlot slot) {
        // A slot is a nested table carrying the slot key as its element id.
        UiBuilder.TableBuilder builder = UiBuilder.table();
        builder.id(slot.slotKey());
        applyCommon(builder, slot);
        compileChildren(builder, slot.children());
        attach(parent, builder);
    }

    // ------------------------------------------------------------------
    // Leaves
    // ------------------------------------------------------------------

    private UiBuilder.LabelBuilder compileLabel(VLabel label) {
        UiBuilder.LabelBuilder builder = UiBuilder.label(label.text().resolve(resolver));
        if (label.wrap()) builder.wrap();
        if (label.style() != null) builder.style(label.style());
        if (label.labelAlign() != null) builder.labelAlign(label.labelAlign());
        applyCommon(builder, label);
        return builder;
    }

    private UiBuilder.ImageBuilder compileImage(VImage image) {
        UiBuilder.ImageBuilder builder = UiBuilder.image(image.region());
        if (image.placeholder() != null) builder.placeholder(image.placeholder());
        if (image.scaling() != null) builder.scaling(Scaling.valueOf(image.scaling()));
        applyCommon(builder, image);
        return builder;
    }

    private UiBuilder.ButtonBuilder compileButton(VButton button) {
        String text = button.text() == null ? "" : button.text().resolve(resolver);
        UiBuilder.ButtonBuilder builder = UiBuilder.button(text);
        if (button.icon() != null) {
            builder.icon(button.icon());
            if (button.iconPlaceholder() != null) builder.placeholder(button.iconPlaceholder());
        }
        if (button.style() != null) builder.style(button.style());
        if (button.clicked() != null) builder.clicked(button.clicked());
        if (button.group() != null) builder.group(button.group());
        if (button.checked() != null) builder.checked(button.checked());
        builder.disabled(button.disabled());
        applyCommon(builder, button);
        return builder;
    }

    private UiBuilder.ImageButtonBuilder compileImageButton(VImageButton button) {
        UiBuilder.ImageButtonBuilder builder = UiBuilder.imageButton(button.icon());
        if (button.iconPlaceholder() != null) builder.placeholder(button.iconPlaceholder());
        if (button.style() != null) builder.style(button.style());
        if (button.clicked() != null) builder.clicked(button.clicked());
        if (button.group() != null) builder.group(button.group());
        if (button.checked() != null) builder.checked(button.checked());
        builder.disabled(button.disabled());
        applyCommon(builder, button);
        return builder;
    }

    private UiBuilder.FieldBuilder compileField(VField field) {
        UiBuilder.FieldBuilder builder = UiBuilder.field(field.text().resolve(resolver));
        String hint = field.hint() == null ? "" : field.hint().resolve(resolver);
        if (!hint.isEmpty()) builder.hint(hint);
        if (field.maxLength() != null) builder.maxLength(field.maxLength());
        if (field.style() != null) builder.style(field.style());
        if (field.enter() != null) builder.enter(field.enter());
        builder.disabled(field.disabled());
        applyCommon(builder, field);
        return builder;
    }

    private UiBuilder.CheckBuilder compileCheck(VCheck check) {
        UiBuilder.CheckBuilder builder = UiBuilder.check(check.text().resolve(resolver));
        if (check.checked() != null) builder.checked(check.checked());
        if (check.style() != null) builder.style(check.style());
        if (check.group() != null) builder.group(check.group());
        builder.disabled(check.disabled());
        applyCommon(builder, check);
        return builder;
    }

    private UiBuilder.SliderBuilder compileSlider(VSlider slider) {
        UiBuilder.SliderBuilder builder = UiBuilder.slider(slider.min(), slider.max(), slider.step());
        if (slider.defaultValue() != null) builder.defaultValue(slider.defaultValue());
        if (slider.style() != null) builder.style(slider.style());
        builder.disabled(slider.disabled());
        applyCommon(builder, slider);
        return builder;
    }

    private void compileSpace(UiBuilder.ContainerBuilder<?> parent, VSpace space) {
        UiBuilder.SpaceBuilder builder = UiBuilder.space();
        applyCommon(builder, space);
        attach(parent, builder);
    }

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    private void applyCommon(UiBuilder.NodeBuilder<?> builder, VNode node) {
        if (node.id() != null) builder.id(node.id());
        UiLayout l = node.layout();
        if (l == null || l.isDefault()) return;
        if (l.width() != null) builder.width(l.width());
        if (l.height() != null) builder.height(l.height());
        if (l.size() != null) builder.size(l.size());
        if (l.minWidth() != null) builder.minWidth(l.minWidth());
        if (l.maxWidth() != null) builder.maxWidth(l.maxWidth());
        if (l.minHeight() != null) builder.minHeight(l.minHeight());
        if (l.maxHeight() != null) builder.maxHeight(l.maxHeight());
        if (l.pad() != null) builder.pad(l.pad());
        if (l.padTop() != null) builder.padTop(l.padTop());
        if (l.padLeft() != null) builder.padLeft(l.padLeft());
        if (l.padBottom() != null) builder.padBottom(l.padBottom());
        if (l.padRight() != null) builder.padRight(l.padRight());
        if (Boolean.TRUE.equals(l.grow())) builder.grow();
        if (Boolean.TRUE.equals(l.growX())) builder.growX();
        if (Boolean.TRUE.equals(l.growY())) builder.growY();
        if (Boolean.TRUE.equals(l.fill())) builder.fill();
        if (Boolean.TRUE.equals(l.fillX())) builder.fillX();
        if (Boolean.TRUE.equals(l.fillY())) builder.fillY();
        if (Boolean.TRUE.equals(l.expand())) builder.expand();
        if (Boolean.TRUE.equals(l.expandX())) builder.expandX();
        if (Boolean.TRUE.equals(l.expandY())) builder.expandY();
        if (Boolean.TRUE.equals(l.uniform())) builder.uniform();
        if (Boolean.TRUE.equals(l.uniformX())) builder.uniformX();
        if (Boolean.TRUE.equals(l.uniformY())) builder.uniformY();
        if (l.colspan() != null) builder.colspan(l.colspan());
        if (l.align() != null) builder.align(l.align());
        if (l.color() != null) builder.color(l.color());
        if (Boolean.TRUE.equals(l.disabled()) && !(builder instanceof UiBuilder.ContainerBuilder<?>)) {
            // container nodes cannot be disabled in v160; leaf specs handle this themselves
        }
    }

    private void attach(UiBuilder.ContainerBuilder<?> parent, UiBuilder.NodeBuilder<?> child) {
        parent.add(child);
    }

    /** Debug helper: renders a compiled builder back to DSL text via Mindustry's writer. */
    public String toDsl(UiBuilder.NodeBuilder<?> node) {
        return node.toString();
    }
}
