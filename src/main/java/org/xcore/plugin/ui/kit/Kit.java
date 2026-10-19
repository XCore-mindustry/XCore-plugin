package org.xcore.plugin.ui.kit;

import mindustry.gen.Iconc;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VLabel;
import org.xcore.ui.VNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The pieces every menu is put together from: a window, a header, tabs, cards, rows of a list.
 *
 * <p>Each piece takes the width it is to have and gives a width to everything inside it, so a
 * menu built from them leaves nothing to stretch (see {@link Screen}). They also carry the look
 * the menus share: a dark window, a band for a title, a colour per topic.
 */
public final class Kit {

    public static final String HEADER = "2b3040";
    public static final String CARD = "242833";
    /** Darker than a card: a surface inside one, such as a preview. */
    public static final String INSET = "14161c";
    public static final String SUCCESS = "24402b";
    public static final String ERROR = "4a2626";

    public static final float MARGIN = 8f;
    public static final float GAP = 8f;
    public static final float PAD = 10f;
    public static final float BAND_MARGIN = 6f;
    public static final float LINE = 3f;
    public static final float TAB_HEIGHT = 44f;
    public static final float TAB_GAP = 4f;
    /** What a vertical scroll bar takes from a pane, so its content never needs a horizontal one. */
    public static final float SCROLLBAR = 26f;
    public static final float FIELD_HEIGHT = 44f;
    public static final float BUTTON_HEIGHT = 42f;
    /** A button that holds one glyph, wide enough for a finger. */
    public static final float GLYPH_BUTTON = 56f;
    /** What a button keeps free around its text, both sides together. */
    public static final float BUTTON_INSET = 14f;

    private Kit() {
    }

    /** Fills a piece with rows, none wider than {@code inner}. */
    public interface Content {
        void render(Ui.TableBuilder content, float inner);
    }

    /** Fills a row of a list, with {@code inner} of width to share between its cells. */
    public interface RowContent {
        void render(Ui.ButtonTableBuilder row, float inner);
    }

    /**
     * A tab.
     *
     * @param glyph an icon in the colour of {@code accent}, or 0 for none
     */
    public record Tab(char glyph, String label, String action, Accent accent, boolean selected) {
        String text() {
            return (glyph == 0 ? "" : "[#" + accent.color() + "]" + glyph + "[] ")
                    + (selected ? "[white]" : "[lightgray]") + label + "[]";
        }
    }

    /** One choice of several, as a button that stays pressed while it is the chosen one. */
    public record Option(String text, String action, boolean selected) {
    }

    /** Something to do, as a button. One that is not {@code enabled} is shown and cannot be pressed. */
    public record Action(String text, String action, boolean enabled) {
        public Action(String text, String action) {
            this(text, action, true);
        }
    }

    // ------------------------------------------------------------------ window

    /**
     * The window of a menu. The client's dialog adds the button that closes it, so a menu has
     * none of its own.
     */
    public static VNode window(Consumer<Ui.TableBuilder> content) {
        return Ui.table(window -> {
            window.background("pane");
            window.margin(MARGIN);
            content.accept(window);
        });
    }

    /** The band at the top of a window; {@code text} may run to several lines. */
    public static VNode header(float width, String text) {
        return band(width, HEADER, MARGIN, text);
    }

    /** A strip of {@code color} with {@code text} on it. */
    public static VNode band(float width, String color, float margin, String text) {
        return Ui.table(band -> {
            band.background("whiteui");
            band.margin(margin);
            band.layout(l -> l.width(width).color(color));
            band.labelWrap(Text.raw(text), l -> l.width(width - 2f * margin));
        });
    }

