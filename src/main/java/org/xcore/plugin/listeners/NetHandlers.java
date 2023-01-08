package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import mindustry.net.Packets;

import static mindustry.Vars.logic;
import static mindustry.Vars.netServer;

public class NetHandlers {
    public static void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        Player other = packet.other, admin = con.player;
        Packets.AdminAction action = packet.action;

        if (action != Packets.AdminAction.ban && !con.player.admin || con.player == null || packet.other == null) return;

        Events.fire(new EventType.AdminRequestEvent(admin, other, action));

        switch (action) {
            case kick -> other.kick(Packets.KickReason.kick);
            case ban -> netServer.admins.banPlayerID(other.uuid());
            case trace -> {
                var info = other.getInfo();
                Call.traceInfo(con, other, new Administration.TraceInfo(other.ip(), other.uuid(), other.con.modclient, other.con.mobile, info.timesJoined, info.timesKicked));
                Log.info("@ has requested trace info of @.", admin.plainName(), other.plainName());
            }
            case wave -> {
                logic.skipWave();
                Log.info("@ has skipped the wave.", admin.plainName());
            }
        }
    }
}
