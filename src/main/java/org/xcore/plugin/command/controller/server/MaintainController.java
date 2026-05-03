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
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.enums.Feature;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerDataCacheReloadCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerCommandExecuteCommandV1;
import org.xcore.plugin.service.MapIdentityAuditService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
    private final RuntimeToggleConfigService toggleConfigService;
    private final MapIdentityAuditService mapIdentityAuditService;
    private final TopMenuCacheService topMenuCacheService;

    @Inject
    public MaintainController(NetworkService network,
                              PlayerDataRepository playerDataRepository,
                              PluginState pluginState,
                              SessionService sessionService,
                              MapIdentityAuditService mapIdentityAuditService,
                              TopMenuCacheService topMenuCacheService,
                              Config config,
                              @Named("xcConfigFile") Fi configFile,
                              @Named("pretty") Gson prettyGson) {
        this.network = network;
        this.playerDataRepository = playerDataRepository;
        this.pluginState = pluginState;
        this.sessionService = sessionService;
        this.config = config;
        this.mapIdentityAuditService = mapIdentityAuditService;
        this.topMenuCacheService = topMenuCacheService;
        this.toggleConfigService = new RuntimeToggleConfigService(config, configFile, prettyGson);
    }

    public MaintainController(NetworkService network,
                              PlayerDataRepository playerDataRepository,
                              PluginState pluginState,
                              SessionService sessionService,
                              MapIdentityAuditService mapIdentityAuditService,
                              Config config,
                              Fi configFile,
                              Gson prettyGson) {
        this(network, playerDataRepository, pluginState, sessionService, mapIdentityAuditService, null, config, configFile, prettyGson);
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

        Core.settings.put("lastServerMode", mode.name());
        Core.settings.put("defaultGameMode", mode.name());
        Core.settings.forceSave();

        Log.info("Game mode changed to: " + mode.name());
    }

    @Command("redis-reload")
    @CommandDescription("Reloads Redis transport connection.")
    public void redisReload(XCoreSender sender) {
        if (network.reloadBackend()) {
            Log.info("Redis transport reloaded: backend=@", network.backendName());
        } else {
            Log.err("Redis transport reload failed. Transport backend is now disconnected.");
        }
    }

    @Command("transport-reload")
    @CommandDescription("Reloads transport backend using current config.")
    public void transportReload(XCoreSender sender) {
        if (network.reloadBackend()) {
            Log.info("Transport backend reloaded: backend=@", network.backendName());
        } else {
            Log.err("Transport backend reload failed. Transport backend is now disconnected.");
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
        if (deleted > 0 && topMenuCacheService != null) {
            topMenuCacheService.invalidateAll();
        }
        network.post(new PlayerDataCacheReloadCommandV1(config.server));
        Log.info("Deleted @ bots from database.", deleted);
    }

    @Command("audit-map-votes")
    @CommandDescription("Runs a read-only audit for legacy map identity collisions and affected map votes.")
    public void auditMapVotes(XCoreSender sender) {
        var report = mapIdentityAuditService.audit();

        Log.info("Map identity audit: mapsScanned=@ playersScanned=@ conflictGroups=@ conflictingMaps=@ affectedPlayers=@ affectedVoteReferences=@",
                report.mapsScanned(),
                report.playersScanned(),
                report.conflictGroups().size(),
                report.conflictingMapCount(),
                report.affectedPlayerCount(),
                report.affectedVoteReferenceCount());

        if (!report.hasConflicts()) {
            Log.info("No legacy map identity collisions found.");
            return;
        }

        for (var group : report.conflictGroups()) {
            Log.info("Conflict group '@' for map '@' mode='@': maps=@ affectedPlayers=@ affectedVoteReferences=@",
                    group.legacyKey(),
                    group.mapName(),
                    group.gameMode(),
                    group.maps().size(),
                    group.affectedPlayers().size(),
                    group.affectedVoteReferences());

            for (var map : group.maps()) {
                Log.info("  mapId=@ file='@' author='@' like=@ dislike=@ reputation=@",
                        map.mapId(),
                        map.fileName(),
                        map.author(),
                        map.like(),
                        map.dislike(),
                        map.reputation());
            }
        }
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
            network.post(new ServerCommandExecuteCommandV1(normalizedCommand, List.of(), false));
            return;
        }

        if (except) {
            Log.info("Dispatching '@' to [ALL EXCEPT @]", normalizedCommand, Seq.with(targets));
        } else {
            Log.info("Dispatching '@' to @", normalizedCommand, Seq.with(targets));
        }

        network.post(new ServerCommandExecuteCommandV1(normalizedCommand, Arrays.asList(targets), except));
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
        String normalized = toggleConfigService.normalizeCommandName(command);
        if (normalized == null) {
            Log.err("Command name cannot be empty.");
            return;
        }

        String rootCommand = toggleConfigService.extractRootCommand(normalized);
        if (PROTECTED_DISABLE_COMMANDS.contains(rootCommand)) {
            Log.err("Command '@' cannot be disabled.", rootCommand);
            return;
        }

        if (!toggleConfigService.disable(RuntimeToggleConfigService.ToggleTarget.COMMAND, normalized).changed()) {
            Log.info("Command '@' is already disabled.", normalized);
            return;
        }

        Log.info("Command '@' disabled.", normalized);
    }

    @Command("enable-cmd <command>")
    @CommandDescription("Re-enables a disabled command or command path.")
    public void enableCmd(XCoreSender sender, @Argument("command") @Greedy String command) {
        String normalized = toggleConfigService.normalizeCommandName(command);
        if (normalized == null) {
            Log.err("Command name cannot be empty.");
            return;
        }

        if (!toggleConfigService.enable(RuntimeToggleConfigService.ToggleTarget.COMMAND, normalized).changed()) {
            Log.info("Command '@' was not disabled.", normalized);
            return;
        }

        Log.info("Command '@' enabled.", normalized);
    }

    @Command("disabled-cmds")
    @CommandDescription("Lists all disabled commands.")
    public void disabledCmds(XCoreSender sender) {
        if (toggleConfigService.isEmpty(RuntimeToggleConfigService.ToggleTarget.COMMAND)) {
            Log.info("No commands are disabled.");
            return;
        }

        Log.info("Disabled commands: @", toggleConfigService.list(RuntimeToggleConfigService.ToggleTarget.COMMAND));
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

        if (!toggleConfigService.disable(RuntimeToggleConfigService.ToggleTarget.FEATURE, feature.get().key()).changed()) {
            Log.info("Feature '@' is already disabled.", feature.get().key());
            return;
        }

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

        if (!toggleConfigService.enable(RuntimeToggleConfigService.ToggleTarget.FEATURE, feature.get().key()).changed()) {
            Log.info("Feature '@' was not disabled.", feature.get().key());
            return;
        }

        Log.info("Feature '@' enabled.", feature.get().key());
    }

    @Command("disabled-features")
    @CommandDescription("Lists all disabled features.")
    public void disabledFeatures(XCoreSender sender) {
        if (toggleConfigService.isEmpty(RuntimeToggleConfigService.ToggleTarget.FEATURE)) {
            Log.info("No features are disabled.");
            return;
        }

        Log.info("Disabled features: @", toggleConfigService.list(RuntimeToggleConfigService.ToggleTarget.FEATURE));
    }

}