    /**
     * Tabs as buttons of one group, so the pressed one stays pressed until the new page arrives.
     * As many go in a row as the widest label allows; the rows are then evened out, and each
     * fills the width.
     */
    public static VNode tabs(float width, String group, List<Tab> tabs) {
        float widest = 0f;
        for (Tab tab : tabs) {
            widest = Math.max(widest, TextWidth.of(tab.text()) + BUTTON_INSET);
        }
        int fits = Math.clamp((int) ((width + TAB_GAP) / (widest + TAB_GAP)), 1, Math.max(1, tabs.size()));
        int rows = (tabs.size() + fits - 1) / fits;
        int perRow = (tabs.size() + rows - 1) / Math.max(1, rows);

        return Ui.table(strip -> {
            strip.layout(l -> l.padTop(GAP));
            for (int from = 0; from < tabs.size(); from += perRow) {
                List<Tab> rowTabs = tabs.subList(from, Math.min(tabs.size(), from + perRow));
                float tabWidth = (width - (rowTabs.size() - 1) * TAB_GAP) / rowTabs.size();
                strip.add(Ui.table(row -> {
                    row.layout(l -> l.padBottom(TAB_GAP));
                    for (int i = 0; i < rowTabs.size(); i++) {
                        Tab tab = rowTabs.get(i);
                        boolean last = i == rowTabs.size() - 1;
                        String text = TextWidth.of(tab.text()) + BUTTON_INSET <= tabWidth
                                ? tab.text()
                                : TextWidth.fit(tab.text(), tabWidth - BUTTON_INSET);
                        row.button(Text.raw(text), tab.action(), b -> b
                                .style("flatTogglet")
                                .group(group)
                                .checked(tab.selected())
                                .layout(l -> l.width(tabWidth).height(TAB_HEIGHT).padRight(last ? 0f : TAB_GAP)));
                    }
                })).row();
            }
        });
    }

    /** The line under the tabs, in the colour of the open one. */
    public static VNode line(float width, Accent accent) {
        return Ui.image("whiteui", l -> l.width(width).height(LINE).padBottom(GAP).color(accent.color()));
    }

    /** What the last action came to: green for done, red for refused. */
    public static VNode feedback(float width, String message, boolean success) {
        return Ui.table(band -> {
            band.background("whiteui");
            band.margin(BAND_MARGIN);
            band.layout(l -> l.width(width).padBottom(GAP).color(success ? SUCCESS : ERROR));
            band.add(centered(message, width - 2f * BAND_MARGIN));
        });
    }

    /**
     * The part of a window that scrolls. Only this can give up height, so on a short screen what
     * is above and below it stays and it scrolls.
     */
    public static void body(Ui.TableBuilder window, Screen screen, Consumer<Ui.TableBuilder> body) {
        window.add(pane(screen, body)).row();
    }

    /** The scrolling part as a node, for a place that is not a row of the window (a slot). */
    public static VNode pane(Screen screen, Consumer<Ui.TableBuilder> body) {
        return Ui.pane(pane -> {
            if (screen.columns() == 1) {
                pane.style("noBarPane");
            }
            pane.layout(l -> l.width(screen.width()));
            pane.table(body);
        });
    }

    // ------------------------------------------------------------------ cards

    /** A card: a band in the accent's colour with the title, and the content under it. */
    public static VNode card(float width, Accent accent, String title, Content content) {
        float inner = width - 2f * PAD;
        return Ui.table(card -> {
            card.background("whiteui");
            card.layout(l -> l.width(width).padBottom(GAP).color(CARD));
            card.add(band(width, accent.band(), BAND_MARGIN, "[#" + accent.color() + "]" + title + "[]")).row();
            card.add(Ui.table(body -> {
                body.layout(l -> l.width(inner).padTop(PAD).padBottom(PAD));
                content.render(body, inner);
            })).row();
        });
    }

    /** A card with no title. */
    public static VNode card(float width, Content content) {
        float inner = width - 2f * PAD;
        return Ui.table(card -> {
            card.background("whiteui");
            card.layout(l -> l.width(width).padBottom(GAP).color(CARD));
            card.add(Ui.table(body -> {
                body.layout(l -> l.width(inner).padTop(PAD).padBottom(PAD));
                content.render(body, inner);
            })).row();
        });
    }

    /** A card that says there is nothing to show. */
    public static VNode note(float width, String text) {
        return card(width, (content, inner) ->
                content.add(centered("[lightgray]" + Iconc.info + " " + text + "[]", inner)).row());
    }

