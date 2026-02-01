package org.xcore.plugin.command.controller.server;

import arc.struct.Seq;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.net.Packets;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ServerContext;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.common.TextArgumentSplitter;

import static mindustry.Vars.netServer;

@Singleton
public class MaintainController {

    private final NetworkService network;
    private final PlayerDataRepository playerDataRepository;
    private final PluginState pluginState;

    @Inject
    public MaintainController(NetworkService network,
                              PlayerDataRepository playerDataRepository,
                              PluginState pluginState) {
        this.network = network;
        this.playerDataRepository = playerDataRepository;
        this.pluginState = pluginState;
    }

    @Command(name = "exit", description = "Exit the server application safely.")
    public void exit(ServerContext ctx) {
        Log.info("Shutting down server.");
        netServer.kickAll(Packets.KickReason.serverRestarting);
        System.exit(0);
    }

    @Command(name = "sock-restart", description = "Restart the NetSock connection.")
    public void sockRestart(ServerContext ctx) {
        network.disconnect();
        network.safeConnect();
        Log.info("NetSock restarted.");
    }

    @Command(name = "gg-restart", params = "[on/off]", description = "Toggle server restart on GameOver.")
    public void ggRestart(ServerContext ctx) {
        pluginState.restartOnGameOver = ctx.args().length == 0 || !ctx.arg(0).equals("off");
        Log.info("GameOver restart turned @", pluginState.restartOnGameOver ? "on" : "off");
    }

    @Command(name = "db-delete-bots", description = "Delete low-playtime players from DB")
    public void deleteBots(ServerContext ctx) {
        long deleted = playerDataRepository.deleteBots();
        network.post(new SocketEvents.ReloadPlayerDataCache());
        Log.info("Deleted @ bots from database.", deleted);
    }

    @Command(name = "gcmd", params = "<args...>", description = "Execute command on other servers")
    public void gcmd(ServerContext ctx) {
        if (ctx.args().length == 0 || ctx.arg(0).isBlank()) return;
        String[] parsed = TextArgumentSplitter.split(ctx.arg(0));
        if (parsed.length == 0) return;

        String cmd = parsed[0];
        if (cmd.equalsIgnoreCase("gcmd")) return;

        Seq<String> targets = new Seq<>();
        for (int i = 1; i < parsed.length; i++) targets.add(parsed[i]);

        Log.info("Dispatching '@' to @", cmd, targets.isEmpty() ? "[ALL]" : targets);
        network.post(new SocketEvents.ExecuteCommand(cmd, targets.toArray(String.class)));
    }
}
