package org.xcore.plugin.commands.controllers.server;

import arc.struct.Seq;
import arc.util.Log;
import mindustry.net.Packets;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.ServerContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.TextArgumentSplitter;

import static org.xcore.plugin.PluginVars.*;
import static mindustry.Vars.netServer;

@SuppressWarnings("unused")
public class MaintainController {

    @Command(name = "exit", description = "Exit the server application safely.")
    public void exit(ServerContext ctx) {
        Log.info("Shutting down server.");
        netServer.kickAll(Packets.KickReason.serverRestarting);
        System.exit(0);
    }

    @Command(name = "reload-config", description = "Reload local and global configurations.")
    public void reload(ServerContext ctx) {
        Config.init();
        GlobalConfig.init();
        Log.info("Configurations reloaded.");
    }

    @Command(name = "sock-restart", description = "Restart the NetSock connection.")
    public void sockRestart(ServerContext ctx) {
        NetSock.sock.disconnect();
        NetSock.safeConnect();
        Log.info("NetSock restarted.");
    }

    @Command(name = "gg-restart", params = "[on/off]", description = "Toggle server restart on GameOver.")
    public void ggRestart(ServerContext ctx) {
        gameoverRestart = ctx.args().length == 0 || !ctx.arg(0).equals("off");
        Log.info("GameOver restart turned @", gameoverRestart ? "on" : "off");
    }

    @Command(name = "db-clear-bots", description = "Clear low-playtime players from DB")
    public void clearBots(ServerContext ctx) {
        long deleted = database.getPlayerDatas().clearBots();
        NetSock.post(new SocketEvents.ReloadPlayerDataCache());
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
        NetSock.post(new SocketEvents.ExecuteCommand(cmd, targets.toArray(String.class)));
    }
}