    /** Wrapped text of the given width. */
    public static VNode text(String text, float width) {
        return Ui.labelWrap(Text.raw(text), l -> l.width(width));
    }

    /** Wrapped text centred in {@code width}. */
    public static VNode centered(String text, float width) {
        return new VLabel(null, Ui.layout().width(width).build(), Text.raw(text), true, null, "center");
    }

    /** Wrapped text set against the right edge of {@code width}. */
    public static VNode right(String text, float width) {
        return new VLabel(null, Ui.layout().width(width).build(), Text.raw(text), true, null, "right");
    }

    // ------------------------------------------------------------------ buttons

    /** Choices as buttons of one size, in rows of as many as {@code width} holds at {@code minWidth} each. */
    public static void options(Ui.TableBuilder content, float width, float minWidth, String group,
                               List<Option> options) {
        int perRow = Math.max(1, Math.min(options.size(), (int) ((width + TAB_GAP) / (minWidth + TAB_GAP))));
        float optionWidth = (width - (perRow - 1) * TAB_GAP) / perRow;
        for (int from = 0; from < options.size(); from += perRow) {
            List<Option> rowOptions = options.subList(from, Math.min(options.size(), from + perRow));
            content.add(Ui.table(row -> {
                row.layout(l -> l.expandX().align("left").padBottom(TAB_GAP));
                for (int i = 0; i < rowOptions.size(); i++) {
                    Option option = rowOptions.get(i);
                    boolean last = i == rowOptions.size() - 1;
                    row.button(Text.raw((option.selected() ? "[accent]" : "[lightgray]") + option.text() + "[]"),
                            option.action(), b -> b
                                    .style("flatTogglet")
                                    .group(group)
                                    .checked(option.selected())
                                    .layout(l -> l.width(optionWidth).height(BUTTON_HEIGHT)
                                            .padRight(last ? 0f : TAB_GAP)));
                }
            })).row();
        }
    }

    /** Choices as buttons of one size, as many in a row as the longest of their texts allows. */
    public static void options(Ui.TableBuilder content, float width, String group, List<Option> options) {
        float widest = 0f;
        for (Option option : options) {
            widest = Math.max(widest, TextWidth.of(option.text()) + BUTTON_INSET);
        }
        options(content, width, Math.min(widest, width), group, options);
    }

    /**
     * Actions as buttons of one size, side by side where their texts allow it and one under the
     * other where they do not.
     */
    public static VNode actions(float width, List<Action> actions) {
        float widest = 0f;
        for (Action action : actions) {
            widest = Math.max(widest, TextWidth.of(action.text()) + BUTTON_INSET);
        }
        int fits = Math.clamp((int) ((width + TAB_GAP) / (widest + TAB_GAP)), 1, Math.max(1, actions.size()));
        int rows = (actions.size() + fits - 1) / fits;
        int perRow = (actions.size() + rows - 1) / Math.max(1, rows);

        return Ui.table(block -> {
            for (int from = 0; from < actions.size(); from += perRow) {
                List<Action> rowActions = actions.subList(from, Math.min(actions.size(), from + perRow));
                float buttonWidth = (width - (rowActions.size() - 1) * TAB_GAP) / rowActions.size();
                boolean lastRow = from + perRow >= actions.size();
                block.add(Ui.table(row -> {
                    row.layout(l -> l.padBottom(lastRow ? 0f : TAB_GAP));
                    for (int i = 0; i < rowActions.size(); i++) {
                        boolean last = i == rowActions.size() - 1;
                        row.add(button(rowActions.get(i), buttonWidth, last ? 0f : TAB_GAP));
                    }
                })).row();
            }
        });
    }

    /** One action as a button of the given width. */
    public static VNode button(Action action, float width) {
        return button(action, width, 0f);
    }

