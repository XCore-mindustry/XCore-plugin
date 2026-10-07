package org.xcore.plugin.ui.menu.help;

import mindustry.Vars;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
import org.xcore.plugin.ui.menu.HelpMenu;
import org.xcore.ui.Ui;
import org.xcore.ui.Text;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.xcore.plugin.ui.kit.Kit.GAP;

/**
 * The command browser ({@code /help}): a tab per kind of command, the commands of a page as rows
 * that open a command's own page, laid out once per {@link Screen}. Turning a page patches the
 * list and the pager and leaves the rest of the window.
 */
public class HelpUiController implements UiController<HelpUiModel, HelpUiEvent> {

    public static final SlotKey<Object> SLOT_COMMANDS = SlotKey.of("slot_help_commands");
    public static final SlotKey<Object> SLOT_PAGER = SlotKey.of("slot_help_pager");
    public static final SlotKey<Object> SLOT_SEARCH = SlotKey.of("slot_help_search");

    private static final float STRIPE = 4f;

    private final Session session;
    private final HelpMenu helpMenu;

    public HelpUiController(Session session, HelpMenu helpMenu) {
        this.session = session;
        this.helpMenu = helpMenu;
    }

    @Override
    public HelpUiModel initialModel(Object context) {
        XCoreSender sender = helpMenu != null ? helpMenu.resolveSender(session) : null;
        List<HelpCommandItem> commands = (helpMenu != null && sender != null)
                ? helpMenu.buildHelpCommandItems(session, sender)
                : List.of();
        return new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, commands, null);
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
            case HelpUiEvent.OpenPage e -> {
                HelpUiModel updated = model.withPage(e.page());
                yield UpdateResult.patch(updated, Screen.slots(SLOT_COMMANDS, SLOT_PAGER));
            }
            case HelpUiEvent.Search e -> UpdateResult.patch(model.withSearch(e.query()),
                    Screen.slots(SLOT_SEARCH, SLOT_COMMANDS, SLOT_PAGER));
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
        if ("action:search".equals(action)) {
            return new HelpUiEvent.Search(result.getString("field_help_search", ""));
        }
        if ("action:clear_search".equals(action)) {
            return new HelpUiEvent.Search("");
        }
        if (action.startsWith("action:tab:")) {
            String catName = action.substring("action:tab:".length()).toUpperCase(Locale.ROOT);
            try {
                return new HelpUiEvent.SelectCategory(HelpCategory.valueOf(catName));
            } catch (IllegalArgumentException ignored) {
                return new HelpUiEvent.SelectCategory(HelpCategory.ALL);
            }
        }
        if (action.startsWith("action:page:")) {
            try {
                return new HelpUiEvent.OpenPage(Integer.parseInt(action.substring("action:page:".length())));
            } catch (NumberFormatException ignored) {
                return new HelpUiEvent.OpenPage(0);
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
        return Screen.each(screen -> window(model, screen));
    }

    /** The window as one {@link Screen} sees it: the list, or the command picked from it. */
    VNode window(HelpUiModel model, Screen screen) {
        Optional<HelpCommandItem> selected = model.mode() == HelpUiModel.ViewMode.DETAILS
                ? model.selectedCommand() : Optional.empty();
        return selected.isPresent() ? details(selected.get(), screen) : list(model, screen);
    }

    // ------------------------------------------------------------------ list

    private VNode list(HelpUiModel model, Screen screen) {
        float width = screen.width();

        return Kit.window(window -> {
            window.add(Kit.header(width, "[accent]" + Iconc.bookOpen + "[] [white]" + t("help-ui-title") + "[]\n"
                    + t("help-ui-summary", Map.of("count", model.allCommands().size())))).row();

            List<Kit.Tab> tabs = new ArrayList<>();
            for (HelpCategory category : HelpCategory.values()) {
                if (category == HelpCategory.ADMIN && model.countForCategory(HelpCategory.ADMIN) == 0) {
                    continue;
                }
                tabs.add(new Kit.Tab(category.icon(),
                        t(category.bundleKey()) + " [gray]" + model.countForCategory(category) + "[]",
                        "action:tab:" + category.name().toLowerCase(Locale.ROOT),
                        Accent.of(category.colorHex()), model.selectedCategory() == category));
            }
            window.add(Kit.tabs(width, "help_tabs", tabs)).row();
            window.add(Kit.line(width, Accent.of(model.selectedCategory().colorHex()))).row();
            window.slot(screen.slot(SLOT_SEARCH).path(), slot -> slot.add(search(model, width))).row();

            // A turned page changes these two and leaves the rest of the window as it is.
            window.slot(screen.slot(SLOT_COMMANDS).path(), slot ->
                    slot.add(Kit.pane(screen, commands -> commands(commands, model, screen)))).row();
            window.slot(screen.slot(SLOT_PAGER).path(), slot -> {
                if (model.pages() > 1) {
                    int page = model.page();
                    slot.add(Kit.pager(width, (page + 1) + " / " + model.pages(),
                            page > 0 ? "action:page:" + (page - 1) : null,
                            page < model.pages() - 1 ? "action:page:" + (page + 1) : null, null));
                }
            }).row();
        });
    }

    private VNode search(HelpUiModel model, float width) {
        float button = Kit.FIELD_HEIGHT;
        float field = width - 2f * (button + Kit.TAB_GAP);
        return Ui.table(bar -> {
            bar.layout(l -> l.padTop(GAP).padBottom(GAP));
            bar.field("field_help_search", f -> f.value(model.searchQuery())
                    .hint(arc.util.Strings.stripColors(t("help-ui-search-hint")))
                    .enter("action:search")
                    .layout(l -> l.width(field).height(Kit.FIELD_HEIGHT).padRight(Kit.TAB_GAP)));
            bar.button(Text.raw("[accent]" + Iconc.zoom + "[]"), "action:search", b -> b.style("flatBordert")
                    .layout(l -> l.width(button).height(Kit.FIELD_HEIGHT).padRight(Kit.TAB_GAP)));
            bar.button(Text.raw("[gray]" + Iconc.cancel + "[]"), "action:clear_search", b -> b.style("flatBordert")
                    .layout(l -> l.width(button).height(Kit.FIELD_HEIGHT)));
        });
    }

    private void commands(Ui.TableBuilder list, HelpUiModel model, Screen screen) {
        List<HelpCommandItem> commands = model.pageCommands();
        if (commands.isEmpty()) {
            list.add(Kit.note(screen.cards(), t(model.searchQuery().isBlank()
                    ? "help-ui-empty-category" : "help-ui-search-empty"))).row();
            return;
        }
        for (HelpCommandItem command : commands) {
            list.add(commandRow(command, screen.cards())).row();
        }
    }

    /** A command as a row that opens it: a stripe in its category's colour, how it is written, what it does. */
    private VNode commandRow(HelpCommandItem command, float width) {
        return Kit.row("action:cmd:" + command.name(), width, false, true, (row, inner) -> {
            float text = inner - STRIPE - GAP;
            row.add(Ui.image("whiteui", l -> l.width(STRIPE).growY().padRight(GAP)
                    .color(command.category().colorHex())));
            row.add(Kit.text(rowText(command, text, session != null ? session.locale() : null), text));
        });
    }

    /**
     * The two lines of a command's row, each cut to {@code width}: the command as it is typed,
     * and its description without the note that it is for admins, which the mark in front says.
     */
    static String rowText(HelpCommandItem command, float width, Localization local) {
        String syntax = command.primarySyntax() == null ? command.name() : command.primarySyntax();
        int space = syntax.indexOf(' ');
        String arguments = space < 0 ? "" : " [gray]" + TextWidth.escape(syntax.substring(space + 1)) + "[]";
        String overloads = command.syntaxes().size() > 1
                ? "  [darkgray]" + (local != null
                ? local.t("help-ui-overloads", Map.of("count", command.syntaxes().size()))
                : "(" + command.syntaxes().size() + ")") + "[]"
                : "";
        String first = (command.isAdminOnly() ? "[scarlet]" + Iconc.admin + "[] " : "")
                + "[accent]/" + command.name() + "[]" + arguments + overloads;

        String description = cleanAdminNote(command.rawDescription());
        if (description.isBlank()) {
            return TextWidth.fit(first, width);
        }
        return TextWidth.fit(first, width) + "\n" + TextWidth.fit("[lightgray]" + description + "[]", width);
    }

    // ------------------------------------------------------------------ details

    private VNode details(HelpCommandItem command, Screen screen) {
        float width = screen.width();
        HelpCategory category = command.category();
        Accent accent = Accent.of(category.colorHex());

        return Kit.window(window -> {
            window.add(Kit.header(width, "[accent]" + Iconc.bookOpen + "[] [white]" + t("help-ui-title") + "[]\n"
                    + (command.isAdminOnly() ? "[scarlet]" + Iconc.admin + "[] " : "")
                    + "[accent]/" + command.name() + "[]  [#" + category.colorHex() + "]" + category.icon() + " "
                    + t(category.bundleKey()) + "[]")).row();
            window.add(Kit.line(width, accent)).row();

            Kit.body(window, screen, body -> {
                List<VNode> left = new ArrayList<>();
                left.add(Kit.card(screen.card(), accent, Iconc.info + " /" + command.name(), (content, inner) -> {
                    String description = cleanAdminNote(command.rawDescription());
                    content.add(Kit.text("[white]" + (description.isBlank() ? "-" : description) + "[]", inner)).row();
                    if (!command.aliases().isEmpty()) {
                        String aliases = command.aliases().stream()
                                .map(alias -> "[white]/" + alias + "[]")
                                .collect(Collectors.joining("[gray],[] "));
                        content.add(Ui.table(line -> {
                            line.layout(l -> l.padTop(GAP));
                            line.add(Kit.text(t("help-ui-aliases", Map.of("aliases", aliases)), inner));
                        })).row();
                    }
                }));
                left.add(Kit.card(screen.card(), accent, Iconc.edit + " " + t("help-ui-syntax-title"), (content, inner) ->
                        content.add(Kit.text(command.syntaxes().stream()
                                .map(syntax -> "[accent]/" + TextWidth.escape(syntax) + "[]")
                                .collect(Collectors.joining("\n")), inner)).row()));

                List<VNode> right = new ArrayList<>();
                if (!command.arguments().isEmpty()) {
                    right.add(Kit.card(screen.card(), accent, Iconc.list + " " + t("help-ui-args-title"), (content, inner) -> {
                        for (int i = 0; i < command.arguments().size(); i++) {
                            HelpCommandItem.ArgumentInfo argument = command.arguments().get(i);
                            boolean first = i == 0;
                            content.add(Ui.table(line -> {
                                line.layout(l -> l.padTop(first ? 0f : GAP));
                                line.add(Kit.text(argumentText(argument), inner));
                            })).row();
                        }
                    }));
                }
                body.add(Kit.columns(screen, left, right)).row();
            });

            List<Kit.Action> actions = new ArrayList<>();
            actions.add(new Kit.Action("[accent]" + Iconc.left + "[] " + t("help-ui-btn-back"), "action:back"));
            if (command.hasNoRequiredArgs()) {
                actions.add(new Kit.Action("[green]" + Iconc.play + "[] " + t("help-ui-btn-run"),
                        "action:run:" + command.name()));
            }
            actions.add(new Kit.Action("[sky]" + Iconc.copy + "[] " + t("help-ui-btn-copy"),
                    "action:copy:" + command.primarySyntax()));
            window.add(Ui.table(bar -> {
                bar.layout(l -> l.padTop(GAP));
                bar.add(Kit.actions(width, actions));
            })).row();
        });
    }

    /** An argument as it is typed, whether it may be left out, and under that what it is for. */
    private String argumentText(HelpCommandItem.ArgumentInfo argument) {
        String name = argument.required()
                ? "[scarlet]<" + TextWidth.escape(argument.name()) + ">[]"
                : "[sky][[" + TextWidth.escape(argument.name()) + "][]";
        String kind = t(argument.required() ? "help-ui-arg-required" : "help-ui-arg-optional");
        String description = argument.description() == null || argument.description().isBlank()
                ? "" : "\n[white]" + argument.description() + "[]";
        return name + "  [gray]—[] " + kind + "[]" + description;
    }

    private String t(String key) {
        return session != null ? session.locale().t(key) : key;
    }

    private String t(String key, Map<String, Object> args) {
        return session != null ? session.locale().t(key, args) : key;
    }

    private static final java.util.regex.Pattern ADMIN_ONLY_PATTERN = java.util.regex.Pattern.compile(
            "(?i)\\s*(\\[[^\\]]+\\])?\\s*[(«\\[]?(тільки для адміністраторів|тільки для адмінів|только для админов|только для администраторов|admin only|only for admins)[)»\\]]?[.!?]?(\\s*\\[\\])?",
            java.util.regex.Pattern.UNICODE_CASE | java.util.regex.Pattern.CASE_INSENSITIVE
    );

    public static String cleanAdminNote(String text) {
        if (text == null || text.isBlank()) return "";
        return ADMIN_ONLY_PATTERN.matcher(text).replaceAll("").trim();
    }
}
