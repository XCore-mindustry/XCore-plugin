package org.xcore.plugin.event.handler;

import arc.util.Log;
import arc.util.Time;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.event.SocketEvents;

import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class ConnectionHandler {

    private final SessionService sessionService;
    private final AdminDataRepository adminDataRepository;
    private final NetworkService network;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final VoteService voteService;
    private final PrivateMessageService privateMessageService;
    private final PlayerDisplayService playerDisplayService;

    @Inject
    public ConnectionHandler(SessionService sessionService,
                             AdminDataRepository adminDataRepository,
                             NetworkService network,
                             Config config,
                             GlobalConfig globalConfig,
                             VoteService voteService,
                             PrivateMessageService privateMessageService,
                             PlayerDisplayService playerDisplayService) {
        this.sessionService = sessionService;
        this.adminDataRepository = adminDataRepository;
        this.network = network;
        this.config = config;
        this.globalConfig = globalConfig;
        this.voteService = voteService;
        this.privateMessageService = privateMessageService;
        this.playerDisplayService = playerDisplayService;
    }

    public void onPlayerJoin(PlayerJoin event) {
        var player = event.player;

        Session session = sessionService.registerLogin(player);
        if (session == null || session.data == null) {
            Log.err("Session is null! Player: @", player);
            player.kick("Session is null! Write to us on Discord to resolve issues.");
            return;
        }
        PlayerData data = session.data;
        Localization locale = session.locale();

        Time.runTask(120, () -> {
            if (player != null && player.con != null && player.con.isConnected()) {
                if (player.con.lastReceivedClientSnapshot == -1) {
                    String kickMsg = locale.format("kick-bot-protection", args());
                    player.kick(kickMsg);
                }
            }
        });

        locale.send("welcome", args("serverName", mindustry.net.Administration.Config.serverName.string()));

        data.setNickname(player.coloredName()).setPlayer(player);

        Call.clientPacketReliable(player.con, "adm_mod_begin", "");

        if (data.exists && !data.ip.equals(player.ip())) {
            if (player.admin) {
                var adminData = adminDataRepository.findByUuid(data.uuid);
                adminData.adminConfirmed = false;
                adminDataRepository.save(adminData);

                player.admin = false;
                netServer.admins.unAdminPlayer(player.uuid());
                locale.send("error-ip-changed", args());
            }

            data.setIp(player.ip());
            session.save();
        }

        if (data.admin && data.adminConfirmed) {
            player.admin = true;
            if (!netServer.admins.isAdmin(player.uuid(), player.usid())) {
                netServer.admins.adminPlayer(player.uuid(), player.usid());
            }
        }

        if (!data.exists) {
            data.setIp(player.ip());
            data.exists = true;
            session.save();
        }

        playerDisplayService.refresh(session);

        if (player.getInfo().timesJoined < 5) {
            Call.openURI(player.con, globalConfig.discordUrl);
        }

        long unreadMessages = privateMessageService.countUnread(data.uuid);
        if (unreadMessages > 0) {
            locale.send("private-message-join-notification", args("count", unreadMessages));
        }

        Log.info("@ #@ @ joined", player.plainName(), data.pid, player.uuid());
        sessionService.broadcast("player-joined", args(
                "nickname", player.coloredName(),
                "pid", data.pid));
        network.post(new SocketEvents.PlayerJoinLeaveEvent(
                player.plainName() + " #" + data.pid,
                config.server,
                true)
        );
    }

    public void onPlayerLeave(PlayerLeave event) {
        Player player = event.player;

        Session session = sessionService.registerLogout(event.player);
        PlayerData data = session.data;

        voteService.handleLeave(event.player);

        if (data != null) {
            Log.info("@ #@ @ left", player.plainName(), data.pid, player.uuid());
            sessionService.broadcast("player-left", args(
                    "nickname", player.coloredName(),
                    "pid", data.pid)
            );

            network.post(new SocketEvents.PlayerJoinLeaveEvent(
                    player.plainName() + " #" + data.pid,
                    config.server,
                    false)
            );
        }
    }
}
