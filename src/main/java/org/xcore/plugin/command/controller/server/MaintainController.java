package org.xcore.plugin.command.controller.server;

import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
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
import org.xcore.plugin.model.enums.Feature;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static com.ospx.flubundle.Bundle.args;
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
    private final SessionService sessionService;
    private final Config config;
    private final Fi configFile;
    private final Gson prettyGson;

    @Inject
    public MaintainController(NetworkService network,
                              PlayerDataRepository playerDataRepository,
                              PluginState pluginState,
                              SessionService sessionService,
                              Config config,
                              @Named("xcConfigFile") Fi configFile,
                              @Named("pretty") Gson prettyGson) {
        this.network = network;
        this.playerDataRepository = playerDataRepository;
        this.pluginState = pluginState;
        this.sessionService = sessionService;
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

    @Command("set-team [id] [pid]")
    public void setTeam(XCoreSender sender, @Argument("id") int id, @Argument("pid") int pid) {
        Team team = Team.get(id);


        var dbPlayer = sessionService.getOrLoadFromDb(pid);
        Session targetSession = (dbPlayer != null) ? sessionService.get(dbPlayer.uuid) : null;

        if (targetSession == null || targetSession.player == null) {
            Log.err("[scarlet]Player not found.");
            return;
        }

        targetSession.player.team(team);

        Log.info("[green]Player's team [white]" + targetSession.player.name() + "[green]changed to [white]" + team.name);
    }

    @Command("set-gamemode [name]")
    public void setGamemode(XCoreSender sender, @Argument("name") @Default("-1") String name) {
        Gamemode mode = Seq.with(Gamemode.all).find(g -> g.name().equalsIgnoreCase(name));

        if (mode == null) {
            Log.err("[scarlet]Error: Mode " + name + " not found. Available: survival, sandbox, attack, pvp.");
            return;
        }

        mode.apply(Vars.state.rules);
        Call.setRules(Vars.state.rules);

        Core.settings.put("defaultGameMode", mode.name());
        Core.settings.forceSave();

        Log.info("Game mode changed to: " + mode.name());
    }

    @Command("redis-reload")
    @CommandDescription("Reloads Redis transport connection.")
    public void redisReload(XCoreSender sender) {
        network.disconnect();
        network.safeConnect();
        Log.info("Redis transport reloaded.");
    }

    @Command("transport-reload")
    @CommandDescription("Reloads transport backend using current config.")
    public void transportReload(XCoreSender sender) {
        if (network.reloadBackend()) {
            Log.info("Transport backend reloaded: backend=@", network.backendName());
        } else {
            Log.err("Transport backend reload failed. Previous backend remains active.");
        }
    }

    @Command("gg-restart [state]")
    @CommandDescription("Toggles automatic server restart on Game Over.")
    public void ggRestart(XCoreSender sender,
                          @Argument(value = "state", description = "New state") @Default("true") boolean state) {
        pluginState.restartOnGameOver = state;
        Log.info("GameOver restart turned @", pluginState.restartOnGameOver ? "on" : "off");
    }

    @Command("db-delete-bots")
    @CommandDescription("Deletes players with less than 2 minutes of playtime from the database.")
    public void deleteBots(XCoreSender sender) {
        long deleted = playerDataRepository.deleteBots();
        network.post(new SocketEvents.ReloadPlayerDataCache());
        Log.info("Deleted @ bots from database.", deleted);
    }

    @Command("gcmd <command>")
    @CommandDescription("Executes a command on remote servers via socket. Example: gcmd --targets mini-pvp,mini-hexed -- say hello world")
    public void gcmd(XCoreSender sender,
                     @Argument(value = "command", description = "Command to execute on remote servers") @Greedy String command,
                     @Flag(value = "targets", description = "Comma-separated target server names") String targetsCsv,
                     @Flag(value = "except", description = "Invert targets (execute everywhere EXCEPT targets)") boolean except) {

        if (command == null || command.isBlank()) {
            Log.err("Usage: gcmd [--targets server-a,server-b] [--except] -- <command>");
            return;
        }

        String normalizedCommand = command.trim();
        String[] targets = parseTargetList(targetsCsv);

        if (targets.length == 0) {
            Log.info("Dispatching '@' to [ALL]", normalizedCommand);
            network.post(new SocketEvents.ExecuteCommand(normalizedCommand, new String[0], false));
            return;
        }

        if (except) {
            Log.info("Dispatching '@' to [ALL EXCEPT @]", normalizedCommand, Seq.with(targets));
        } else {
            Log.info("Dispatching '@' to @", normalizedCommand, Seq.with(targets));
        }

        network.post(new SocketEvents.ExecuteCommand(normalizedCommand, targets, except));
    }

    private String[] parseTargetList(String targetsCsv) {
        if (targetsCsv == null || targetsCsv.isBlank()) {
            return new String[0];
        }

        return Arrays.stream(targetsCsv.split(","))
                .map(String::trim)
                .filter(target -> !target.isEmpty())
                .distinct()
                .toArray(String[]::new);
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

    @Command("disable-feature <feature>")
    @CommandDescription("Disables a feature by key (e.g. rtv) at runtime.")
    public void disableFeature(XCoreSender sender, @Argument("feature") String featureKey) {
        var feature = Feature.fromKey(featureKey);
        if (feature.isEmpty()) {
            Log.err("Unknown feature '@'. Available: @", featureKey,
                    java.util.Arrays.stream(Feature.values()).map(Feature::key).collect(java.util.stream.Collectors.joining(", ")));
            return;
        }

        Set<String> disabledFeatures = mutableDisabledFeatures();
        if (!disabledFeatures.add(feature.get().key())) {
            Log.info("Feature '@' is already disabled.", feature.get().key());
            return;
        }

        saveConfig();
        Log.info("Feature '@' disabled.", feature.get().key());
    }

    @Command("enable-feature <feature>")
    @CommandDescription("Re-enables a disabled feature by key (e.g. rtv).")
    public void enableFeature(XCoreSender sender, @Argument("feature") String featureKey) {
        var feature = Feature.fromKey(featureKey);
        if (feature.isEmpty()) {
            Log.err("Unknown feature '@'. Available: @", featureKey,
                    java.util.Arrays.stream(Feature.values()).map(Feature::key).collect(java.util.stream.Collectors.joining(", ")));
            return;
        }

        Set<String> disabledFeatures = mutableDisabledFeatures();
        if (!disabledFeatures.remove(feature.get().key())) {
            Log.info("Feature '@' was not disabled.", feature.get().key());
            return;
        }

        saveConfig();
        Log.info("Feature '@' enabled.", feature.get().key());
    }

    @Command("disabled-features")
    @CommandDescription("Lists all disabled features.")
    public void disabledFeatures(XCoreSender sender) {
        if (config.disabledFeatures == null || config.disabledFeatures.isEmpty()) {
            Log.info("No features are disabled.");
            return;
        }

        var ordered = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ordered.addAll(config.disabledFeatures);
        Log.info("Disabled features: @", String.join(", ", ordered));
    }

    private Set<String> mutableDisabledFeatures() {
        if (config.disabledFeatures == null) {
            config.disabledFeatures = new HashSet<>();
        } else if (!(config.disabledFeatures instanceof HashSet<?>)) {
            config.disabledFeatures = new HashSet<>(config.disabledFeatures);
        }
        return config.disabledFeatures;
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
