package org.xcore.plugin.event.handler;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerJoinLeaveV1;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.vote.VoteService;

import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class ConnectionHandler {

    private final SessionService sessionService;
    private final AdminDataRepository adminDataRepository;
    private final NetworkService network;
    private final TomlXcoreConfig config;
    private final TomlSecretsConfig secretsConfig;
    private final VoteService voteService;
    private final PrivateMessageService privateMessageService;
    private final PlayerDisplayService playerDisplayService;
    private final DiscordAdminAccessService discordAdminAccessService;
    private final ObserverService observerService;

    @Inject
    public ConnectionHandler(SessionService sessionService,
                             AdminDataRepository adminDataRepository,
                             NetworkService network,
                             TomlXcoreConfig config,
                             TomlSecretsConfig secretsConfig,
                             VoteService voteService,
                             PrivateMessageService privateMessageService,
                             PlayerDisplayService playerDisplayService,
                             DiscordAdminAccessService discordAdminAccessService,
                             ObserverService observerService) {
        this.sessionService = sessionService;
        this.adminDataRepository = adminDataRepository;
        this.network = network;
        this.config = config;
        this.secretsConfig = secretsConfig;
        this.voteService = voteService;
        this.privateMessageService = privateMessageService;
        this.playerDisplayService = playerDisplayService;
        this.discordAdminAccessService = discordAdminAccessService;
        this.observerService = observerService;
    }

    public void onPlayerJoin(PlayerJoin event) {
        var player = event.player;

        Session session = sessionService.registerLogin(player);
        if (session == null || session.data == null) {
            Log.err("Session is null! Player: @", player);
            player.kick("Session is null! Write to us on Discord to resolve issues.");
            return;
        }

        observerService.restore(player);

        PlayerData data = session.data;
        Localization locale = session.locale();

        locale.send("welcome", args("serverName", mindustry.net.Administration.Config.serverName.string()));

        data.nickname = player.coloredName();
        data.player = player;

        Call.clientPacketReliable(player.con, "adm_mod_begin", "");

        if (data.exists && !java.util.Objects.equals(data.ip, player.ip())) {
            if (player.admin) {
                discordAdminAccessService.deactivateRuntimeAdmin(player, player.uuid());
                locale.send("error-ip-changed", args());
            }

            sessionService.updateConnectionData(session, player.ip(), player.coloredName());
        }

        if (!data.exists) {
            data.ip = player.ip();
            data.exists = true;
            sessionService.persistPlayer(session);
        }

        playerDisplayService.refresh(session);

        if (player.getInfo().timesJoined < 5) {
            Call.openURI(player.con, secretsConfig.externalLinks.discordUrl);
        }

        long unreadMessages = privateMessageService.countUnread(data.uuid);
        if (unreadMessages > 0) {
            locale.send("private-message-join-notification", args("count", unreadMessages));
        }

        Log.info("@ #@ @ joined", player.plainName(), data.pid, player.uuid());
        sessionService.broadcast("player-joined", args(
                "nickname", player.coloredName(),
                "pid", data.pid));
        network.post(new PlayerJoinLeaveV1(
                player.plainName() + " #" + data.pid,
                config.server.name,
                true)
        );
    }

    public void onPlayerLeave(PlayerLeave event) {
        Player player = event.player;

        Session session = sessionService.registerLogout(event.player);
        PlayerData data = session != null ? session.data : null;

        voteService.handleLeave(event.player);

        if (data != null) {
            Log.info("@ #@ @ left", player.plainName(), data.pid, player.uuid());
            sessionService.broadcast("player-left", args(
                    "nickname", player.coloredName(),
                    "pid", data.pid)
            );

            network.post(new PlayerJoinLeaveV1(
                    player.plainName() + " #" + data.pid,
                    config.server.name,
                    false)
            );
        }
    }
}
