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
            String syntax = cmd.syntax();

            Log.info("   @ - @", syntax, desc);
        }
    }

    private void showQuery(XCoreSender sender, String query) {
        HelpQueryResult<XCoreSender> result = helpHandler.query(org.incendo.cloud.help.HelpQuery.of(sender, query));

        if (result instanceof VerboseCommandResult<XCoreSender> verbose) {
            printEntryDetails(UnifiedCommand.fromCloud(verbose.entry().command().rootComponent().name(), verbose.entry()));
            return;
        }

        if (result instanceof MultipleCommandResult<XCoreSender> multiple) {
            Log.info("Multiple matches found: @", Strings.join(", ", multiple.childSuggestions()));
            return;
        }

        // If not found in cloud (or it's just an index result), check vanilla commands specifically
        CommandHandler.Command legacyCmd = findLegacyCommand(query);
        if (legacyCmd != null) {
            printEntryDetails(UnifiedCommand.fromLegacy(legacyCmd));
            return;
        }

        Log.info("Command '@' not found.", query);
    }

    private void printEntryDetails(UnifiedCommand cmd) {
        Log.info("Command: @", cmd.name());
        Log.info("Usage: @", cmd.syntax());
        Log.info("Description: @", cmd.description());

        if (cmd.isCloudCommand() && cmd.cloudEntry() != null) {
            var root = cmd.cloudEntry().command().rootComponent();
            if (!root.aliases().isEmpty()) {
                Log.info("Aliases: @", Strings.join(", ", root.aliases()));
            }

            var components = cmd.cloudEntry().command().components();
            boolean hasArgs = false;
            for (var component : components) {
                if (component.type() == CommandComponent.ComponentType.LITERAL ||
                        component.type() == CommandComponent.ComponentType.FLAG) continue;

                if (!hasArgs) {
                    Log.info("Arguments:");
                    hasArgs = true;
                }

                String desc = component.description().textDescription();
                if (desc.isEmpty()) desc = "No description";

                Log.info("   @ - @", component.name(), desc);
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
        Map<String, UnifiedCommand> commandMap = new LinkedHashMap<>();
        var handler = ServerControl.instance.handler;

        IndexCommandResult<XCoreSender> index = helpHandler.queryRootIndex(sender);
        Map<String, CommandEntry<XCoreSender>> cloudCommands = new HashMap<>();

        for (CommandEntry<XCoreSender> entry : index.entries()) {
            String name = entry.command().rootComponent().name();
            cloudCommands.put(name.toLowerCase(), entry);
        }

        for (var cmd : handler.getCommandList()) {
            String nameLower = cmd.text.toLowerCase();

            if (cmd instanceof MindustryCloudCommand<?> || cloudCommands.containsKey(nameLower)) {
                CommandEntry<XCoreSender> entry = cloudCommands.get(nameLower);
                if (entry != null) {
                    commandMap.put(cmd.text, UnifiedCommand.fromCloud(cmd.text, entry));
                }
            } else {
                commandMap.putIfAbsent(cmd.text, UnifiedCommand.fromLegacy(cmd));
            }
        }

        return new ArrayList<>(commandMap.values());
    }

    private record UnifiedCommand(
            String name,
            String syntax,
            String description,
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
                    cmd.text + (cmd.paramText.isEmpty() ? "" : " " + cmd.paramText),
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
            return !cmdDesc.isEmpty() ? cmdDesc.textDescription() : "No description provided.";
        }
    }
}
