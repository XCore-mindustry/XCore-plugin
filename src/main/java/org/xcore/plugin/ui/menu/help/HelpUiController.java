package org.xcore.plugin.ui.menu.help;

import arc.util.Strings;
import mindustry.Vars;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.menu.HelpMenu;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Modern reactive command browser dialog utilizing xcore-ui and native Mindustry glyphs.
 */
public class HelpUiController implements UiController<HelpUiModel, HelpUiEvent> {

    public record UiMetrics(
            float dialogWidth,
            float contentWidth,
            float cardWidth,
            float cardContentWidth,
            float paneMaxHeight,
            int descMaxLength
    ) {
        public static UiMetrics of(boolean isMobile) {
            if (isMobile) {
                float dw = 680f;
                float pad = 10f;
                float cw = dw - pad * 2f;
                float cardW = cw - 26f;
                float cardInnerW = cardW - 18f;
                return new UiMetrics(dw, cw, cardW, cardInnerW, 440f, 38);
            } else {
                float dw = 740f;
                float pad = 12f;
                float cw = dw - pad * 2f;
                float cardW = cw - 26f;
                float cardInnerW = cardW - 22f;
                return new UiMetrics(dw, cw, cardW, cardInnerW, 480f, 48);
            }
        }
    }

    private final Session session;
    private final HelpMenu helpMenu;

    public HelpUiController(Session session, HelpMenu helpMenu) {
        this.session = session;
        this.helpMenu = helpMenu;
    }

