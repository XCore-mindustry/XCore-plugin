package org.xcore.plugin.command.controller.server;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.server.ServerControl;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.help.result.CommandEntry;
import org.incendo.cloud.help.result.HelpQueryResult;
import org.incendo.cloud.help.result.IndexCommandResult;
import org.incendo.cloud.help.result.MultipleCommandResult;
import org.incendo.cloud.help.result.VerboseCommandResult;
import org.xcore.cloud.mindustry.MindustryCloudCommand;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;

import java.util.*;

@Singleton
public class ServerHelpController implements CloudServerController {

    private final CloudService cloud;
    private HelpHandler<XCoreSender> helpHandler;

    @Inject
    public ServerHelpController(CloudService cloud) {
        this.cloud = cloud;
    }

    @PostConstruct
    public void init() {
        this.helpHandler = cloud.getServerHelpHandler();
    }

    @Command("help [query]")
    public void help(XCoreSender sender, @Argument("query") @Greedy String query) {
        if (query == null || query.isBlank()) {
            showIndex(sender);
        } else {
            showQuery(sender, query);
        }
    }

    private void showIndex(XCoreSender sender) {
        List<UnifiedCommand> allCommands = collectAllCommands(sender);
        allCommands.sort(Comparator.comparing(UnifiedCommand::name));

        Log.info("Commands:");
        for (UnifiedCommand cmd : allCommands) {
            String desc = cmd.description();
            Log.info("   @ - @", cmd.name(), desc);
            for (String syntax : cmd.syntaxes()) {
                Log.info("      @", syntax);
            }
        }
    }

    private void showQuery(XCoreSender sender, String query) {
        List<UnifiedCommand> allCommands = collectAllCommands(sender);
        HelpQueryResult<XCoreSender> result = helpHandler.query(org.incendo.cloud.help.HelpQuery.of(sender, query));

        if (result instanceof VerboseCommandResult<XCoreSender> verbose) {
            if (cloud.isCommandDisabled(verbose.entry().command())) {
                Log.info("Command '@' not found.", query);
                return;
            }

            String rootName = verbose.entry().command().rootComponent().name();
            UnifiedCommand cmd = findUnifiedCommand(allCommands, rootName);
            printEntryDetails(Objects.requireNonNullElseGet(cmd, () -> UnifiedCommand.fromCloud(rootName, verbose.entry())));
            return;
        }

        if (result instanceof MultipleCommandResult<XCoreSender> multiple) {
            Log.info("Multiple matches found: @", Strings.join(", ", multiple.childSuggestions()));
            return;
        }

        UnifiedCommand cmd = findUnifiedCommand(allCommands, query);
        if (cmd != null) {
            printEntryDetails(cmd);
            return;
        }

        // If not found in cloud (or it's just an index result), check vanilla commands specifically
        CommandHandler.Command legacyCmd = findLegacyCommand(query);
        if (legacyCmd != null && !cloud.isCommandDisabled(legacyCmd.text)) {
            printEntryDetails(UnifiedCommand.fromLegacy(legacyCmd));
        } else {
            Log.info("Command '@' not found.", query);
        }
    }

    private UnifiedCommand findUnifiedCommand(List<UnifiedCommand> commands, String name) {
        for (UnifiedCommand cmd : commands) {
            if (cmd.name().equalsIgnoreCase(name)) return cmd;
        }
        return null;
    }

    private void printEntryDetails(UnifiedCommand cmd) {
        Log.info("Command: @", cmd.name());
        Log.info("Description: @", cmd.description());

        if (cmd.syntaxes().size() == 1) {
            Log.info("Usage: @", cmd.syntaxes().getFirst());
        } else {
            Log.info("Usages:");
            for (String syntax : cmd.syntaxes()) {
                Log.info("   @", syntax);
            }
        }

        if (cmd.isCloudCommand() && cmd.primaryCloudEntry() != null) {
            var root = cmd.primaryCloudEntry().command().rootComponent();
            if (!root.aliases().isEmpty()) {
                Log.info("Aliases: @", Strings.join(", ", root.aliases()));
            }

            List<CommandVariant> cloudVariants = cmd.cloudVariants();
            boolean hasMultipleVariants = cloudVariants.size() > 1;

            for (CommandVariant variant : cloudVariants) {
                var components = variant.cloudEntry().command().components();
                List<CommandComponent<XCoreSender>> args = new ArrayList<>();
                for (var component : components) {
                    if (component.type() == CommandComponent.ComponentType.LITERAL ||
                            component.type() == CommandComponent.ComponentType.FLAG) {
                        continue;
                    }
                    args.add(component);
                }

                if (args.isEmpty()) continue;

                if (hasMultipleVariants) {
                    Log.info("Arguments for @:", variant.syntax());
                } else {
                    Log.info("Arguments:");
                }

                for (var component : args) {
                    String desc = component.description().textDescription();
                    if (desc.isEmpty()) desc = "No description";
                    Log.info("   @ - @", component.name(), desc);
                }
            }
        }
    }

