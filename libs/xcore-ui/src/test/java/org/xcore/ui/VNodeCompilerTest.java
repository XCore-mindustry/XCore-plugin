package org.xcore.ui;

import org.junit.jupiter.api.Test;
import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiDslWriter;

import static org.assertj.core.api.Assertions.assertThat;

class VNodeCompilerTest {

    @Test
    void compilesSimpleTableWithButtonAndLabel() {
        VNode root = Ui.table(t -> {
            t.background("pane");
            t.margin(8f);
            t.label(Text.raw("Hello Mindustry v160"), l -> l.growX());
            t.row();
            t.button(Text.raw("Click Me"), "action:click", b -> b.style("green").size(120f, 40f));
        });

        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        UiBuilder.TableBuilder compiled = compiler.compile(root);
        String dsl = UiDslWriter.write(compiled);

        assertThat(dsl).contains("table");
        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("margin: 8");
        assertThat(dsl).contains("label: \"Hello Mindustry v160\"");
        assertThat(dsl).contains("growX: true");
        assertThat(dsl).contains("row");
        assertThat(dsl).contains("button: \"Click Me\"");
        assertThat(dsl).contains("clicked: \"action:click\"");
        assertThat(dsl).contains("style: green");
    }

    @Test
    void resolvesLazyLocalizationAtCompileTime() {
        LocalizerResolver resolver = (key, args) -> switch (key) {
            case "btn.save" -> "Save";
            case "btn.cancel" -> "Cancel";
            default -> key;
        };

        VNode root = Ui.table(t -> {
            t.button(Text.t("btn.save"), "save", null);
            t.button(Text.t("btn.cancel"), "cancel", null);
        });

        VNodeCompiler compiler = new VNodeCompiler(resolver);
        UiBuilder.TableBuilder compiled = compiler.compile(root);
        String dsl = UiDslWriter.write(compiled);

        assertThat(dsl).contains("Save");
        assertThat(dsl).contains("Cancel");
        assertThat(dsl).contains("clicked: save");
        assertThat(dsl).contains("clicked: cancel");
    }

    @Test
    void compilesSlotWithElementIdForMenuBuilderUpdate() {
        VNode root = Ui.table(t -> {
            t.label(Text.raw("Header"));
            t.row();
            t.slot("slot_user_card", slot -> {
                slot.label(Text.raw("Dynamic User Info"));
            });
        });

        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        UiBuilder.TableBuilder compiled = compiler.compile(root);
        String dsl = UiDslWriter.write(compiled);

        // The slot must be emitted with id: slot_user_card so Call.menuBuilderUpdate can target it
        assertThat(dsl).contains("id: slot_user_card");
        assertThat(dsl).contains("label: \"Dynamic User Info\"");
    }

    @Test
    void compilesSlotBodySeparatelyForPatching() {
        VSlot slot = new VSlot("my_slot", UiLayout.DEFAULT, java.util.List.of(
                Ui.label(Text.raw("Patched Content"))
        ));

        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        UiBuilder.TableBuilder compiledSlot = compiler.compileSlotBody(slot);
        String dsl = UiDslWriter.write(compiledSlot);

        assertThat(dsl).contains("label: \"Patched Content\"");
    }
}
