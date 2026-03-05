package org.xcore.plugin.event;

import arc.Events;
import arc.func.Boolf;
import arc.util.Log;
import arc.util.Time;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Administration.TraceInfo;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.service.*;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.model.BanRequestData;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressService;
import org.xcore.plugin.vote.VoteChoice;
import org.xcore.plugin.vote.VoteKick;
import org.xcore.plugin.vote.VoteService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class NetEventService {

    @Getter @Setter
    public Boolf<String> ipAcceptor = (ip) -> true;
    public int blockedIPs = 0;
    public int blockedIPsPerMinute = 0;

    private final SessionService sessionService;
    private final Config config;
    private final TranslatorService translatorService;
    private final NetworkService network;
    private final VoteService voteService;
    private final SecurityService securityService;
    private final IngressService ingressService;
    private final Gson rawGson;

    @Inject
    public NetEventService(SessionService sessionService, Config config,
                           TranslatorService translatorService, NetworkService network,
                           VoteService voteService,
                           SecurityService securityService,
                           IngressService ingressService,
                           @Named("raw") Gson rawGson) {
        this.sessionService = sessionService;
        this.config = config;
        this.translatorService = translatorService;
        this.network = network;
        this.voteService = voteService;
        this.securityService = securityService;
        this.ingressService = ingressService;
        this.rawGson = rawGson;
    }


    public String chat(Player author, String text) {
        VoteChoice choice = VoteChoice.parse(text);

        if (choice.isValid() && voteService.isVoting()) {
            var currentVote = voteService.getCurrentSession();
            VoteKick currentKick = voteService.getCurrentVoteKick();
            if (currentKick != null && currentKick.isTarget(author)) {
                var session = sessionService.get(author);
                if (session != null) {
                    session.locale().send("error-vote-yourself", args());
                }
                return null;
            }
            if (currentVote.voted.containsKey(author.id)) {
                var session = sessionService.get(author);
                if (session != null) {
                    session.locale().send("error-already-voted", args());
                }
                return null;
            }
            currentVote.vote(author, choice.sign());
        }

        Log.info("&fi@: @", "&lc" + author.plainName(), "&lw" + text);

        if (securityService.isMuted(author)) return null;

        author.sendMessage(netServer.chatFormatter.format(author, text), author, text);
        translatorService.translate(author, text);

        network.post(new SocketEvents.MessageEvent(author.plainName(), text.replace("`", "*"), config.server));
        return null;
    }

    public void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        Player admin = con.player, target = packet.other;
        var action = packet.action;

        if (!admin.admin || target == null || (target.admin && target != admin)) return;

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
                target.kick(Packets.KickReason.banned);
                netServer.admins.banPlayerID(target.uuid());
                sessionService.broadcast("tempban-player-banned", args(
                        "adminName", admin.coloredName(),
                        "playerName", target.coloredName()));
                Log.info("@ banned @ (@)", admin.plainName(), target.plainName(), target.uuid());

                var targetData = sessionService.get(target.uuid()).data;
                String banJson = rawGson.toJson(new BanRequestData(targetData.pid, target.coloredName()));

                Call.clientPacketReliable(admin.con, "give_ban_data", banJson);
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

    public boolean connectFilter(String address) {
        if (!ipAcceptor.get(address)) {
            blockedIPs++;
            blockedIPsPerMinute++;
            return false;
        }
        return true;
    }

    public void connect(NetConnection con, Packets.Connect packet) {
        Events.fire(new EventType.ConnectionEvent(con));
    }

    public void connectPacket(NetConnection con, Packets.ConnectPacket packet) {
        if (con.kicked) return;

        Events.fire(new EventType.ConnectPacketEvent(con, packet));
        con.connectTime = Time.millis();

        AccessResult result = ingressService.validate(con, packet);

        if (result instanceof AccessResult.Denied(String reason, boolean silent, long kickDuration)) {
            if (silent) {
                con.close();
            } else {
                con.kick(reason, kickDuration);
            }
            return;
        }

        String uuid = packet.uuid;
        Administration.PlayerInfo info = netServer.admins.getInfo(uuid);

        netServer.admins.updatePlayerJoined(uuid, con.address, packet.name);

        Player player = Player.create();
        player.admin = netServer.admins.isAdmin(uuid, packet.usid);
        player.con = con;
        player.con.usid = packet.usid;
        player.con.uuid = uuid;
        player.con.mobile = packet.mobile;
        player.name = packet.name;
        player.locale = packet.locale;
        player.color.set(packet.color).a(1f);

        if (!player.admin && !info.admin) {
            info.adminUsid = packet.usid;
        }

        con.player = player;
        player.team(netServer.assignTeam(player));
        netServer.sendWorldData(player);

        Events.fire(new EventType.PlayerConnect(player));
    }
}