    private CommandHandler.Command findLegacyCommand(String name) {
        var handler = ServerControl.instance.handler;
        for (CommandHandler.Command cmd : handler.getCommandList()) {
            if (cmd.text.equalsIgnoreCase(name)) return cmd;
        }
        return null;
    }

    private List<UnifiedCommand> collectAllCommands(XCoreSender sender) {
        Map<String, UnifiedCommandBuilder> commandMap = new LinkedHashMap<>();
        var handler = ServerControl.instance.handler;

        IndexCommandResult<XCoreSender> index = helpHandler.queryRootIndex(sender);
        Set<String> cloudNames = new HashSet<>();

        for (CommandEntry<XCoreSender> entry : index.entries()) {
            String rootName = entry.command().rootComponent().name();
            String rootKey = rootName.toLowerCase(Locale.ROOT);
            cloudNames.add(rootKey);
            for (String alias : entry.command().rootComponent().aliases()) {
                cloudNames.add(alias.toLowerCase(Locale.ROOT));
            }

            if (cloud.isCommandDisabled(entry.command())) {
                continue;
            }

            commandMap.computeIfAbsent(rootKey, ignored -> new UnifiedCommandBuilder(rootName))
                    .addVariant(CommandVariant.fromCloud(entry));
        }

        for (var cmd : handler.getCommandList()) {
            String nameLower = cmd.text.toLowerCase(Locale.ROOT);

            if (cmd instanceof MindustryCloudCommand<?> || cloudNames.contains(nameLower)) {
                continue;
            }

            if (cloud.isCommandDisabled(cmd.text)) {
                continue;
            }

            commandMap.computeIfAbsent(nameLower, ignored -> new UnifiedCommandBuilder(cmd.text))
                    .addVariant(CommandVariant.fromLegacy(cmd));
        }

        return new ArrayList<>(commandMap.values().stream().map(UnifiedCommandBuilder::build).toList());
    }

    private record UnifiedCommand(String name, List<CommandVariant> variants) {
        static UnifiedCommand fromCloud(String name, CommandEntry<XCoreSender> entry) {
            return new UnifiedCommand(name, List.of(CommandVariant.fromCloud(entry)));
        }

        static UnifiedCommand fromLegacy(CommandHandler.Command cmd) {
            return new UnifiedCommand(cmd.text, List.of(CommandVariant.fromLegacy(cmd)));
        }

        List<String> syntaxes() {
            return variants.stream().map(CommandVariant::syntax).toList();
        }

        String description() {
            for (CommandVariant variant : variants) {
                if (variant.description() != null && !variant.description().isBlank()) return variant.description();
            }
            return "No description provided.";
        }

        boolean isCloudCommand() {
            return variants.stream().anyMatch(CommandVariant::isCloud);
        }

        List<CommandVariant> cloudVariants() {
            return variants.stream().filter(CommandVariant::isCloud).toList();
        }

        CommandEntry<XCoreSender> primaryCloudEntry() {
            for (CommandVariant variant : variants) {
                if (variant.cloudEntry() != null) return variant.cloudEntry();
            }
            return null;
        }
    }

    private record CommandVariant(
            String syntax,
            String description,
            CommandEntry<XCoreSender> cloudEntry,
            CommandHandler.Command legacyCommand
    ) {
        static CommandVariant fromCloud(CommandEntry<XCoreSender> entry) {
            return new CommandVariant(entry.syntax(), extractCloudDescription(entry), entry, null);
        }

        static CommandVariant fromLegacy(CommandHandler.Command cmd) {
            return new CommandVariant(
                    cmd.text + (cmd.paramText.isEmpty() ? "" : " " + cmd.paramText),
                    cmd.description,
                    null,
                    cmd
            );
        }

        boolean isCloud() {
            return cloudEntry != null;
        }

        private static String extractCloudDescription(CommandEntry<XCoreSender> entry) {
            var desc = entry.command().rootComponent().description();
            if (!desc.isEmpty()) return desc.textDescription();
            var cmdDesc = entry.command().commandDescription().description();
            return !cmdDesc.isEmpty() ? cmdDesc.textDescription() : "No description provided.";
        }
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
