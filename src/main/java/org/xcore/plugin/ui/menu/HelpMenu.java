package org.xcore.plugin.ui.menu;

import arc.util.CommandHandler;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.help.result.CommandEntry;
import org.xcore.cloud.mindustry.MindustryCloudCommand;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.*;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class HelpMenu extends Menu {

    private final CloudService cloud;
    private HelpHandler<XCoreSender> helpHandler;
    private static final int MAX_DESC_LEN = 40;

    @Inject
    public HelpMenu(Config config, GlobalConfig globalConfig, SessionService sessionService, CloudService cloud) {
        super(config, globalConfig, sessionService);
        this.cloud = cloud;
    }

    @PostConstruct
    public void init() {
        this.helpHandler = cloud.getHelpHandler();
    }

    public void help(String uuid, int page) {
        Session session = sessionService.get(uuid).clear();
        XCoreSender sender = session.sender;

        if (sender == null) {
            session.locale().send("error-internal");
            return;
        }

        List<UnifiedCommand> allCommands = collectAllCommands(sender);
        allCommands.sort(Comparator.comparing(UnifiedCommand::name));

        if (allCommands.isEmpty()) {
            session.locale().send("empty");
            return;
        }

        var pagination = CustomGatherers.calculatePagination(allCommands.size(), globalConfig.commandsPerPage);
        int currentPage = pagination.clampPage(page);
        int skip = (currentPage - 1) * globalConfig.commandsPerPage;
        List<UnifiedCommand> pageSlice = allCommands.subList(skip, Math.min(skip + globalConfig.commandsPerPage, allCommands.size()));

        session.builder()
                .title("help-menu-title")
                .content("help-menu-content", args("page", currentPage, "total", pagination.totalPages()))
                .start()
                    .ifAdd(currentPage > 1, session.locale().t("previous"), () -> help(uuid, currentPage - 1))
                    .ifAdd(currentPage < pagination.totalPages(), session.locale().t("next"), () -> help(uuid, currentPage + 1))
                .end()
                .addForEach(pageSlice, (builder, cmd) -> {
                    String desc = resolveDescription(session, cmd);
                    String btnText = session.locale().t("help-menu-button", args(
                            "command", cmd.name(),
                            "description", truncate(desc, MAX_DESC_LEN)
                    ));
                    builder.addRow(btnText, () -> {
                        session.pushHistory(() -> help(uuid, currentPage));
                        details(uuid, cmd, currentPage, sender);
                    });
                })
                .addNavigationRow()
                .show();
    }

    private void details(String uuid, UnifiedCommand cmd, int returnPage, XCoreSender sender) {
        Session session = sessionService.get(uuid).clear();

        String title = session.locale().t("help-command-title", args("name", cmd.name()));
        String content = cmd.isCloudCommand()
                ? buildCloudContent(session, cmd, sender)
                : buildLegacyContent(session, cmd);

        session.builder()
                .title(title, true)
                .rawContent(content)
                .addRow(session.locale().t("help-back"), () -> help(uuid, returnPage))
                .addNavigationRow()
                .show();
    }

    private String buildCloudContent(Session session, UnifiedCommand cmd, XCoreSender sender) {
        StringBuilder sb = new StringBuilder();
        sb.append(session.locale().t("help-command-header", args("syntax", cmd.syntax(), "description", resolveDescription(session, cmd))));

        if (cmd.cloudEntry() != null) {
            var aliases = cmd.cloudEntry().command().rootComponent().alternativeAliases();
            if (!aliases.isEmpty()) {
                sb.append("\n").append(session.locale().t("help-aliases", args("aliases", formatAliases(aliases))));
            }
        }

        sb.append("\n\n").append(session.locale().t("help-args-title"));
        List<String> args = extractArgs(session, cmd);
        if (args.isEmpty()) sb.append("\n").append(session.locale().t("help-no-arguments"));
        else args.forEach(a -> sb.append("\n").append(a));

        return sb.toString();
    }

    private String buildLegacyContent(Session session, UnifiedCommand cmd) {
        return session.locale().t("help-legacy-command-content", args(
                "name", cmd.name(),
                "params", cmd.params(),
                "description", resolveDescription(session, cmd)
        ));
    }

    private List<UnifiedCommand> collectAllCommands(XCoreSender sender) {
        Map<String, UnifiedCommand> commandMap = new LinkedHashMap<>();
        var handler = Vars.netServer.clientCommands;

        Map<String, CommandEntry<XCoreSender>> allowedCloud = new HashMap<>();
        helpHandler.queryRootIndex(sender).entries().forEach(e -> {
            var root = e.command().rootComponent();
            allowedCloud.put(root.name().toLowerCase(), e);
            for (String alias : root.aliases()) allowedCloud.put(alias.toLowerCase(), e);
        });

        for (var cmd : handler.getCommandList()) {
            String nameLower = cmd.text.toLowerCase();
            if (cmd instanceof MindustryCloudCommand<?>) {
                CommandEntry<XCoreSender> entry = allowedCloud.get(nameLower);
                if (entry != null && entry.command().rootComponent().name().equalsIgnoreCase(cmd.text)) {
                    commandMap.put(cmd.text, UnifiedCommand.fromCloud(cmd.text, entry));
                }
            } else {
                commandMap.putIfAbsent(cmd.text, UnifiedCommand.fromLegacy(cmd));
            }
        }
        return new ArrayList<>(commandMap.values());
    }

    private String resolveDescription(Session session, UnifiedCommand cmd) {
        String key = "commands-" + cmd.name() + "-description";
        String res = session.locale().t(key);
        if (!res.equals(key)) return res;
        return (cmd.rawDescription() != null && !cmd.rawDescription().isEmpty()) ? cmd.rawDescription() : session.locale().t("help-no-description");
    }

    private List<String> extractArgs(Session session, UnifiedCommand cmd) {
        List<String> lines = new ArrayList<>();
        var command = cmd.cloudEntry().command();
        for (var comp : command.components()) {
            if (comp.type() == CommandComponent.ComponentType.LITERAL || comp.type() == CommandComponent.ComponentType.FLAG) continue;
            String display = comp.required() ? "[white]<" + comp.name() + ">[]" : "[white][" + comp.name() + "][]";
            String key = "commands-" + cmd.name() + "-" + comp.name() + "-description";
            String desc = session.locale().t(key);
            if (desc.equals(key)) desc = session.locale().t("help-no-arg-description");
            lines.add(session.locale().t("help-arg-entry", args("arg", display, "description", desc)));
        }
        return lines;
    }

    private String formatAliases(Collection<String> aliases) {
        return aliases.stream().map(a -> "[white]/" + a + "[]").collect(java.util.stream.Collectors.joining("[gray], []"));
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private record UnifiedCommand(String name, String syntax, String rawDescription, boolean isCloudCommand,
                                  CommandEntry<XCoreSender> cloudEntry, CommandHandler.Command legacyCommand) {
        static UnifiedCommand fromCloud(String name, CommandEntry<XCoreSender> entry) {
            return new UnifiedCommand(name, entry.syntax(), extractDesc(entry), true, entry, null);
        }
        static UnifiedCommand fromLegacy(CommandHandler.Command cmd) {
            return new UnifiedCommand(cmd.text, "/" + cmd.text + (cmd.paramText.isEmpty() ? "" : " " + cmd.paramText), cmd.description, false, null, cmd);
        }
        private static String extractDesc(CommandEntry<XCoreSender> entry) {
            var d = entry.command().rootComponent().description();
            if (!d.isEmpty()) return d.textDescription();
            var cd = entry.command().commandDescription().description();
            return !cd.isEmpty() ? cd.textDescription() : "";
        }
        String params() { return legacyCommand != null ? legacyCommand.paramText : ""; }
    }
}