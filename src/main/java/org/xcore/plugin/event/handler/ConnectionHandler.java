package org.xcore.plugin.event.handler;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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

        // Snapshot the join-time facts the later steps need. These are cheap reads of live
        // game state, so they belong here on the game thread; everything downstream is
        // either Mongo or a packet.
        var join = new JoinSnapshot(
                player.uuid(),
                player.ip(),
                player.coloredName(),
                player.admin,
                player.con != null && player.getInfo() != null && player.getInfo().timesJoined < 5,
                player
        );

        // The four Mongo round-trips this method used to make - load, connection-data
        // update, first-save and the unread count - now run together on the storage
        // executor, and the session is registered once in a single step on the game thread.
        //
        // forPlayer drops the continuation when the player left while the load was in
        // flight, so a join-then-immediately-quit no longer registers an orphaned session.
        // The data writes still happen, which is correct: the new IP and nickname are real
        // regardless of how long the connection lasted, and markOnline is never reached so
        // there is no online flag for onPlayerLeave to clear.
        async.forPlayer(player, () -> prepareJoin(join), (p, prepared) -> completeJoin(p, join, prepared));
    }

    /**
     * Runs on the storage executor. Loads the player row, applies the join-time changes and
     * writes them back, so the game thread never waits on Mongo and the session is only
     * published once the data is already settled.
     *
     * <p>Safe to touch {@code PlayerData} here: it was loaded by this thread and is not
     * reachable from {@code sessionCache} until the continuation registers it, so no later
     * gameplay mutation can race this phase.
     *
     * <p>{@link JoinSnapshot} carries the player only so the transient
     * {@code PlayerData.player} back-reference can be set before publication, and
     * {@code loadPlayerData} reads its uuid. No live game state is called here.
     */
    private PreparedJoin prepareJoin(JoinSnapshot join) {
        PlayerData data = sessionService.loadPlayerData(join.uuid());

        String currentIp = join.ip();
        String currentName = join.name();
        boolean ipChanged = !Objects.equals(data.ip, currentIp);
        boolean nameChanged = !Objects.equals(data.nickname, currentName);

        // Both are transient/BsonIgnore, so they never reach the repository. data.player is
        // set before publication so the game thread sees it fully initialised.
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

    /** Runs on the game thread. Publishes the session and performs the game-state steps. */
    private void completeJoin(Player player, JoinSnapshot join, PreparedJoin prepared) {
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

        // Revoked here rather than beside the write above: it is game state, and the
        // storage write landing first does not affect what the player is told.
        if (prepared.ipChanged() && player.admin) {
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

    /**
     * The game-thread facts a join needs, read once so the storage phase never touches
     * {@code Player}.
     */
    private record JoinSnapshot(
            String uuid,
            String ip,
            String name,
            boolean admin,
            boolean firstJoin,
            Player player
    ) {
    }

    /** The result of the storage phase: settled data plus the decisions it implies. */
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
