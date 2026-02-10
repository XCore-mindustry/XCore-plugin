package org.xcore.plugin.command.controller.server;

import arc.struct.Seq;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.net.Packets;
import org.incendo.cloud.annotations.*;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.NetworkService;

import static mindustry.Vars.netServer;

@Singleton
public class MaintainController implements CloudServerController {

    private final NetworkService network;
    private final PlayerDataRepository playerDataRepository;
    private final PluginState pluginState;
    private final GlobalConfig globalConfig;

    @Inject
    public MaintainController(NetworkService network,
                              PlayerDataRepository playerDataRepository,
                              PluginState pluginState,
                              GlobalConfig globalConfig) {
        this.network = network;
        this.playerDataRepository = playerDataRepository;
        this.pluginState = pluginState;
        this.globalConfig = globalConfig;
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
                for (String serverName : globalConfig.servers.keys()) {
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
}
