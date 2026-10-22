package org.xcore.plugin.ui.kit;

import mindustry.gen.Iconc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.ui.VButton;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodes;
import org.xcore.ui.runtime.SlotKey;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KitTest {

    @Test
    @DisplayName("colour tags take no room, an escaped bracket takes one")
    void textWidth_ignoresColourTags() {
        float plain = TextWidth.of("Mini-PvP");
        assertThat(TextWidth.of("[accent]Mini-PvP[]")).isEqualTo(plain);
        assertThat(TextWidth.of("[#ffd37f]Mini[white]-PvP")).isEqualTo(plain);
        assertThat(TextWidth.of("[[Mini-PvP")).isEqualTo(plain + TextWidth.of("["), within(0.01f));
        // Not a colour, so the client draws it as it is.
        assertThat(TextWidth.of("[12/30]")).isGreaterThan(TextWidth.of("12/30"));
    }

    @Test
    @DisplayName("the widths are those of the client's font")
    void textWidth_matchesTheClientFont() {
        // Measured on Mindustry's font.woff at the size labels are drawn with.
        assertThat(TextWidth.of("Время игры")).isCloseTo(105.3f, within(1.5f));
        assertThat(TextWidth.of("HexedCore")).isCloseTo(111.2f, within(1.5f));
        assertThat(TextWidth.of("Администрирование")).isCloseTo(182.1f, within(2f));
    }

    @Test
    @DisplayName("the width of a text of several lines is that of its widest")
    void textWidth_takesTheWidestLine() {
        assertThat(TextWidth.of("ab\nabcdef\nabc")).isEqualTo(TextWidth.of("abcdef"));
    }

    @Test
    @DisplayName("a text that fits is left alone, a longer one is cut with an ellipsis")
    void fit_cutsOnlyWhatDoesNotFit() {
        assertThat(TextWidth.fit("[accent]Short[]", 200f)).isEqualTo("[accent]Short[]");

        String cut = TextWidth.fit("[accent]A very long nickname of a player[]", 120f);
        // The colour open at the cut is closed there, so it does not run into the next line.
        assertThat(cut).startsWith("[accent]A very").endsWith("…[]");
        assertThat(TextWidth.of(cut)).isLessThanOrEqualTo(120f);
        assertThat(TextWidth.fit("[white]Name[] [gold]a tag that is far too long[]", 100f)).endsWith("…[]").doesNotEndWith("[][]");
    }

    @Test
    @DisplayName("a word wider than its place is broken into lines that fit, with its colours left whole")
    void breakWords_breaksOnlyWhatIsTooWide() {
        assertThat(TextWidth.breakWords("[accent]Short words[] stay as they are", 120f))
                .isEqualTo("[accent]Short words[] stay as they are");

        String word = "Ы".repeat(60);
        String broken = TextWidth.breakWords("до [accent]" + word + "[] после", 120f);
        assertThat(broken.replace("\n", "")).isEqualTo("до [accent]" + word + "[] после");
        assertThat(broken.split("\\s+")).allSatisfy(part -> assertThat(TextWidth.of(part)).isLessThanOrEqualTo(120f));
        // A tag is never split, wherever the line happens to end.
        for (float width = 20f; width < 200f; width += 7f) {
            assertThat(TextWidth.breakWords("[#ffd37f]" + word + "[][[" + word + "[white]", width))
                    .contains("[#ffd37f]", "[]", "[[", "[white]");
        }
        assertThat(TextWidth.breakWords(null, 100f)).isEmpty();
    }

    @Test
    @DisplayName("an escaped text keeps its brackets instead of having them read as colours")
    void escape_keepsBrackets() {
        assertThat(TextWidth.escape("votekick <player> [reason]")).isEqualTo("votekick <player> [[reason]");
        assertThat(TextWidth.of(TextWidth.escape("[accent]")))
                .isCloseTo(TextWidth.of("accent") + TextWidth.of("[[") + TextWidth.of("]"), within(0.01f));
        assertThat(TextWidth.escape(null)).isEmpty();
    }

    @Test
    @DisplayName("a sign the font has no glyph for is told apart from one it has")
    void undrawable_findsWhatTheFontLacks() {
        assertThat(TextWidth.undrawable("Готово ● 100%\n— ok")).isEmpty();
        // An arrow outside the font, half of an emoji, and a letter the font has no width for.
        assertThat(TextWidth.undrawable("a → b")).isEqualTo("→");
        assertThat(TextWidth.undrawable("ok \uD83D\uDE00")).hasSize(2);
        assertThat(TextWidth.undrawable("soft\u00ADhyphen")).isEqualTo("\u00AD");
    }

    @Test
    @DisplayName("tabs take as many rows as their labels need, and every row fills the width")
    void tabs_evenOutTheirRows() {
        List<Kit.Tab> tabs = new ArrayList<>();
        for (String label : List.of("Обзор", "Статистика", "Онлайн (12)")) {
            tabs.add(new Kit.Tab(Iconc.star, label, "action:" + label, Accent.GOLD, false));
        }

        assertThat(buttonWidths(Kit.tabs(734f, "tabs", tabs))).hasSize(3).allMatch(w -> w < 250f);
        // Three do not fit a small phone side by side: two rows, the second one button wide.
        List<Float> small = buttonWidths(Kit.tabs(330f, "tabs", tabs));
        assertThat(small).containsExactly(163f, 163f, 330f);
    }

    @Test
    @DisplayName("every label of a tab fits its button")
    void tabs_fitTheirLabels() {
        List<Kit.Tab> tabs = new ArrayList<>();
        tabs.add(new Kit.Tab(Iconc.star, "Очень длинное название категории рейтинга", "a", Accent.GOLD, true));
        tabs.add(new Kit.Tab(Iconc.star, "Вторая", "b", Accent.BLUE, false));
        for (Screen screen : Screen.ALL) {
            LayoutAssert.assertLaidOut(Kit.tabs(screen.width(), "tabs", tabs), screen);
        }
    }

    @Test
    @DisplayName("every class of screens has a slot of its own")
    void screen_slotsDifferByClass() {
        SlotKey<Object> base = SlotKey.of("slot_list");
        assertThat(Screen.slots(base)).extracting(SlotKey::path)
                .containsExactly("slot_list_wide", "slot_list_narrow", "slot_list_small");
    }

    @Test
    @DisplayName("each window fits the narrowest screen of its class")
    void screens_fitTheScreensTheyAreFor() {
        // Window margins on both sides, and what the dialog of the client adds around it.
        float chrome = 2f * Kit.MARGIN + 8f;
        assertThat(Screen.WIDE.width() + chrome).isLessThanOrEqualTo(Screen.WIDE_WIDTH);
        assertThat(Screen.NARROW.width() + chrome).isLessThanOrEqualTo(Screen.NARROW_WIDTH);
        assertThat(Screen.SMALL.width() + chrome).isLessThanOrEqualTo(360f);
    }

    @Test
    @DisplayName("an accent made from a colour has a dark band of it")
    void accent_ofDarkensTheColour() {
        Accent accent = Accent.of("ff5555");
        assertThat(accent.color()).isEqualTo("ff5555");
        assertThat(accent.band()).isEqualTo("4f2626");
        assertThat(Accent.of("not a colour")).isEqualTo(Accent.GRAY);
    }

    private static List<Float> buttonWidths(VNode node) {
        List<Float> widths = new ArrayList<>();
        for (VNode child : VNodes.walk(node)) {
            if (child instanceof VButton button) {
                widths.add(button.layout().width());
            }
        }
        return widths;
    }

    @Test
    @DisplayName("tiles show a value or a picture over a caption, and fit the cards they sit above")
    void tiles_fitTheCards() {
        for (Screen screen : Screen.ALL) {
            VNode strip = org.xcore.ui.Ui.table(body -> Kit.tiles(body, screen, Accent.GOLD, List.of(
                    Kit.Tile.number("61", "Hexes"), Kit.Tile.number("/season", "Season"),
                    Kit.Tile.icon("unit-flare-ui", "Flying units"))));

            for (VNode node : VNodes.walk(strip)) {
                Float width = node.layout() == null ? null : node.layout().width();
                if (width != null) {
                    assertThat(width).as("%s on %s", node, screen).isLessThanOrEqualTo(screen.cards());
                }
            }
            assertThat(LayoutAssert.allText(strip)).contains("61", "Hexes", "/season", "Flying units");
            assertThat(VNodes.walk(strip)).anyMatch(node -> node instanceof org.xcore.ui.VImage image
                    && image.region().equals("unit-flare-ui"));
        }
    }

    @Test
    @DisplayName("icons run in rows no longer than the place and are named under them; none, nothing")
    void icons_areNamed() {
        List<String> regions = List.of("unit-a-ui", "unit-b-ui", "unit-c-ui", "unit-d-ui", "unit-e-ui", "unit-f-ui",
                "unit-g-ui", "unit-h-ui", "unit-i-ui", "unit-j-ui");
        VNode shown = org.xcore.ui.Ui.table(content -> Kit.icons(content, regions, "A, B, C", 200f));
        VNode none = org.xcore.ui.Ui.table(content -> Kit.icons(content, List.of(), "", 200f));

        int perRow = (int) (200f / (Kit.ROW_ICON + 4f));
        long pictures = 0;
        for (VNode node : VNodes.walk(shown)) {
            if (node instanceof org.xcore.ui.VTable table) {
                assertThat(table.children().stream().filter(child -> child instanceof org.xcore.ui.VImage).count())
                        .isLessThanOrEqualTo(perRow);
            }
            if (node instanceof org.xcore.ui.VImage) pictures++;
        }
        assertThat(pictures).isEqualTo(regions.size());
        assertThat(LayoutAssert.allText(shown)).isEqualTo("[lightgray]A, B, C[]");
        assertThat(VNodes.walk(none)).noneMatch(node -> node instanceof org.xcore.ui.VImage
                || node instanceof org.xcore.ui.VLabel);
    }

    @Test
    @DisplayName("a card can carry the picture of what it is about in its band")
    void card_withIcon() {
        VNode card = Kit.card(Screen.NARROW.card(), Accent.BLUE, "block-core-shard-ui", "Capture",
                (content, inner) -> content.add(Kit.text("Take hexes", inner)).row());

        assertThat(VNodes.walk(card)).anyMatch(node -> node instanceof org.xcore.ui.VImage image
                && image.region().equals("block-core-shard-ui"));
        assertThat(LayoutAssert.allText(card)).contains("Capture", "Take hexes");
    }
}
