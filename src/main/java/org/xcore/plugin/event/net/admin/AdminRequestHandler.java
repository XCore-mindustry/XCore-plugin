package org.xcore.plugin.event.net.admin;

import arc.Events;
import arc.util.Log;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration.TraceInfo;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.integration.AdminModIntegration;
import org.xcore.plugin.model.BanRequestData;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.BanMenu;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class AdminRequestHandler {

    private final SessionService sessionService;
    private final BanMenu banMenu;
    private final AdminModIntegration adminModIntegration;
    private final Gson rawGson;

    @Inject
    public AdminRequestHandler(SessionService sessionService,
                               BanMenu banMenu,
                               AdminModIntegration adminModIntegration,
                               @Named("raw") Gson rawGson) {
        this.sessionService = sessionService;
        this.banMenu = banMenu;
        this.adminModIntegration = adminModIntegration;
        this.rawGson = rawGson;
    }

    public void handle(NetConnection con, AdminRequestCallPacket packet) {
        Player admin = con.player;
        Player target = packet.other;
        var action = packet.action;

        if (!admin.admin || target == null || (target.admin && target != admin)) {
            return;
        }

        Events.fire(new EventType.AdminRequestEvent(admin, target, action));

        switch (action) {
            case kick -> {
                target.kick(Packets.KickReason.kick);

                sessionService.broadcast("notification-admin-kick", args(
                        "admin", admin.coloredName(),
                        "target", target.coloredName()
                ));

                Log.info("@ kicked @ (@)", admin.plainName(), target.plainName(), target.uuid());
            }
            case ban -> {
                var adminSession = sessionService.get(admin);
                var targetSession = sessionService.get(target);
                if (adminSession == null || targetSession == null || targetSession.data == null) {
                    return;
                }

                if (adminSession.data.adminModVersion != null) {
                    target.kick(Packets.KickReason.banned);
                    adminModIntegration.holdVanillaBan(target.uuid());
                    String banJson = rawGson.toJson(new BanRequestData(targetSession.data.pid, target.coloredName()));
                    Call.clientPacketReliable(admin.con, "give_ban_data", banJson);
                } else {
                    banMenu.open(admin, target);
                }
            }
            case trace -> {
                var data = sessionService.getOrLoadFromDb(target.uuid());

                var trace = new TraceInfo(
                        target.ip(),
                        String.valueOf(data == null ? -1 : data.pid),
                        target.locale(),
                        target.con.modclient,
                        target.con.mobile,
                        target.getInfo().timesJoined,
                        target.getInfo().timesKicked,
                        target.getInfo().ips.toArray(String.class),
                        target.getInfo().names.toArray(String.class)
                );

                Call.traceInfo(con, target, trace);
                Log.info("@ has requested trace info of @.", admin.plainName(), target.plainName());
            }
            case wave -> {
                Vars.logic.skipWave();
                sessionService.broadcast("notification-admin-wave-skip", args(
                        "admin", admin.coloredName()
                ));
                Log.info("@ has skipped the wave.", admin.plainName());
            }
            case switchTeam -> {
                var session = sessionService.get(con.player);
                if (session != null) {
                    session.locale().send("error-access-denied", args());
                }
            }
        }
    }
}
