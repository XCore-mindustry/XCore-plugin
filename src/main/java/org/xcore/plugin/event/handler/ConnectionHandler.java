package org.xcore.plugin.event.handler;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerJoinLeaveV1;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.IdentityDisplayMode;
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

@Slf4j
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
    private final Async async;

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
                             MapVoteObserverService mapVoteObserverService,
                             Async async) {
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
        this.async = async;
    }

    public void onPlayerJoin(PlayerJoin event) {
        if (event == null || event.player == null) return;
        var player = event.player;

        var join = new JoinSnapshot(
                player.uuid(),
                player.ip(),
                player.coloredName(),
                player.admin,
                player.con != null && player.getInfo() != null && player.getInfo().timesJoined < 5,
                player
        );

        if (join.admin()) {
            player.admin(false);
        }

        async.forPlayer(
                player,
                () -> prepareJoin(join),
                (p, prepared) -> completeJoin(p, join, prepared),
                (p, error) -> p.kick("Failed to load player data. Please reconnect.")
        );
    }

    private PreparedJoin prepareJoin(JoinSnapshot join) {
        PlayerData data = sessionService.loadPlayerData(join.uuid());

        String currentIp = join.ip();
        String currentName = join.name();
        boolean ipChanged = !Objects.equals(data.ip, currentIp);
        boolean nameChanged = !Objects.equals(data.nickname, currentName);

        data.nickname = currentName;
        data.player = join.player();

        boolean connectionDataChanged = data.exists && (ipChanged || nameChanged);
        if (connectionDataChanged) {
            sessionService.updateConnectionData(data, currentIp, currentName);
        }

        if (!data.exists) {
            data.ip = currentIp;
            data.exists = true;
            sessionService.persistData(data);
        }

        long unreadMessages = privateMessageService.countUnread(data.uuid);

        return new PreparedJoin(data, ipChanged, unreadMessages);
    }

    private void completeJoin(Player player, JoinSnapshot join, PreparedJoin prepared) {
        boolean admin = join.admin();
        boolean revokeAdmin = admin && prepared.ipChanged();
        if (admin && !revokeAdmin) {
            player.admin(true);
        }

        Session session = sessionService.registerLogin(player, prepared.data());
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

        if (player.con != null) {
            Call.clientPacketReliable(player.con, "adm_mod_begin", "");
        }

        if (revokeAdmin) {
            discordAdminAccessService.deactivateRuntimeAdmin(player, player.uuid());
            if (locale != null) {
                locale.send("error-ip-changed", args());
            }
        }

        playerDisplayService.refresh(session);
        sessionService.markOnline(data, config.server.name);

        if (join.firstJoin() && player.con != null) {
            if (secretsConfig.externalLinks != null && secretsConfig.externalLinks.discordUrl != null) {
                Call.openURI(player.con, secretsConfig.externalLinks.discordUrl);
            }
        }

        if (prepared.unreadMessages() > 0 && locale != null) {
            locale.send("private-message-join-notification", args("count", prepared.unreadMessages()));
        }
        if (data.username != null && !data.username.isEmpty()) {
            Log.info("@ [@] #@ @ joined", player.plainName(), data.username, data.pid, player.uuid());
        }
        else {
            Log.info("@ #@ @ joined", player.plainName(), data.pid, player.uuid());
        }

        // Відправка локалізованого входу відповідно до налаштувань відображення
        broadcastJoin(player, data);

        network.post(new PlayerJoinLeaveV1(
                formatNetworkIdentity(player, data),
                config.server.name,
                true)
        );
    }

    private record JoinSnapshot(
            String uuid,
            String ip,
            String name,
            boolean admin,
            boolean firstJoin,
            Player player
    ) {
    }

    private record PreparedJoin(PlayerData data, boolean ipChanged, long unreadMessages) {
    }

    public void onPlayerLeave(PlayerLeave event) {
        if (event == null || event.player == null) return;
        Player player = event.player;

        mapVoteObserverService.unregisterViewing(player.uuid());
        Session session = sessionService.registerLogout(player);
        PlayerData data = session != null ? session.data : null;

        voteService.handleLeave(player);

        if (data != null) {
            sessionService.markOffline(data);
            if (data.username != null && !data.username.isEmpty()) {
                Log.info("@ [@] #@ @ left", player.plainName(), data.username, data.pid, player.uuid());
            } else {
                Log.info("@ #@ @ left", player.plainName(), data.pid, player.uuid());
            }

            // Відправка локалізованого виходу відповідно до налаштувань відображення
            broadcastLeave(player, data);

            network.post(new PlayerJoinLeaveV1(
                    formatNetworkIdentity(player, data),
                    config.server.name,
                    false)
            );
        }
    }

    // =========================================================================
    // Допоміжні методи форматування ідентифікаторів та відправки
    // =========================================================================

    private void broadcastJoin(Player player, PlayerData data) {
        IdentityDisplayMode mode = resolveEffectiveDisplayMode(data);
        String key = switch (mode) {
            case USERNAME -> "player-joined-username";
            case BOTH -> "player-joined-both";
            case NONE -> "player-joined-none";
            case PID -> "player-joined-pid";
        };

        sessionService.broadcast(key, args(
                "nickname", player.coloredName(),
                "pid", data.pid,
                "username", data.username != null ? data.username : ""
        ));
    }

    private void broadcastLeave(Player player, PlayerData data) {
        IdentityDisplayMode mode = resolveEffectiveDisplayMode(data);
        String key = switch (mode) {
            case USERNAME -> "player-left-username";
            case BOTH -> "player-left-both";
            case NONE -> "player-left-none";
            case PID -> "player-left-pid";
        };

        sessionService.broadcast(key, args(
                "nickname", player.coloredName(),
                "pid", data.pid,
                "username", data.username != null ? data.username : ""
        ));
    }

    /**
     * Визначає реальний режим відображення: якщо юзернейма немає, але вибрано USERNAME або BOTH,
     * робиться автоматичний відкат на PID.
     */
    private IdentityDisplayMode resolveEffectiveDisplayMode(PlayerData data) {
        if (data == null) return IdentityDisplayMode.PID;
        IdentityDisplayMode mode = data.identityDisplayMode != null ? data.identityDisplayMode : IdentityDisplayMode.PID;
        boolean hasUsername = data.username != null && !data.username.isBlank();

        if (!hasUsername && (mode == IdentityDisplayMode.USERNAME || mode == IdentityDisplayMode.BOTH)) {
            return IdentityDisplayMode.PID;
        }
        return mode;
    }

    /**
     * Форматує ім'я для мережевих пакетів (Discord релею тощо)
     */
    private String formatNetworkIdentity(Player player, PlayerData data) {
        IdentityDisplayMode mode = resolveEffectiveDisplayMode(data);
        String plain = player != null ? player.plainName() : (data != null ? data.nickname : "Player");
        if (data == null) return plain;

        return switch (mode) {
            case USERNAME -> plain + " @" + data.username;
            case BOTH -> plain + " @" + data.username + " #" + data.pid;
            case NONE -> plain;
            case PID -> plain + " #" + data.pid;
        };
    }
}