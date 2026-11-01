package org.xcore.ui;

import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** What the compiled tree has to look like for the v160 client to build what the tree says. */
class CompilerContractTest {

    private static String dsl(VNode root) {
        return UiDslWriter.write(new VNodeCompiler(LocalizerResolver.IDENTITY).compile(root));
    }

    @Test
    void plainRootIsTheDialogBodyItself() {
        String dsl = dsl(Ui.table(t -> {
            t.background("pane");
            t.label(Text.raw("x"));
        }));

        assertThat(dsl).doesNotContain("table{");
        assertThat(dsl).contains("background: pane");
    }

    @Test
    void rootWithCellLayoutIsNestedSoTheClientKeepsIt() {
        String dsl = dsl(Ui.table(t -> {
            t.background("pane");
            t.layout(l -> l.growX().maxWidth(500f).condition("width >= 800"));
            t.label(Text.raw("x"));
        }));

        // The client applies only background/margin/align to the body; the rest needs a cell.
        assertThat(dsl).contains("table{");
        assertThat(dsl.substring(dsl.indexOf("table{"))).contains("maxWidth: 500", "growX: true", "background: pane");
    }

    @Test
    void slotWithRowsAppliesItsLayoutOnce() {
        VNode root = Ui.table(t -> t.slot("s", body -> {
            body.layout(l -> l.padTop(10f));
            body.label(Text.raw("a")).row();
            body.label(Text.raw("b"));
        }));

        String dsl = dsl(root);
        assertThat(dsl.split("padTop: 10", -1)).hasSize(2);
        VSlot slot = VNodes.findSlot(root, "s");
        assertThat(slot.layout().padTop()).isEqualTo(10f);
        assertThat(((VTable) slot.children().getFirst()).layout().padTop()).isNull();
    }

    @Test
    void layoutDisabledReachesLeavesThatCanBeDisabled() {
        String dsl = dsl(Ui.table(t -> {
            t.button(Text.raw("go"), "go", b -> b.layout(l -> l.disabled()));
            t.field("f", f -> f.layout(l -> l.disabled()));
        }));

        assertThat(dsl.split("disabled: true", -1)).hasSize(3);
    }

    @Test
    void enabledLeavesCarryNoDisabledFlag() {
        String dsl = dsl(Ui.table(t -> {
            t.button(Text.raw("go"), "go");
            t.field("f", f -> {
            });
            t.check(Text.raw("c"), c -> {
            });
            t.slider(0f, 1f, 0.1f, s -> {
            });
        }));

        assertThat(dsl).doesNotContain("disabled");
    }

    @Test
    void componentIsExpandedWithItsProps() {
        org.xcore.ui.component.Component<String> greeting = name -> List.of(Ui.label(Text.raw("Hi " + name)));

        assertThat(dsl(Ui.table(t -> t.component(greeting, "Ann")))).contains("Hi Ann");
    }
}
