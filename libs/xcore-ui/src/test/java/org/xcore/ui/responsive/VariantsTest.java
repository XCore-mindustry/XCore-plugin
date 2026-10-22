package org.xcore.ui.responsive;

import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.Test;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.VNodes;
import org.xcore.ui.runtime.SlotKey;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariantsTest {

    static final Variants.Variant<Float> WIDE = new Variants.Variant<>("wide", 800f, 700f);
    static final Variants.Variant<Float> NARROW = new Variants.Variant<>("narrow", 490f, 450f);
    static final Variants.Variant<Float> SMALL = new Variants.Variant<>("small", 0f, 330f);
    static final SlotKey<Object> LIST = SlotKey.of("list");

    private static VNode dialog(List<Variants.Variant<Float>> variants) {
        return Variants.byWidth(variants, v -> Ui.table(window -> {
            window.label(Text.raw(v.id()), l -> l.width(v.value()));
            window.slot(Variants.slot(LIST, v), slot -> slot.label(Text.raw("rows")));
        }));
    }

    @Test
    void nestsOneComparisonPerTableFromTheWidest() {
        String dsl = UiDslWriter.write(new VNodeCompiler(LocalizerResolver.IDENTITY)
                .compile(dialog(List.of(SMALL, WIDE, NARROW))));

        assertThat(dsl).containsSubsequence(
                "condition: \"width >= 800\"", "label: wide",
                "condition: \"width < 800\"",
                "condition: \"width >= 490\"", "label: narrow",
                "condition: \"width < 490\"", "label: small");
        assertThat(dsl.split("condition:", -1)).hasSize(5);
    }

    @Test
    void singleVariantNeedsNoCondition() {
        String dsl = UiDslWriter.write(new VNodeCompiler(LocalizerResolver.IDENTITY).compile(dialog(List.of(NARROW))));

        assertThat(dsl).doesNotContain("condition");
    }

    @Test
    void everyLayoutHasItsOwnSlotAndAPatchNamesThemAll() {
        VNode root = dialog(List.of(WIDE, NARROW, SMALL));

        assertThat(VNodes.slotsByKey(root)).containsOnlyKeys("list_wide", "list_narrow", "list_small");
        assertThat(Variants.slots(List.of(WIDE, NARROW, SMALL), LIST))
                .extracting(SlotKey::path).containsExactly("list_wide", "list_narrow", "list_small");
    }
}
