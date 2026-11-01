package org.xcore.ui;

import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeafSpecTest {

    private static String dsl(VNode root) {
        return UiDslWriter.write(new VNodeCompiler(LocalizerResolver.IDENTITY).compile(root));
    }

    @Test
    void labelSpecCarriesAlignAndStyleBesidesItsLayout() {
        VLabel label = Ui.labelWrap(Text.raw("hi"), l -> l.labelAlign("center").style("outline").width(200f));

        assertThat(label.wrap()).isTrue();
        assertThat(label.labelAlign()).isEqualTo("center");
        assertThat(label.style()).isEqualTo("outline");
        assertThat(label.layout().width()).isEqualTo(200f);
        assertThat(dsl(Ui.table(t -> t.add(label)))).contains("labelAlign: center", "wrap: true", "width: 200");
    }

    @Test
    void layoutOnlyLambdasStillWork() {
        VLabel label = Ui.label(Text.raw("hi"), l -> l.growX().padTop(4f));

        assertThat(label.wrap()).isFalse();
        assertThat(label.layout().growX()).isTrue();
    }

    @Test
    void imageSpecCarriesScalingAndPlaceholder() {
        VImage image = Ui.image("net-x", l -> l.scaling(VImage.SCALING_FIT).placeholder("nomap").size(30f));

        assertThat(image.scaling()).isEqualTo("fit");
        assertThat(image.placeholder()).isEqualTo("nomap");
        assertThat(dsl(Ui.table(t -> t.add(image)))).contains("scaling: fit", "placeholder: nomap", "size: 30");
    }

    @Test
    void defaultsApplyToTheCellsAfterThem() {
        String dsl = dsl(Ui.table(t -> {
            t.defaults(l -> l.padRight(4f));
            t.label(Text.raw("a"));
        }));

        assertThat(dsl).containsSubsequence("defaults", "padRight: 4", "label: a");
    }

    @Test
    void slotCanBeDeclaredByItsKey() {
        VNode root = Ui.table(t -> t.slot(org.xcore.ui.runtime.SlotKey.of("k"), s -> s.label(Text.raw("a"))));

        assertThat(VNodes.findSlot(root, "k")).isNotNull();
    }
}
