package org.xcore.ui;

import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xcore.ui.responsive.Variants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Menus whose layout in the real client backs what this library says about it. Written as
 * {@code .msui} by {@code ./gradlew exportUiFixtures} and rendered by mindustry-testkit:
 *
 * <pre>
 * ../mindustry-testkit/render/render --profiles phone,android1080,pc build/ui-render
 * </pre>
 *
 * What each one must show is in its javadoc; a {@code contrast-*} menu is sent the way the
 * library used to send it, to show the difference.
 */
@Tag("ui-render")
class RealClientFixtures {

    private static final String LONG = "A wrapped label asks its table for the width of its whole line, "
            + "so only a width on a cell keeps it on a phone's screen. ".repeat(2);
    private final Path output = Path.of(System.getProperty("xcore.uiRenderOutput", "build/ui-render"));

    private void write(String name, VNode root) throws IOException {
        write(name, new VNodeCompiler(LocalizerResolver.IDENTITY).compile(root));
    }

    private void write(String name, UiBuilder.NodeBuilder<?> body) throws IOException {
        Files.createDirectories(output);
        Files.writeString(output.resolve(name + ".msui"), UiDslWriter.write(body));
    }

    /**
     * A root table with a width: the compiler nests it under the dialog body, so the width holds
     * and the text wraps inside 300 units on every screen. Expected: no findings.
     */
    @Test
    void rootLayoutIsKept() throws IOException {
        write("root-width-kept", Ui.table(root -> {
            root.background("pane");
            root.layout(l -> l.width(300f));
            root.labelWrap(Text.raw(LONG), l -> l.growX());
        }));
    }

    /**
     * The same menu as the compiler used to send it, the width loose on the body: the client
     * builds the root into the dialog's own table and applies only background and margin there.
     * Expected: no findings, as nothing leaves the screen, but the width is gone: in the element
     * tree the label spans the dialog (436 units on phone) instead of 300, and the background
     * covers the whole dialog body.
     */
    @Test
    void contrastRootLayoutOnTheBody() throws IOException {
        write("contrast-root-width-on-body", UiBuilder.table().background("pane").width(300f)
                .add(UiBuilder.label(LONG).wrap().growX()));
    }

    /**
     * Three layouts under nested width conditions: the client builds exactly one, the one for its
     * screen (pc: wide, android1080: narrow, phone: small), and each fits. Expected: no findings,
     * and one {@code variant} label in each element tree.
     */
    @Test
    void variantsBuildOneLayoutPerScreen() throws IOException {
        List<Variants.Variant<Float>> screens = List.of(
                new Variants.Variant<>("wide", 800f, 700f),
                new Variants.Variant<>("narrow", 490f, 450f),
                new Variants.Variant<>("small", 0f, 330f));
        write("variants", Variants.byWidth(screens, variant -> Ui.table(window -> {
            window.background("pane");
            window.layout(l -> l.width(variant.value()));
            window.labelWrap(Text.raw("variant " + variant.id()), l -> l.width(variant.value()));
            window.row();
            window.labelWrap(Text.raw(LONG), l -> l.width(variant.value()));
        })));
    }

    /**
     * A slot with rows and a top pad, inside a card: the pad is applied once, on the slot's cell,
     * and the rows fill it. Expected: no findings; the gap above the slot is 10 units, not 20.
     */
    @Test
    void slotWithRowsPadsOnce() throws IOException {
        write("slot-rows", Ui.table(card -> {
            card.background("pane");
            card.layout(l -> l.width(300f));
            card.label(Text.raw("Above the slot"), l -> l.width(280f));
            card.row();
            card.slot("rows", slot -> {
                slot.layout(l -> l.width(280f).padTop(10f));
                slot.labelWrap(Text.raw("First row"), l -> l.width(280f));
                slot.row();
                slot.labelWrap(Text.raw("Second row"), l -> l.width(280f));
            });
        }));
    }
}
