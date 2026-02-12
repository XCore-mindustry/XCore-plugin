package org.xcore.plugin.command.controller.client;

import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.ui.Menus;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.help.result.CommandEntry;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.cloud.mindustry.MindustryCloudCommand;
import org.xcore.plugin.ui.MenuBuilder;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MenuSession;

import java.util.*;
import java.util.function.Consumer;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class HelpController implements CloudClientController {

    private static final int MAX_DESCRIPTION_LENGTH = 40;

    private final CloudService cloud;
    private final GlobalConfig globalConfig;

    private HelpHandler<XCoreSender> helpHandler;

    private final MenuService menuService;

    @Inject
    public HelpController(CloudService cloud, GlobalConfig globalConfig, MenuService menuService) {
        this.cloud = cloud;
        this.globalConfig = globalConfig;
        this.menuService = menuService;
    }

    @PostConstruct
    public void init() {
        this.helpHandler = cloud.getHelpHandler();
    }

    @Command("help [page]")
    public void help(XCoreSender sender, @Argument("page") @Default("1") int page) {
        showIndex(sender, page);
    }

    private void showIndex(XCoreSender sender, int page) {
        List<UnifiedCommand> allCommands = collectAllCommands(sender);

        allCommands.sort(Comparator.comparing(UnifiedCommand::name));

        if (allCommands.isEmpty()) {
            sender.send("empty");
            return;
        }

        var pagination = CustomGatherers.calculatePagination(allCommands.size(), globalConfig.commandsPerPage);
        int currentPage = pagination.clampPage(page);

        if (!pagination.isValidPage(page)) {
            sender.send("error-page-between", args("totalPages", pagination.totalPages()));
        }

        List<UnifiedCommand> pageCommands = getPageSlice(allCommands, currentPage, globalConfig.commandsPerPage);

        MenuSession session = menuService.get(sender.player().uuid());
        session.actions.clear();
        session.resetSender();
        session.sender = sender;

        MenuBuilder menu = session.builder()
                .title("help-menu-title")
                .content("help-menu-content", args("page", currentPage, "total", pagination.totalPages()));

        addPaginationButtons(menu, currentPage, pagination.totalPages(),
                prevPage -> showIndex(sender, prevPage),
                nextPage -> showIndex(sender, nextPage));

        for (UnifiedCommand cmd : pageCommands) {
            String desc = resolveDescription(sender, cmd);
            String buttonText = sender.format("help-menu-button", args(
                    "command", cmd.name(),
                    "description", truncate(desc, MAX_DESCRIPTION_LENGTH)
            ));

            menu.addRow(buttonText, () -> {
                session.pushHistory(() -> showIndex(sender, page));
                showCommandDetails(session, cmd, currentPage);
            });
        }

        menu.addNavigationRow();
        menu.show(menuService.getMenuId());
    }

    private void showCommandDetails(MenuSession session, UnifiedCommand cmd, int returnPage) {
        session.actions.clear();
        XCoreSender sender = session.sender;
        String content = cmd.isCloudCommand()
                ? buildCloudCommandContent(sender, cmd)
                : buildLegacyCommandContent(sender, cmd);
        String title = sender.format("help-command-title", args("name", cmd.name()));
        session.builder()
                .title(title, true)
                .rawContent(content)
                .addRow(sender.format("help-back"), () -> showIndex(sender, returnPage)).addNavigationRow()
                .show(menuService.getMenuId());
    }

    private String buildCloudCommandContent(XCoreSender sender, UnifiedCommand cmd) {
        String desc = resolveDescription(sender, cmd);
        StringBuilder content = new StringBuilder();
        content.append(sender.format("help-command-header", args(
                "syntax", cmd.syntax(),
                "description", desc
        )));

        if (cmd.cloudEntry() != null) {
            var aliases = cmd.cloudEntry().command().rootComponent().alternativeAliases();
            if (!aliases.isEmpty()) {
                content.append("\n")
                        .append(sender.format("help-aliases", args(
                                "aliases", formatAliases(aliases)
                        )));
            }
        }

        content.append("\n\n").append(sender.format("help-args-title"));
        List<String> argLines = extractArgumentLines(sender, cmd);
        if (argLines.isEmpty()) {
            content.append("\n").append(sender.format("help-no-arguments"));
        } else {
            argLines.forEach(line -> content.append("\n").append(line));
        }
        return content.toString();
    }

    private String formatAliases(Collection<String> aliases) {
        return aliases.stream()
                .map(a -> "[white]/" + a + "[]")
                .collect(java.util.stream.Collectors.joining("[gray], []"));
    }
    private List<String> extractArgumentLines(XCoreSender sender, UnifiedCommand cmd) {
        List<String> lines = new ArrayList<>();
        var command = cmd.cloudEntry().command();
        for (var component : command.components()) {
            if (component.type() == CommandComponent.ComponentType.LITERAL ||
                    component.type() == CommandComponent.ComponentType.FLAG) {
                continue;
            }
            String argName = component.name();

            String argDisplay = component.required() ? "[white]<" + argName + ">[]" : "[white][" + argName + "][]";
            String argDesc = resolveArgumentDescription(sender, cmd.name(), argName);
            lines.add(sender.format("help-arg-entry", args("arg", argDisplay, "description", argDesc)));
        }
        return lines;
    }

    private String buildLegacyCommandContent(XCoreSender sender, UnifiedCommand cmd) {
        return sender.format("help-legacy-command-content", args(
                "name", cmd.name(),
                "params", cmd.params(),
                "description", resolveDescription(sender, cmd)
        ));
    }

    private List<UnifiedCommand> collectAllCommands(XCoreSender sender) {
        Map<String, UnifiedCommand> commandMap = new LinkedHashMap<>();
        var handler = Vars.netServer.clientCommands;

        Map<String, CommandEntry<XCoreSender>> allowedCloudCommands = new HashMap<>();
        helpHandler.queryRootIndex(sender).entries().forEach(e -> {
            var root = e.command().rootComponent();
            allowedCloudCommands.put(root.name().toLowerCase(), e);
            for (String alias : root.aliases()) {
                allowedCloudCommands.put(alias.toLowerCase(), e);
            }
        });

        for (var cmd : handler.getCommandList()) {
            String nameLower = cmd.text.toLowerCase();
            if (cmd instanceof MindustryCloudCommand<?>) {
                CommandEntry<XCoreSender> entry = allowedCloudCommands.get(nameLower);
                if (entry != null) {
                    if (entry.command().rootComponent().name().equalsIgnoreCase(cmd.text)) {
                        commandMap.put(cmd.text, UnifiedCommand.fromCloud(cmd.text, entry));
                    }
                }
            } else {
                commandMap.putIfAbsent(cmd.text, UnifiedCommand.fromLegacy(cmd));
            }
        }
        return new ArrayList<>(commandMap.values());
    }

    private String resolveDescription(XCoreSender sender, UnifiedCommand cmd) {
        String bundleKey = "commands-" + cmd.name() + "-description";
        String fromBundle = sender.format(bundleKey);

        if (!fromBundle.equals(bundleKey)) {
            return fromBundle;
        }

        if (cmd.rawDescription() != null && !cmd.rawDescription().isEmpty()) {
            return cmd.rawDescription();
        }

        return sender.format("help-no-description");
    }

    private String resolveArgumentDescription(XCoreSender sender, String commandName, String argName) {
        String key = "commands-" + commandName + "-" + argName + "-description";
        String result = sender.format(key);
        return result.equals(key) ? sender.format("help-no-arg-description") : result;
    }

    private void addPaginationButtons(MenuBuilder menu, int currentPage, int totalPages,
                                      Consumer<Integer> onPrev, Consumer<Integer> onNext) {
        List<MenuBuilder.ButtonDef> navButtons = new ArrayList<>();

        if (currentPage > 1) {
            navButtons.add(new MenuBuilder.ButtonDef(
                    menu.sender.format("previous"),
                    () -> onPrev.accept(currentPage - 1)
            ));
        }
        if (currentPage < totalPages) {
            navButtons.add(new MenuBuilder.ButtonDef(
                    menu.sender.format("next"),
                    () -> onNext.accept(currentPage + 1)
            ));
        }

        if (!navButtons.isEmpty()) {
            menu.addRow(navButtons);
        }
    }

    private static <T> List<T> getPageSlice(List<T> list, int page, int pageSize) {
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, list.size());
        return list.subList(start, end);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    private record UnifiedCommand(
            String name,
            String syntax,
            String rawDescription,
            boolean isCloudCommand,
            CommandEntry<XCoreSender> cloudEntry,
            CommandHandler.Command legacyCommand
    ) {
        static UnifiedCommand fromCloud(String name, CommandEntry<XCoreSender> entry) {
            return new UnifiedCommand(
                    name,
                    entry.syntax(),
                    extractCloudDescription(entry),
                    true,
                    entry,
                    null
            );
        }
        static UnifiedCommand fromLegacy(CommandHandler.Command cmd) {
            return new UnifiedCommand(
                    cmd.text,
                    "/" + cmd.text + (cmd.paramText.isEmpty() ? "" : " " + cmd.paramText),
                    cmd.description,
                    false,
                    null,
                    cmd
            );
        }
        private static String extractCloudDescription(CommandEntry<XCoreSender> entry) {
            var desc = entry.command().rootComponent().description();
            if (!desc.isEmpty()) return desc.textDescription();
            var cmdDesc = entry.command().commandDescription().description();
            return !cmdDesc.isEmpty() ? cmdDesc.textDescription() : "";
        }
        String params() {
            return legacyCommand != null ? legacyCommand.paramText : "";
        }
    }
}