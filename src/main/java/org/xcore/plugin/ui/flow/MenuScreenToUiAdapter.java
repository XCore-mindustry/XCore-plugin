package org.xcore.plugin.ui.flow;

import mindustry.ui.builder.UiBuilder.NodeBuilder;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;

import java.util.List;

/**
 * Bridges legacy {@link MenuScreen} instances into Mindustry v160 {@link VNode}
 * and {@link NodeBuilder} trees without modifying existing flows.
 */
public final class MenuScreenToUiAdapter {

    private MenuScreenToUiAdapter() {
    }

    /**
     * Converts a {@link MenuScreen} into an immutable {@link VNode} table.
     * Buttons are sequentially indexed as string numbers ("0", "1", ...)
     * matching the legacy registerMenu option indices.
     */
    public static VNode toVNode(MenuScreen screen) {
        return Ui.table(t -> {
            t.layout(l -> l.pad(12f));

            // Optional content text
            if (screen.content() != null && !screen.content().isBlank()) {
                t.pane(p -> {
                    p.label(Text.raw(screen.content()), l -> l.growX());
                });
                t.row();
            }

            // Buttons rows
            int optionIndex = 0;
            for (List<MenuButton> row : screen.rows()) {
                final int rowStart = optionIndex;
                t.add(Ui.table(btnRow -> {
                    btnRow.layout(l -> l.growX());
                    int idx = rowStart;
                    for (MenuButton btn : row) {
                        btnRow.button(Text.raw(btn.text()), String.valueOf(idx), b -> b.layout(l -> l.growX().uniform()));
                        idx++;
                    }
                }));
                t.row();
                optionIndex += row.size();
            }
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
}