    @Override
    public HelpUiModel initialModel(Object context) {
        boolean isMobile = session != null && session.player != null && session.player.con != null && session.player.con.mobile;
        XCoreSender sender = helpMenu != null ? helpMenu.resolveSender(session) : null;
        List<HelpCommandItem> commands = (helpMenu != null && sender != null)
                ? helpMenu.buildHelpCommandItems(session, sender)
                : List.of();
        return new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, commands, null, isMobile);
    }

    @Override
    public UpdateResult<HelpUiModel> update(HelpUiModel model, HelpUiEvent event, ControllerContext ctx) {
        return switch (event) {
            case HelpUiEvent.SelectCategory e -> {
                HelpUiModel updated = model.withCategory(e.category());
                yield UpdateResult.rerender(updated);
            }
            case HelpUiEvent.SelectCommand e -> {
                HelpUiModel updated = model.withDetails(e.commandName());
                yield UpdateResult.rerender(updated);
            }
            case HelpUiEvent.BackToList e -> {
                HelpUiModel updated = model.withList();
                yield UpdateResult.rerender(updated);
            }
            case HelpUiEvent.ExecuteCommand e -> {
                String cmdName = e.syntax().replaceAll("^/+", "").trim();
                if (session != null && session.player != null) {
                    session.locale().send("help-ui-executed", Map.of("syntax", cmdName));
                    arc.Core.app.post(() -> {
                        if (Vars.netServer != null && Vars.netServer.clientCommands != null
                                && session.player != null && session.player.con != null) {
                            Vars.netServer.clientCommands.handleMessage("/" + cmdName, session.player);
                        }
                    });
                }
                yield UpdateResult.close(model);
            }
            case HelpUiEvent.CopyCommand e -> {
                String cmdSyntax = e.syntax().replaceAll("^/+", "").trim();
                if (session != null) {
                    session.locale().send("help-ui-copied", Map.of("syntax", cmdSyntax));
                }
                yield UpdateResult.close(model);
            }
            case HelpUiEvent.Close e -> {
                yield UpdateResult.close(model);
            }
        };
    }

    @Override
    public HelpUiEvent parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled()) {
            return new HelpUiEvent.Close();
        }
        String action = result.result != null ? result.result.trim() : "";
        if (action.isBlank() || "action:close".equals(action)) {
            return new HelpUiEvent.Close();
        }
        if ("action:back".equals(action)) {
            return new HelpUiEvent.BackToList();
        }
        if (action.startsWith("action:tab:")) {
            String catName = action.substring("action:tab:".length()).toUpperCase();
            try {
                return new HelpUiEvent.SelectCategory(HelpCategory.valueOf(catName));
            } catch (IllegalArgumentException ignored) {
                return new HelpUiEvent.SelectCategory(HelpCategory.ALL);
            }
        }
        if (action.startsWith("action:cmd:")) {
            return new HelpUiEvent.SelectCommand(action.substring("action:cmd:".length()));
        }
        if (action.startsWith("action:run:")) {
            return new HelpUiEvent.ExecuteCommand(action.substring("action:run:".length()));
        }
        if (action.startsWith("action:copy:")) {
            return new HelpUiEvent.CopyCommand(action.substring("action:copy:".length()));
        }
        return new HelpUiEvent.Close();
    }

    @Override
    public VNode render(HelpUiModel model) {
        UiMetrics metrics = UiMetrics.of(model.isMobile());
        if (model.mode() == HelpUiModel.ViewMode.DETAILS) {
            return renderDetails(model, metrics);
        }
        return renderList(model, metrics);
    }

    private VNode renderList(HelpUiModel model, UiMetrics metrics) {
        return Ui.table(root -> {
            root.background("pane");
            root.margin(model.isMobile() ? 8f : 12f);
            root.layout(l -> l.width(metrics.dialogWidth()).pad(4f));

            // 1. Header
            root.add(Ui.table(header -> {
                header.layout(l -> l.width(metrics.contentWidth()).padBottom(4f));
                Text title = Text.join(
                        Text.raw("[accent]" + Iconc.bookOpen + "[] [white]"),
                        Text.t("help-ui-title"),
                        Text.raw("[]")
                );
                Text summary = Text.t("help-ui-summary", Text.args("count", model.allCommands().size()));

                if (model.isMobile()) {
                    header.add(Ui.table(topRow -> {
                        topRow.layout(l -> l.width(metrics.contentWidth()));
                        topRow.label(title, l -> l.align("left").growX());
                        topRow.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close", b -> b
                                .style("cleart")
                                .layout(l -> l.size(32f)));
                    })).row();
                    header.label(summary, l -> l.align("left").growX().padTop(2f));
                } else {
                    header.label(title, l -> l.align("left").growX());
                    header.label(summary, l -> l.align("right").padRight(8f));
                    header.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close", b -> b
                            .style("cleart")
                            .layout(l -> l.size(34f)));
                }
            })).row();

            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(4f).padBottom(6f).color("3b4252")).row();

            // 2. Category Tabs
            root.add(Ui.table(tabs -> {
                tabs.layout(l -> l.width(metrics.contentWidth()).padBottom(6f));
                for (HelpCategory cat : HelpCategory.values()) {
                    if (cat == HelpCategory.ADMIN && model.countForCategory(HelpCategory.ADMIN) == 0) {
                        continue;
                    }
                    boolean checked = model.selectedCategory() == cat;
                    Text tabText = Text.join(
                            Text.raw(checked ? "[accent]" : "[lightgray]"),
                            Text.raw(cat.icon() + " "),
                            Text.t(cat.bundleKey()),
                            Text.raw(" [gray]" + model.countForCategory(cat) + "[]")
                    );
                    tabs.button(tabText, "action:tab:" + cat.name().toLowerCase(), b -> b
                            .style(checked ? "togglet" : "cleart")
                            .checked(checked)
                            .layout(l -> l.height(34f).padTop(2f).padBottom(2f).padLeft(3f).padRight(3f).growX().uniform()));
                }
            })).row();

            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(2f).padBottom(6f).color("3b4252")).row();

            // 3. Command Cards List inside ScrollPane
            List<HelpCommandItem> commands = model.filteredCommands();
            root.pane(pane -> {
                pane.layout(l -> l.width(metrics.contentWidth()).maxHeight(metrics.paneMaxHeight()));
                pane.table(cards -> {
                    cards.layout(l -> l.width(metrics.cardWidth()));

                    if (commands.isEmpty()) {
                        cards.add(Ui.table(empty -> {
                            empty.layout(l -> l.width(metrics.cardWidth()).pad(24f));
                            empty.label(Text.raw("[gray]" + Iconc.info + "[]"), l -> l.align("center").padBottom(6f)).row();
                            empty.label(Text.t("help-ui-empty-category"), l -> l.align("center"));
                        })).row();
                        return;
                    }

                    for (HelpCommandItem cmd : commands) {
                        cards.buttonTable("action:cmd:" + cmd.name(), card -> {
                            card.style("default");
                            card.margin(model.isMobile() ? 6f : 8f);
                            card.layout(l -> l.width(metrics.cardWidth()).maxWidth(metrics.cardWidth()).padBottom(4f));

                            card.table(inner -> {
                                inner.layout(l -> l.width(metrics.cardContentWidth()).maxWidth(metrics.cardContentWidth()));

                                // Left category colored accent stripe
                                inner.image("whiteui", l -> l.width(4f).growY().padRight(8f).color(cmd.category().colorHex()));

                                // Content column
                                inner.add(Ui.table(col -> {
                                    col.layout(l -> l.width(metrics.cardContentWidth() - 16f).maxWidth(metrics.cardContentWidth() - 16f).growX());

                                    // Top row of card
                                    col.add(Ui.table(top -> {
                                        top.layout(l -> l.growX().maxWidth(metrics.cardContentWidth() - 16f));
                                        top.label(Text.raw("[accent]/" + cmd.name() + "[]"), l -> l.align("left"));
                                        if (cmd.syntaxes().size() > 1) {
                                            top.label(Text.join(
                                                    Text.raw("  [darkgray]"),
                                                    Text.t("help-ui-overloads", Text.args("count", cmd.syntaxes().size()))
                                            ), l -> l.align("left"));
                                        }
                                        top.add(Ui.table(spacer -> spacer.layout(l -> l.growX())));
                                        top.label(Text.join(
                                                Text.raw("[#" + cmd.category().colorHex() + "]" + cmd.category().icon() + " "),
                                                Text.t(cmd.category().bundleKey()),
                                                Text.raw("[]")
                                        ), l -> l.align("right"));
                                        if (cmd.isAdminOnly()) {
                                            top.label(Text.raw("  [scarlet]" + Iconc.admin + "[]"), l -> l.align("right"));
                                        }
                                        top.label(Text.raw(" [gray]»[]"), l -> l.align("right"));
                                    })).row();

                                    // Bottom row of card
                                    col.add(Ui.table(bottom -> {
                                        bottom.layout(l -> l.growX().maxWidth(metrics.cardContentWidth() - 16f).padTop(2f));
                                        String bottomLine = formatCardBottomLine(cmd.primarySyntax(), cmd.rawDescription(), metrics.descMaxLength());
                                        bottom.label(Text.raw(bottomLine), l -> l.align("left").growX());
                                    })).row();
                                }));
                            });
                        }).row();
                    }
                });
            }).row();

            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(6f).padBottom(4f).color("3b4252")).row();

            // 4. Footer
            root.add(Ui.table(footer -> {
                footer.layout(l -> l.width(metrics.contentWidth()).padTop(2f));
                Text hint = Text.join(
                        Text.raw("[gray]" + Iconc.info + "[] [lightgray]"),
                        Text.t("help-ui-hint"),
                        Text.raw("[]")
                );
                footer.label(hint, l -> l.align("left").growX());
                footer.button(Text.raw("[accent]" + Iconc.cancel + "[]"), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.size(32f)));
            })).row();
        });
    }

    private VNode renderDetails(HelpUiModel model, UiMetrics metrics) {
        Optional<HelpCommandItem> selectedOpt = model.selectedCommand();
        if (selectedOpt.isEmpty()) {
            return renderList(model.withList(), metrics);
        }
        HelpCommandItem cmd = selectedOpt.get();

        return Ui.table(root -> {
            root.background("pane");
            root.margin(model.isMobile() ? 8f : 12f);
            root.layout(l -> l.width(metrics.dialogWidth()).pad(4f));

            // Header
            root.add(Ui.table(header -> {
                header.layout(l -> l.width(metrics.contentWidth()).padBottom(4f));
                Text title = Text.join(
                        Text.raw("[accent]" + Iconc.bookOpen + "[] [white]"),
                        Text.t("help-ui-title"),
                        Text.raw(" [gray]» [accent]/" + cmd.name() + "[]")
                );
                if (model.isMobile()) {
                    header.add(Ui.table(topRow -> {
                        topRow.layout(l -> l.width(metrics.contentWidth()));
                        topRow.label(title, l -> l.align("left").growX());
                        topRow.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close", b -> b
                                .style("cleart")
                                .layout(l -> l.size(32f)));
                    })).row();
                } else {
                    header.label(title, l -> l.align("left").growX());
                    header.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close", b -> b
                            .style("cleart")
                            .layout(l -> l.size(34f)));
                }
            })).row();

            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(4f).padBottom(6f).color("3b4252")).row();

            // Navigation bar: Back button + Category chip
            root.add(Ui.table(nav -> {
                nav.layout(l -> l.width(metrics.contentWidth()).padBottom(6f));
                nav.button(Text.join(Text.raw("[accent]" + Iconc.left + "[] "), Text.t("help-ui-back")), "action:back", b -> b
                        .style("cleart")
                        .layout(l -> l.height(34f).padRight(8f)));
                nav.add(Ui.table(sp -> sp.layout(l -> l.growX())));
                nav.label(Text.join(
                        Text.raw("[#" + cmd.category().colorHex() + "]" + cmd.category().icon() + " "),
                        Text.t(cmd.category().bundleKey()),
                        Text.raw("[]")
                ), l -> l.align("right"));
            })).row();

            // Scrollable Details Pane
            root.pane(pane -> {
                pane.layout(l -> l.width(metrics.contentWidth()).maxHeight(metrics.paneMaxHeight()));
                pane.table(body -> {
                    body.layout(l -> l.width(metrics.cardWidth()));

                    // 1. Overview Card
                    body.add(Ui.table(hero -> {
                        hero.background("pane");
                        hero.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

                        hero.label(Text.raw("[accent]/" + cmd.name() + "[]"), l -> l.align("left").growX().pad(6f).padBottom(2f)).row();
                        hero.label(Text.raw("[white]" + (cmd.rawDescription().isBlank() ? "-" : cmd.rawDescription()) + "[]"),
                                l -> l.align("left").growX().pad(6f).padTop(0f).padBottom(4f)).row();

                        if (!cmd.aliases().isEmpty()) {
                            String aliasList = cmd.aliases().stream().map(a -> "[white]/" + a + "[]").collect(Collectors.joining("[gray], []"));
                            hero.label(Text.t("help-ui-aliases", Text.args("aliases", aliasList)),
                                    l -> l.align("left").growX().pad(6f).padTop(0f)).row();
                        }
                    })).row();

                    // 2. Syntax variants
                    body.add(Ui.table(syntaxBox -> {
                        syntaxBox.background("pane");
                        syntaxBox.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

                        syntaxBox.label(Text.t("help-ui-syntax-title"), l -> l.align("left").growX().pad(6f).padBottom(2f)).row();
                        for (String s : cmd.syntaxes()) {
                            syntaxBox.add(Ui.table(row -> {
                                row.layout(l -> l.width(metrics.cardContentWidth()).pad(2f));
                                row.label(Text.raw("[gray]• [accent]/" + escapeMarkup(s) + "[]"), l -> l.align("left").growX());
                            })).row();
                        }
                    })).row();

                    // 3. Parameters / Arguments
                    if (!cmd.arguments().isEmpty()) {
                        body.add(Ui.table(argsBox -> {
                            argsBox.background("pane");
                            argsBox.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

                            argsBox.label(Text.t("help-ui-args-title"), l -> l.align("left").growX().pad(6f).padBottom(2f)).row();
                            for (HelpCommandItem.ArgumentInfo arg : cmd.arguments()) {
                                argsBox.add(Ui.table(argRow -> {
                                    argRow.layout(l -> l.width(metrics.cardContentWidth()).pad(3f));
                                    Text badge = arg.required()
                                            ? Text.join(Text.raw("[scarlet]<" + escapeMarkup(arg.name()) + ">[]  [lightgray]("), Text.t("help-ui-arg-required"), Text.raw(")[gray]"))
                                            : Text.join(Text.raw("[sky][[" + escapeMarkup(arg.name()) + "]  [lightgray]("), Text.t("help-ui-arg-optional"), Text.raw(")[gray]"));
                                    argRow.label(badge, l -> l.align("left").growX()).row();
                                    if (arg.description() != null && !arg.description().isBlank()) {
                                        argRow.label(Text.raw("[white]  " + arg.description() + "[]"), l -> l.align("left").growX());
                                    }
                                })).row();
                            }
                        })).row();
                    }
                });
            }).row();

            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(6f).padBottom(6f).color("3b4252")).row();

            // Action Toolbar
            root.add(Ui.table(toolbar -> {
                toolbar.layout(l -> l.width(metrics.contentWidth()));
                toolbar.button(Text.join(Text.raw("[accent]" + Iconc.left + "[] "), Text.t("help-ui-btn-back")), "action:back", b -> b
                        .style("cleart")
                        .layout(l -> l.height(40f).growX().uniform().pad(2f)));
                if (cmd.hasNoRequiredArgs()) {
                    toolbar.button(Text.join(Text.raw("[green]" + Iconc.play + "[] "), Text.t("help-ui-btn-run")), "action:run:" + cmd.name(), b -> b
                            .style("cleart")
                            .layout(l -> l.height(40f).growX().uniform().pad(2f)));
                }
                toolbar.button(Text.join(Text.raw("[sky]" + Iconc.copy + "[] "), Text.t("help-ui-btn-copy")), "action:copy:" + cmd.primarySyntax(), b -> b
                        .style("cleart")
                        .layout(l -> l.height(40f).growX().uniform().pad(2f)));
            })).row();
        });
    }

    public static String escapeMarkup(String text) {
        if (text == null || text.isBlank()) return "";
        return text.replace("[", "[[");
    }

    public static String truncatePlain(String raw, int maxPlainLength) {
        if (raw == null || raw.isBlank()) return "";
        String singleLine = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (Strings.stripColors(singleLine).length() <= maxPlainLength) {
            return singleLine;
        }
        if (maxPlainLength <= 3) {
            return "...";
        }
        StringBuilder sb = new StringBuilder();
        int visibleCount = 0;
        boolean inTag = false;
        int targetVisible = maxPlainLength - 3;
        for (int i = 0; i < singleLine.length(); i++) {
            char c = singleLine.charAt(i);
            if (!inTag && c == '[' && i + 1 < singleLine.length() && singleLine.charAt(i + 1) == '[') {
                if (visibleCount >= targetVisible) {
                    sb.append("...[]");
                    return sb.toString();
                }
                sb.append("[[");
                i++;
                visibleCount++;
                continue;
            }
            if (c == '[') {
                inTag = true;
                sb.append(c);
            } else if (c == ']' && inTag) {
                inTag = false;
                sb.append(c);
            } else if (inTag) {
                sb.append(c);
            } else {
                if (visibleCount >= targetVisible) {
                    sb.append("...[]");
                    return sb.toString();
                }
                sb.append(c);
                visibleCount++;
            }
        }
        sb.append("[]");
        return sb.toString();
    }

    public static String formatCardBottomLine(String syntax, String rawDesc, int maxTotalLength) {
        String cleanSyntax = "/" + escapeMarkup(syntax);
        if (rawDesc == null || rawDesc.isBlank()) {
            return truncatePlain(cleanSyntax, maxTotalLength);
        }
        String cleanDesc = rawDesc.replace('\n', ' ').replace('\r', ' ').trim();

        int syntaxLen = Strings.stripColors(cleanSyntax).length();
        int minDescLen = 14;
        int sepLen = 5; // "  |  "

        if (syntaxLen + sepLen + minDescLen > maxTotalLength) {
            int maxSyntaxLen = Math.max(12, maxTotalLength - sepLen - minDescLen);
            cleanSyntax = truncatePlain(cleanSyntax, maxSyntaxLen);
            syntaxLen = Strings.stripColors(cleanSyntax).length();
        }

        int remainingForDesc = Math.max(8, maxTotalLength - syntaxLen - sepLen);
        String truncatedDesc = truncatePlain(cleanDesc, remainingForDesc);
        return "[gray]" + cleanSyntax + "  [darkgray]|[]  [white]" + truncatedDesc + "[]";
    }

    public static String formatDescription(String raw, int maxPlainLength) {
        return truncatePlain(raw, maxPlainLength);
    }
}