    private static VNode button(Action action, float width, float padRight) {
        String text = TextWidth.of(action.text()) + BUTTON_INSET <= width
                ? action.text()
                : TextWidth.fit(action.text(), width - BUTTON_INSET);
        return Ui.button(Text.raw(text), action.action(), b -> {
            if (!action.enabled()) b.disabled();
            b.style("flatBordert").layout(l -> l.width(width).height(BUTTON_HEIGHT).padRight(padRight));
        });
    }

    /**
     * A row of a list that is pressed as a whole: a row is easier to hit with a finger than a
     * button inside it.
     *
     * @param selected draws the row with the border of a chosen one
     * @param enabled  a row that is not is shown and cannot be pressed
     */
    public static VNode row(String action, float width, boolean selected, boolean enabled, RowContent content) {
        return Ui.buttonTable(action, row -> {
            row.style("flatTogglet").checked(selected).margin(MARGIN);
            if (!enabled) row.disabled();
            row.layout(l -> l.width(width).padBottom(TAB_GAP));
            content.render(row, width - 2f * MARGIN);
        });
    }

    /**
     * Turns the pages of a list: back, where the reader is, forth. An action that is
     * {@code null} gives a button that cannot be pressed; a {@code null} {@code refresh} gives
     * no button at all.
     */
    public static VNode pager(float width, String label, String previous, String next, String refresh) {
        float buttons = 2f * (GLYPH_BUTTON + TAB_GAP) + (refresh != null ? GLYPH_BUTTON + TAB_GAP : 0f);
        float text = width - buttons;
        return Ui.table(bar -> {
            bar.layout(l -> l.padTop(GAP));
            bar.add(glyphButton(Iconc.left, previous, TAB_GAP));
            bar.add(centered("[white]" + TextWidth.fit(label, text) + "[]", text));
            if (refresh != null) {
                bar.button(Text.raw("[sky]" + Iconc.refresh + "[]"), refresh, b -> b
                        .style("flatBordert")
                        .layout(l -> l.width(GLYPH_BUTTON).height(BUTTON_HEIGHT).padLeft(TAB_GAP)));
            }
            bar.add(Ui.table(gap -> {
                gap.layout(l -> l.padLeft(TAB_GAP));
                gap.add(glyphButton(Iconc.right, next, 0f));
            }));
        });
    }

    private static VNode glyphButton(char glyph, String action, float padRight) {
        boolean enabled = action != null;
        return Ui.button(Text.raw((enabled ? "[accent]" : "[gray]") + glyph + "[]"),
                enabled ? action : "action:none", b -> {
                    if (!enabled) b.disabled();
                    b.style("flatBordert").layout(l -> l.width(GLYPH_BUTTON).height(BUTTON_HEIGHT).padRight(padRight));
                });
    }

    // ------------------------------------------------------------------ columns

    /** Cards dealt left to right into the columns of the screen. */
    public static VNode columns(Screen screen, List<VNode> cards) {
        List<List<VNode>> columns = new ArrayList<>();
        for (int column = 0; column < screen.columns(); column++) {
            columns.add(new ArrayList<>());
        }
        for (int i = 0; i < cards.size(); i++) {
            columns.get(i % columns.size()).add(cards.get(i));
        }
        return columns(screen.card(), columns);
    }

    /** Two groups of cards: side by side where the screen has two columns, one after the other where it has one. */
    public static VNode columns(Screen screen, List<VNode> left, List<VNode> right) {
        if (screen.columns() > 1) {
            return columns(screen.card(), List.of(left, right));
        }
        List<VNode> all = new ArrayList<>(left);
        all.addAll(right);
        return columns(screen.card(), List.of(all));
    }

    /** Columns of cards, each packed from the top on its own. */
    private static VNode columns(float width, List<List<VNode>> columns) {
        return Ui.table(grid -> {
            for (int column = 0; column < columns.size(); column++) {
                List<VNode> cards = columns.get(column);
                boolean last = column == columns.size() - 1;
                grid.add(Ui.table(stack -> {
                    stack.layout(l -> l.width(width).align("top").padRight(last ? 0f : GAP));
                    for (VNode card : cards) {
                        stack.add(card).row();
                    }
                }));
            }
        });
    }
}
