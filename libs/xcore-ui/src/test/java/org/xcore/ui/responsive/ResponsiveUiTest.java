package org.xcore.ui.responsive;

import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponsiveUiTest {

    private static String dsl(VNode node) {
        return UiDslWriter.write(new VNodeCompiler().compile(node));
    }

    @Test
    @DisplayName("DialogMetrics holds caps, and rejects values that would break layout")
    void dialogMetricsAreCaps() {
        DialogMetrics metrics = DialogMetrics.standard();

        assertThat(metrics.maxDialogWidth()).isEqualTo(760f);
        assertThat(metrics.maxBodyHeight()).isEqualTo(460f);
        assertThat(metrics.textBudget()).isEqualTo(44);
        assertThat(metrics.maxCardWidth()).isLessThan(metrics.maxDialogWidth());

        assertThatThrownBy(() -> DialogMetrics.of(0f, 100f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DialogMetrics.of(100f, 0f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DialogMetrics(100f, 100f, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DialogMetrics.truncate collapses newlines and never splits a surrogate pair")
    void truncateIsSafe() {
        DialogMetrics metrics = DialogMetrics.standard();

        assertThat(metrics.truncate("short")).isEqualTo("short");
        assertThat(metrics.truncate(null)).isEmpty();
        assertThat(metrics.truncate("two\nlines")).isEqualTo("two lines");

        String long_ = "x".repeat(200);
        String cut = metrics.truncate(long_);
        assertThat(cut).hasSize(metrics.textBudget()).endsWith("…");

        // budget lands mid-emoji; the result must still be a valid string
        String emoji = "😀".repeat(40);
        assertThat(metrics.truncate(emoji)).doesNotContain("�");
    }

    @Test
    @DisplayName("DialogMetrics keeps a larger budget for prose than for identifiers")
    void proseGetsItsOwnBudget() {
        DialogMetrics metrics = DialogMetrics.standard();

        // An overflowing nickname is cosmetic; a truncated ban reason loses the context that
        // makes the audit log useful, so the two must not share one number.
        assertThat(DialogMetrics.REASON_BUDGET).isGreaterThan(DialogMetrics.TEXT_BUDGET);

        String reason = "y".repeat(300);
        assertThat(metrics.truncateReason(reason)).hasSize(DialogMetrics.REASON_BUDGET).endsWith("…");
        assertThat(metrics.truncate(reason)).hasSize(DialogMetrics.TEXT_BUDGET).endsWith("…");

        // the reason budget must not be a regression against what desktop users saw before
        assertThat(DialogMetrics.REASON_BUDGET).isGreaterThanOrEqualTo(65);
    }

    @Test
    @DisplayName("DialogScaffold emits caps the client resolves, not fixed widths")
    void dialogScaffoldEmitsCapsNotFixedWidths() {
        VNode root = Ui.scaffold(DialogMetrics.standard(), scaffold -> {
            scaffold.header(h -> h.label(Text.raw("Header Title")));
            scaffold.body(cards -> scaffold.card(cards, "action:1",
                    card -> card.label(Text.raw("Item 1"))));
            scaffold.footer(f -> f.button(Text.raw("Close"), "action:close"));
        });

        String out = dsl(root);

        // the dialog fills and caps; it never pins a width the phone might not have
        assertThat(out).contains("growX: true").contains("maxWidth: 760");
        assertThat(out).doesNotContain("width: 760");

        // the body takes the leftover height but cannot push the footer off screen
        assertThat(out).contains("maxHeight: 460");
    }

    @Test
    @DisplayName("DialogScaffold keeps header, body, and footer actions addressable")
    void dialogScaffoldStructure() {
        VNode root = Ui.scaffold(DialogMetrics.standard(), scaffold -> {
            scaffold.header(h -> h.label(Text.raw("Header Title")));
            scaffold.tabs(t -> t.button(Text.raw("Tab 1"), "tab:1"));
            scaffold.body(cards -> scaffold.card(cards, "action:1",
                    card -> card.label(Text.raw("Item 1"))));
            scaffold.footer(f -> f.button(Text.raw("Close"), "action:close"));
        });

        String out = dsl(root);

        assertThat(out).contains("Header Title");
        assertThat(out).contains("tab:1");
        assertThat(out).contains("action:1");
        assertThat(out).contains("action:close");
        // tabs wrap instead of assuming a fixed column count
        assertThat(out).contains("wrap: true");
    }

    @Test
    @DisplayName("Responsive variants compile to native client-side conditions")
    void responsiveVariantsUseNativeConditions() {
        VNode node = Ui.table(t -> {
            Responsive.portrait(t, v -> v.label(Text.raw("Portrait only")));
            Responsive.landscape(t, v -> v.label(Text.raw("Landscape only")));
            Responsive.atLeast(t, 700f, v -> v.label(Text.raw("Wide only")));
        });

        String out = dsl(node);

        assertThat(out).contains("condition: portrait");
        assertThat(out).contains("condition: landscape");
        assertThat(out).contains("condition: \"width >= 700\"");
    }

    /** Splits the DSL into its top-level brace-delimited blocks. */
    private static java.util.List<String> topLevelBlocks(String dsl) {
        java.util.List<String> blocks = new java.util.ArrayList<>();
        int depth = 0;
        int start = -1;
        for(int i = 0; i < dsl.length(); i++){
            char c = dsl.charAt(i);
            if(c == '{'){
                if(depth == 0) start = i;
                depth++;
            }else if(c == '}'){
                depth--;
                if(depth == 0 && start >= 0){
                    blocks.add(dsl.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return blocks;
    }

    @Test
    @DisplayName("Responsive variants are mutually exclusive blocks, so a client keeps exactly one")
    void responsiveVariantsDoNotBothRender() {
        VNode node = Ui.table(t -> {
            Responsive.portrait(t, v -> v.label(Text.raw("Portrait only")));
            Responsive.landscape(t, v -> v.label(Text.raw("Landscape only")));
        });

        java.util.List<String> blocks = topLevelBlocks(dsl(node));

        // The property that matters is structural, not textual: each variant owns a separate node
        // gated by its own condition, so client-side evaluation keeps exactly one of them.
        assertThat(blocks).hasSize(2);

        String portrait = blocks.get(0);
        String landscape = blocks.get(1);

        assertThat(portrait).contains("condition: portrait").contains("Portrait only");
        assertThat(portrait).doesNotContain("Landscape only");
        assertThat(landscape).contains("condition: landscape").contains("Landscape only");
        assertThat(landscape).doesNotContain("Portrait only");
    }

    @Test
    @DisplayName("UiLayout condition compiles to wire protocol condition attribute")
    void layoutConditionWireFormat() {
        VNode node = Ui.table(t -> {
            t.button(Text.raw("Desktop Only"), "action:d", b -> b.layout(l -> l.whenLandscape()));
            t.button(Text.raw("Mobile Only"), "action:m", b -> b.layout(l -> l.whenPortrait()));
            t.button(Text.raw("Wide Only"), "action:w", b -> b.layout(l -> l.condition("width >= 500")));
        });

        String out = dsl(node);

        assertThat(out).contains("condition: landscape");
        assertThat(out).contains("condition: portrait");
        assertThat(out).contains("condition: \"width >= 500\"");
    }
}