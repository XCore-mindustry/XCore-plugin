package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.game.EventType;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.discord.Bot;

import static mindustry.Vars.logic;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.isSocketServer;

public class NetEvents {
    public static String chat(Player player, String message) {
        return player != null ? "[coral][[[cyan]" + Database.cachedPlayerData.get(player.uuid()).rating + " [sky]#[white] " + player.coloredName() + "[coral]]: [white]" + message : message;
    }

    public static void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        Player admin = con.player, target = packet.other;
        var action = packet.action;

        if (!admin.admin || target == null || (target.admin && target != admin)) return;

        Events.fire(new EventType.AdminRequestEvent(admin, target, action));

        switch (action) {
            case kick -> target.kick(Packets.KickReason.kick);
            case ban -> {
                target.kick(Packets.KickReason.banned);
                netServer.admins.banPlayerID(target.uuid());
                netServer.admins.banPlayerIP(target.ip());
                Call.sendMessage(Strings.format("@[] banned @[].", admin.coloredName(), target.coloredName()));

                if (isSocketServer) {
                    Bot.sendBanEvent(target.plainName(), admin.plainName());
                } else {
                    JavelinPlugin.getJavelinSocket().sendEvent(new SocketEvents.BanEvent(target.plainName(), admin.plainName(), config.server));
                }
            }
            case trace -> {
                var info = target.getInfo();
                Call.traceInfo(con, target, new Administration.TraceInfo(target.ip(), target.uuid(), target.con.modclient, target.con.mobile, info.timesJoined, info.timesKicked));
                Log.info("@ has requested trace info of @.", admin.plainName(), target.plainName());
            }
            case wave -> {
                logic.skipWave();
                Log.info("@ has skipped the wave.", admin.plainName());
            }
        }
    }
}
