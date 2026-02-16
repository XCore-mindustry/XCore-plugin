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
                            "command", formatCommandLabel(session, cmd),
                            "description", truncate(desc, MAX_DESC_LEN)
                    ));
                    builder.addRow(btnText, () -> {
                        session.pushHistory(() -> help(uuid, currentPage));
                        details(uuid, cmd, currentPage);
                    });
                })
                .addNavigationRow()
                .show();
    }

    private void details(String uuid, UnifiedCommand cmd, int returnPage) {
        Session session = sessionService.get(uuid).clear();

        String title = session.locale().t("help-command-title", args("name", cmd.name()));
        String content = buildCommandContent(session, cmd);

        session.builder()
                .title(title, true)
                .rawContent(content)
                .addRow(session.locale().t("help-back"), () -> help(uuid, returnPage))
                .addNavigationRow()
                .show();
    }

    private String buildCommandContent(Session session, UnifiedCommand cmd) {
        boolean hasCloud = cmd.isCloudCommand();
        boolean hasLegacy = !cmd.legacyVariants().isEmpty();

        if (hasCloud && hasLegacy) {
            return buildCloudContent(session, cmd) + "\n\n" + buildLegacyContent(session, cmd);
        }
        if (hasCloud) return buildCloudContent(session, cmd);
        return buildLegacyContent(session, cmd);
    }

    private String buildCloudContent(Session session, UnifiedCommand cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append(session.locale().t("help-command-header", args(
                "syntax", cmd.primarySyntax(),
                "description", resolveDescription(session, cmd)
        )));

        if (cmd.syntaxes().size() > 1) {
            appendUsages(session, sb, cmd.syntaxes());
        }

        if (cmd.primaryCloudEntry() != null) {
            var aliases = extractVisibleAliases(cmd);
            if (!aliases.isEmpty()) {
                sb.append("\n").append(session.locale().t("help-aliases", args("aliases", formatAliases(aliases))));
            }
        }

        var cloudVariants = cmd.cloudVariants();
        appendCloudArguments(session, sb, cmd.name(), cloudVariants);

        return sb.toString().trim();
    }

    private String buildLegacyContent(Session session, UnifiedCommand cmd) {
        List<CommandVariant> legacyVariants = cmd.legacyVariants();
        if (legacyVariants.size() != 1) {
            StringBuilder sb = new StringBuilder();
            sb.append(session.locale().t("help-command-header", args(
                    "syntax", cmd.primarySyntax(),
                    "description", resolveDescription(session, cmd)
            )));
            appendUsages(session, sb, legacyVariants.stream().map(CommandVariant::syntax).toList());
            return sb.toString().trim();
        }

        CommandVariant legacy = legacyVariants.get(0);
        String params = legacy.params();
        String key = (params == null || params.isBlank())
                ? "help-legacy-command-content-no-params"
                : "help-legacy-command-content";

        return session.locale().t(key, args(
                "name", cmd.name(),
                "params", params,
                "description", resolveDescription(session, cmd)
        ));
    }

    private List<UnifiedCommand> collectAllCommands(XCoreSender sender) {
        Map<String, UnifiedCommandBuilder> commandMap = new LinkedHashMap<>();
        var handler = Vars.netServer.clientCommands;

        Set<String> cloudNames = new HashSet<>();
        helpHandler.queryRootIndex(sender).entries().forEach(entry -> {
            var root = entry.command().rootComponent();
            String rootName = root.name();
            String rootKey = rootName.toLowerCase(Locale.ROOT);

            cloudNames.add(rootKey);
            for (String alias : root.aliases()) {
                cloudNames.add(alias.toLowerCase(Locale.ROOT));
            }

            commandMap.computeIfAbsent(rootKey, ignored -> new UnifiedCommandBuilder(rootName))
                    .addVariant(CommandVariant.fromCloud(entry));
        });

        for (var cmd : handler.getCommandList()) {
            String nameLower = cmd.text.toLowerCase(Locale.ROOT);
            if (cmd instanceof MindustryCloudCommand<?> || cloudNames.contains(nameLower)) continue;

            commandMap.computeIfAbsent(nameLower, ignored -> new UnifiedCommandBuilder(cmd.text))
                    .addVariant(CommandVariant.fromLegacy(cmd));
        }

        return new ArrayList<>(commandMap.values().stream().map(UnifiedCommandBuilder::build).toList());
    }

    private String resolveDescription(Session session, UnifiedCommand cmd) {
        String key = "commands-" + cmd.name() + "-description";
        String res = session.locale().t(key);
        if (!res.equals(key)) return res;
        return (cmd.rawDescription() != null && !cmd.rawDescription().isEmpty()) ? cmd.rawDescription() : session.locale().t("help-no-description");
    }

    private List<String> extractArgs(Session session, String commandName, CommandEntry<XCoreSender> commandEntry) {
        List<String> lines = new ArrayList<>();
        var command = commandEntry.command();
        for (var comp : command.components()) {
            if (comp.type() == CommandComponent.ComponentType.LITERAL || comp.type() == CommandComponent.ComponentType.FLAG) continue;
            String display = comp.required() ? "[white]<" + comp.name() + ">[]" : "[white][" + comp.name() + "][]";
            String key = "commands-" + commandName + "-" + comp.name() + "-description";
            String desc = session.locale().t(key);
            if (desc.equals(key)) desc = session.locale().t("help-no-arg-description");
            lines.add(session.locale().t("help-arg-entry", args("arg", display, "description", desc)));
        }
        return lines;
    }

    private String formatAliases(Collection<String> aliases) {
        return aliases.stream().map(a -> "[white]/" + a + "[]").collect(java.util.stream.Collectors.joining("[gray], []"));
    }

    private List<String> extractVisibleAliases(UnifiedCommand cmd) {
        CommandEntry<XCoreSender> entry = cmd.primaryCloudEntry();
        if (entry == null) return List.of();

        Set<String> uniqueAliases = new LinkedHashSet<>();
        for (String alias : entry.command().rootComponent().alternativeAliases()) {
            if (!alias.equalsIgnoreCase(cmd.name())) {
                uniqueAliases.add(alias);
            }
        }
        return new ArrayList<>(uniqueAliases);
    }

    private String formatCommandLabel(Session session, UnifiedCommand cmd) {
        int overloads = cmd.syntaxes().size();
        if (overloads <= 1) return cmd.name();
        return session.locale().t("help-command-with-overload-count", args(
                "name", cmd.name(),
                "count", overloads
        ));
    }

    private void appendCloudArguments(Session session, StringBuilder sb, String commandName, List<CommandVariant> variants) {
        List<UsageArgs> usageArgs = collectUsageArgs(session, commandName, variants);
        if (usageArgs.isEmpty()) {
            return;
        }

        sb.append("\n\n").append(session.locale().t("help-args-title"));

        if (usageArgs.size() == 1 && variants.size() == 1) {
            usageArgs.get(0).args().forEach(line -> sb.append("\n").append(line));
            return;
        }

        for (int i = 0; i < usageArgs.size(); i++) {
            UsageArgs usage = usageArgs.get(i);
            sb.append("\n").append(session.locale().t("help-usage-args-title", args("syntax", usage.syntax())));
            usage.args().forEach(line -> sb.append("\n").append(line));
            if (i < usageArgs.size() - 1) sb.append("\n");
        }
    }

    private List<UsageArgs> collectUsageArgs(Session session, String commandName, List<CommandVariant> variants) {
        List<UsageArgs> result = new ArrayList<>();
        for (CommandVariant variant : variants) {
            List<String> args = extractArgs(session, commandName, variant.cloudEntry());
            if (!args.isEmpty()) {
                result.add(new UsageArgs(variant.syntax(), args));
            }
        }
        return result;
    }

    private void appendUsages(Session session, StringBuilder sb, List<String> syntaxes) {
        sb.append("\n\n").append(session.locale().t("help-usages-title"));
        for (String syntax : syntaxes) {
            sb.append("\n").append(session.locale().t("help-usage-entry", args("syntax", syntax)));
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private record UnifiedCommand(String name, List<CommandVariant> variants) {
        List<String> syntaxes() {
            return variants.stream().map(CommandVariant::syntax).toList();
        }

        String primarySyntax() {
            return variants.isEmpty() ? name : variants.get(0).syntax();
        }

        String rawDescription() {
            for (CommandVariant variant : variants) {
                if (variant.rawDescription() != null && !variant.rawDescription().isBlank()) return variant.rawDescription();
            }
            return "";
        }

        boolean isCloudCommand() {
            return variants.stream().anyMatch(CommandVariant::isCloud);
        }

        List<CommandVariant> cloudVariants() {
            return variants.stream().filter(CommandVariant::isCloud).toList();
        }

        List<CommandVariant> legacyVariants() {
            return variants.stream().filter(variant -> !variant.isCloud()).toList();
        }

        CommandEntry<XCoreSender> primaryCloudEntry() {
            for (CommandVariant variant : variants) {
                if (variant.cloudEntry() != null) return variant.cloudEntry();
            }
            return null;
        }
    }

    private record CommandVariant(String syntax, String rawDescription, CommandEntry<XCoreSender> cloudEntry,
                                  CommandHandler.Command legacyCommand) {
        static CommandVariant fromCloud(CommandEntry<XCoreSender> entry) {
            return new CommandVariant(entry.syntax(), extractDesc(entry), entry, null);
        }

        static CommandVariant fromLegacy(CommandHandler.Command cmd) {
            return new CommandVariant("/" + cmd.text + (cmd.paramText.isEmpty() ? "" : " " + cmd.paramText), cmd.description, null, cmd);
        }

        boolean isCloud() {
            return cloudEntry != null;
        }

        String params() {
            return legacyCommand != null ? legacyCommand.paramText : "";
        }

        private static String extractDesc(CommandEntry<XCoreSender> entry) {
            var d = entry.command().rootComponent().description();
            if (!d.isEmpty()) return d.textDescription();
            var cd = entry.command().commandDescription().description();
            return !cd.isEmpty() ? cd.textDescription() : "";
        }
    }

    private record UsageArgs(String syntax, List<String> args) {
    }

    private static final class UnifiedCommandBuilder {
        private final String name;
        private final Map<String, CommandVariant> variantsBySyntax = new LinkedHashMap<>();

        private UnifiedCommandBuilder(String name) {
            this.name = name;
        }

        private void addVariant(CommandVariant variant) {
            variantsBySyntax.putIfAbsent(variant.syntax(), variant);
        }

        private UnifiedCommand build() {
            return new UnifiedCommand(name, new ArrayList<>(variantsBySyntax.values()));
        }
    }
}
