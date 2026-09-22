package org.xcore.plugin.event.handler;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerJoinLeaveV1;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.service.map.MapVoteObserverService;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.vote.VoteService;

import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class ConnectionHandler {

    private final SessionService sessionService;
    private final NetworkService network;
    private final TomlXcoreConfig config;
    private final TomlSecretsConfig secretsConfig;
    private final VoteService voteService;
    private final PrivateMessageService privateMessageService;
    private final PlayerDisplayService playerDisplayService;
    private final DiscordAdminAccessService discordAdminAccessService;
    private final ObserverService observerService;
    private final MapVoteObserverService mapVoteObserverService;

    @Inject
    public ConnectionHandler(SessionService sessionService,
                             NetworkService network,
                             TomlXcoreConfig config,
                             TomlSecretsConfig secretsConfig,
                             VoteService voteService,
                             PrivateMessageService privateMessageService,
                             PlayerDisplayService playerDisplayService,
                             DiscordAdminAccessService discordAdminAccessService,
                             ObserverService observerService,
                             MapVoteObserverService mapVoteObserverService) {
        this.sessionService = sessionService;
        this.network = network;
        this.config = config;
        this.secretsConfig = secretsConfig;
        this.voteService = voteService;
        this.privateMessageService = privateMessageService;
        this.playerDisplayService = playerDisplayService;
        this.discordAdminAccessService = discordAdminAccessService;
        this.observerService = observerService;
        this.mapVoteObserverService = mapVoteObserverService;
    }

    public void onPlayerJoin(PlayerJoin event) {
        if (event == null || event.player == null) return;
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

        if (locale != null) {
            locale.send("welcome", args("serverName", mindustry.net.Administration.Config.serverName.string()));
        }

        String currentIp = player.ip();
        String currentName = player.coloredName();
        boolean ipChanged = !Objects.equals(data.ip, currentIp);
        boolean nameChanged = !Objects.equals(data.nickname, currentName);

        data.nickname = currentName;
        data.player = player;

        if (player.con != null) {
            Call.clientPacketReliable(player.con, "adm_mod_begin", "");
        }

        if (data.exists && (ipChanged || nameChanged)) {
            if (ipChanged && player.admin) {
                discordAdminAccessService.deactivateRuntimeAdmin(player, player.uuid());
                if (locale != null) {
                    locale.send("error-ip-changed", args());
                }
            }

            sessionService.updateConnectionData(session, currentIp, currentName);
        }

        if (!data.exists) {
            data.ip = currentIp;
            data.exists = true;
            sessionService.persistPlayer(session);
        }

        playerDisplayService.refresh(session);

        if (player.con != null && player.getInfo() != null && player.getInfo().timesJoined < 5) {
            if (secretsConfig.externalLinks != null && secretsConfig.externalLinks.discordUrl != null) {
                Call.openURI(player.con, secretsConfig.externalLinks.discordUrl);
            }
        }

        long unreadMessages = privateMessageService.countUnread(data.uuid);
        if (unreadMessages > 0 && locale != null) {
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
        if (event == null || event.player == null) return;
        Player player = event.player;

        mapVoteObserverService.unregisterViewing(player.uuid());
        Session session = sessionService.registerLogout(player);
        PlayerData data = session != null ? session.data : null;

        voteService.handleLeave(player);

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
