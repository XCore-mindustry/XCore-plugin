package org.xcore.plugin.ui.flow;

import arc.util.io.Writes;
import mindustry.gen.Iconc;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a {@link MenuScreen} — a title, a text and rows of buttons — as a window of the
 * {@link Kit}, so the menus that are still written as flows look like the rest and fit a phone.
 *
 * <p>A button sends its place among the buttons of the screen ("0", "1", ...), the same number
 * the client's own menu dialog would send.
 */
public final class MenuScreenToUiAdapter {

    /** A dialog travels as one packet and the client reads a packet into a 32 KB buffer; this leaves a margin. */
    public static final int PACKET_LIMIT = 24_000;

    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";

    private MenuScreenToUiAdapter() {
    }

    /** A button of a screen and the number it answers with. */
    private record Numbered(MenuButton button, int index) {
    }

    public static VNode toVNode(MenuScreen screen) {
        return Screen.compact(layout -> window(screen, layout));
    }

    /** The window of {@code screen} for one class of client screens. */
    static VNode window(MenuScreen screen, Screen layout) {
        float width = layout.width();

        List<List<Numbered>> rows = new ArrayList<>();
        int index = 0;
        for (List<MenuButton> buttons : screen.rows()) {
            List<Numbered> row = new ArrayList<>();
            for (MenuButton button : buttons) {
                // The client's dialog has a button that closes it, and closing ends a flow as its own does.
                if (!ACTION_CLOSE.equals(button.actionId())) {
                    row.add(new Numbered(button, index));
                }
                index++;
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }

        // The way back stays under the part that scrolls, where the other menus have it.
        Numbered back = null;
        if (!rows.isEmpty()) {
            List<Numbered> last = rows.getLast();
            if (last.size() == 1 && ACTION_BACK.equals(last.getFirst().button().actionId())) {
                back = last.getFirst();
                rows.removeLast();
            }
        }

        boolean titled = screen.title() != null && !screen.title().isBlank();
        boolean worded = screen.content() != null && !screen.content().isBlank();
        Numbered pinned = back;

        return Kit.window(window -> {
            if (titled) {
                window.add(Kit.header(width, "[accent]" + screen.title().strip() + "[]")).row();
                window.add(Kit.line(width, Accent.GOLD)).row();
            }
            if (worded || !rows.isEmpty()) {
                Kit.body(window, layout, body -> {
                    if (worded) {
                        body.add(Kit.card(width, (card, inner) -> card.add(content(screen.content(), inner)).row())).row();
                    }
                    for (int i = 0; i < rows.size(); i++) {
                        body.add(buttons(rows.get(i), width, i == rows.size() - 1)).row();
                    }
                });
            }
            if (pinned != null) {
                window.add(Ui.table(bar -> {
                    bar.layout(l -> l.padTop(Kit.GAP));
                    bar.add(Kit.button(new Kit.Action(Iconc.left + " " + pinned.button().text(),
                            String.valueOf(pinned.index())), width));
                })).row();
            }
        });
    }

    /** A line or two reads best in the middle, as a question does; a longer text from the left. */
    private static VNode content(String text, float width) {
        String content = text.strip();
        return content.indexOf('\n') < 0 ? Kit.centered(content, width) : Kit.text(content, width);
    }

    /**
     * A row of the screen: its buttons side by side where their texts allow it, and in rows of
     * fewer where they do not.
     */
    private static VNode buttons(List<Numbered> row, float width, boolean lastRow) {
        float widest = 0f;
        for (Numbered numbered : row) {
            widest = Math.max(widest, TextWidth.of(numbered.button().text()) + 2f * Kit.MARGIN);
        }
        int fits = Math.clamp((int) ((width + Kit.TAB_GAP) / (widest + Kit.TAB_GAP)), 1, row.size());
        int lines = (row.size() + fits - 1) / fits;
        int perLine = (row.size() + lines - 1) / lines;

        return Ui.table(block -> {
            for (int from = 0; from < row.size(); from += perLine) {
                List<Numbered> line = row.subList(from, Math.min(row.size(), from + perLine));
                float buttonWidth = (width - (line.size() - 1) * Kit.TAB_GAP) / line.size();
                boolean last = lastRow && from + perLine >= row.size();
                block.add(Ui.table(cells -> {
                    cells.layout(l -> l.padBottom(last ? 0f : Kit.TAB_GAP));
                    for (int i = 0; i < line.size(); i++) {
                        cells.add(button(line.get(i), buttonWidth, i == line.size() - 1 ? 0f : Kit.TAB_GAP));
                    }
                })).row();
            }
        });
    }

    /** A button whose text wraps: the texts of these screens were written for a dialog that wraps them. */
    private static VNode button(Numbered numbered, float width, float padRight) {
        return Ui.buttonTable(String.valueOf(numbered.index()), button -> {
            button.style("flatBordert").margin(Kit.MARGIN);
            button.layout(l -> l.width(width).minHeight(Kit.BUTTON_HEIGHT).fillY().padRight(padRight));
            button.add(Kit.centered(numbered.button().text().strip(), width - 2f * Kit.MARGIN));
        });
    }

    /** Compiles a {@link MenuScreen} directly into a Mindustry {@link NodeBuilder}. */
    public static NodeBuilder<?> compile(MenuScreen screen, LocalizerResolver resolver) {
        VNodeCompiler compiler = new VNodeCompiler(resolver);
        return compiler.compile(toVNode(screen));
    }

    public static NodeBuilder<?> compile(MenuScreen screen) {
        return compile(screen, LocalizerResolver.IDENTITY);
    }

    /** Bytes {@code ui} takes on the wire. */
    public static int packetSize(NodeBuilder<?> ui) {
        var bytes = new ByteArrayOutputStream();
        try (var writes = new Writes(new DataOutputStream(bytes))) {
            ui.write(writes);
        }
        return bytes.size();
    }
}
