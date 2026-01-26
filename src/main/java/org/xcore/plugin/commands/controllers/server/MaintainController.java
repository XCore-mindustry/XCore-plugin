package org.xcore.plugin.commands.controllers.server;

import arc.struct.Seq;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.net.Packets;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.ServerContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.network.NetworkService;
import org.xcore.plugin.utils.TextArgumentSplitter;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.gameoverRestart;

@Singleton
public class MaintainController {

    private final NetworkService network;
    private final DatabaseService database;

    @Inject
    public MaintainController(NetworkService network, DatabaseService database) {
        this.network = network;
        this.database = database;
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
        gameoverRestart = ctx.args().length == 0 || !ctx.arg(0).equals("off");
        Log.info("GameOver restart turned @", gameoverRestart ? "on" : "off");
    }

    @Command(name = "db-delete-bots", description = "Delete low-playtime players from DB")
    public void deleteBots(ServerContext ctx) {
        long deleted = database.getPlayerDataRepository().deleteBots();
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
