package org.xcore.plugin.command.controller.server;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.net.Packets;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.*;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.NetworkService;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static mindustry.Vars.netServer;

@Singleton
public class MaintainController implements CloudServerController {
    private static final Set<String> PROTECTED_DISABLE_COMMANDS = Set.of(
            "disable-cmd",
            "enable-cmd",
            "disabled-cmds"
    );

    private final NetworkService network;
    private final PlayerDataRepository playerDataRepository;
    private final PluginState pluginState;
    private final GlobalConfig globalConfig;
    private final Config config;
    private final Fi configFile;
    private final Gson prettyGson;

    @Inject
    public MaintainController(NetworkService network,
                              PlayerDataRepository playerDataRepository,
                              PluginState pluginState,
                              GlobalConfig globalConfig,
                              Config config,
                              @Named("xcConfigFile") Fi configFile,
                              @Named("pretty") Gson prettyGson) {
        this.network = network;
        this.playerDataRepository = playerDataRepository;
        this.pluginState = pluginState;
        this.globalConfig = globalConfig;
        this.config = config;
        this.configFile = configFile;
        this.prettyGson = prettyGson;
    }

    @Command("exit")
    @CommandDescription("Terminates the server process immediately.")
    public void exit(XCoreSender sender) {
        Log.info("Shutting down server.");
        netServer.kickAll(Packets.KickReason.serverRestarting);
        System.exit(0);
    }

    @Command("sock-restart")
    @CommandDescription("Restarts the socket service connection.")
    public void sockRestart(XCoreSender sender) {
        network.disconnect();
        network.safeConnect();
        Log.info("NetSock restarted.");
    }

    @Command("gg-restart [state]")
    @CommandDescription("Toggles automatic server restart on Game Over.")
    public void ggRestart(XCoreSender sender,
                          @Argument(value = "state", description = "New state (on/off)") @Default("on") String state) {
        pluginState.restartOnGameOver = !state.equalsIgnoreCase("off");
        Log.info("GameOver restart turned @", pluginState.restartOnGameOver ? "on" : "off");
    }

    @Command("db-delete-bots")
    @CommandDescription("Deletes players with less than 2 minutes of playtime from the database.")
    public void deleteBots(XCoreSender sender) {
        long deleted = playerDataRepository.deleteBots();
        network.post(new SocketEvents.ReloadPlayerDataCache());
        Log.info("Deleted @ bots from database.", deleted);
    }

    @Command("gcmd <command> [targets]")
    @CommandDescription("Executes a command on remote servers via socket.")
    public void gcmd(XCoreSender sender,
                     @Argument(value = "command", description = "Command to execute. Use quotes for spaces.") String command,
                     @Argument(value = "targets", description = "Target server names") String[] targets,
                     @Flag(value = "except", description = "Invert targets (execute everywhere EXCEPT targets)") boolean except) {

        Seq<String> finalTargets = new Seq<>();

        if ((targets == null || targets.length == 0) && !except) {
            Log.info("Dispatching '@' to [ALL]", command);
            network.post(new SocketEvents.ExecuteCommand(command, new String[0]));
            return;
        }

        if (targets != null) {
            if (except) {
                Seq<String> toExclude = Seq.with(targets);
                for (String serverName : globalConfig.servers.keySet()) {
                    if (!toExclude.contains(serverName)) {
                        finalTargets.add(serverName);
                    }
                }
                Log.info("Dispatching '@' to [ALL EXCEPT @]", command, toExclude);
            } else {
                finalTargets.addAll(targets);
                Log.info("Dispatching '@' to @", command, finalTargets);
            }
        } else {
            Log.info("Dispatching '@' to [ALL]", command);
        }

        network.post(new SocketEvents.ExecuteCommand(command, finalTargets.toArray(String.class)));
    }

    @Command("disable-cmd <command>")
    @CommandDescription("Disables a command or command path at runtime.")
    public void disableCmd(XCoreSender sender, @Argument("command") @Greedy String command) {
        String normalized = normalizeCommandName(command);
        if (normalized == null) {
            Log.err("Command name cannot be empty.");
            return;
        }

        String rootCommand = extractRootCommand(normalized);
        if (PROTECTED_DISABLE_COMMANDS.contains(rootCommand)) {
            Log.err("Command '@' cannot be disabled.", rootCommand);
            return;
        }

        Set<String> disabledCommands = mutableDisabledCommands();
        if (!disabledCommands.add(normalized)) {
            Log.info("Command '@' is already disabled.", normalized);
            return;
        }

        saveConfig();
        Log.info("Command '@' disabled.", normalized);
    }

    @Command("enable-cmd <command>")
    @CommandDescription("Re-enables a disabled command or command path.")
    public void enableCmd(XCoreSender sender, @Argument("command") @Greedy String command) {
        String normalized = normalizeCommandName(command);
        if (normalized == null) {
            Log.err("Command name cannot be empty.");
            return;
        }

        Set<String> disabledCommands = mutableDisabledCommands();
        if (!disabledCommands.remove(normalized)) {
            Log.info("Command '@' was not disabled.", normalized);
            return;
        }

        saveConfig();
        Log.info("Command '@' enabled.", normalized);
    }

    @Command("disabled-cmds")
    @CommandDescription("Lists all disabled commands.")
    public void disabledCmds(XCoreSender sender) {
        if (config.disabledCommands == null || config.disabledCommands.isEmpty()) {
            Log.info("No commands are disabled.");
            return;
        }

        Set<String> ordered = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ordered.addAll(config.disabledCommands);
        Log.info("Disabled commands: @", String.join(", ", ordered));
    }

    private Set<String> mutableDisabledCommands() {
        if (config.disabledCommands == null) {
            config.disabledCommands = new HashSet<>();
        } else if (!(config.disabledCommands instanceof HashSet<?>)) {
            config.disabledCommands = new HashSet<>(config.disabledCommands);
        }
        return config.disabledCommands;
    }

    private String extractRootCommand(String normalizedCommand) {
        return normalizedCommand.split(" ", 2)[0];
    }

    private String normalizeCommandName(String commandName) {
        if (commandName == null) {
            return null;
        }
        String normalized = commandName.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private void saveConfig() {
        configFile.writeString(prettyGson.toJson(config));
    }
}
