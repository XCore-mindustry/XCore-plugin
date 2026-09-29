package org.xcore.plugin.event.handler;

import arc.util.Time;
import arc.Core;
import arc.Settings;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.game.EventType;
import mindustry.entities.EntityGroup;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerJoinLeaveV1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.service.map.MapVoteObserverService;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.concurrent.Callable;

class ConnectionHandlerTest {

    /**
     * Both legs run inline: the storage phase on the calling thread, the game-thread
     * continuation straight after. This keeps the join assertions synchronous without a
     * sleep, and is the only way to observe the end of the chain.
     */
    private final Async async = new Async(InlineStorageExecutor.create(), Runnable::run);

    private NetServer previousNetServer;
    private Administration admins;
    private Settings previousSettings;
    private EntityGroup<Player> previousPlayers;

    @BeforeEach
    void setUp() {
        previousPlayers = Groups.player;
        // NetServer.connectConfirm calls player.add() before firing PlayerJoin, so the
        // player is already in Groups.player by the time the handler runs. The join
        // continuation relies on that to drop work for players who left in the meantime,
        // so the fixture has to reproduce it.
        Groups.player = new EntityGroup<>(Player.class, false, false);
        previousNetServer = Vars.netServer;
        previousSettings = Core.settings;
        admins = mock(Administration.class);
        NetServer netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;
        Core.settings = mock(Settings.class);
        when(Core.settings.getString("servername", "Server")).thenReturn("Server");
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
        Core.settings = previousSettings;
        Groups.player = previousPlayers;
    }

    @Test
    @DisplayName("onPlayerJoin does not touch Mongo or game state until the storage phase has run")
    void onPlayerJoin_defersEverythingUntilTheStoragePhaseCompletes() {
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        ObserverService observerService = mock(ObserverService.class);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        // A dispatcher that has not come back around yet stands in for a busy tick loop.
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        ConnectionHandler handler = new ConnectionHandler(sessionService, networkService, config,
                new TomlSecretsConfig(), mock(VoteService.class), privateMessageService,
                playerDisplayService, mock(DiscordAdminAccessService.class), observerService,
                mock(MapVoteObserverService.class),
                new Async(org.xcore.plugin.concurrent.InlineStorageExecutor.create(), queued::add));

        Player player = onlinePlayer();
        player.name = "Joiner";
        player.con = new DummyNetConnection("2.2.2.2");
        player.con.uuid = "uuid-async";
        player.con.player = player;

        PlayerData data = new PlayerData("uuid-async", true);
        data.pid = 3;
        Session session = mock(Session.class);
        session.data = data;
        when(session.locale()).thenReturn(mock(Localization.class));
        when(sessionService.loadPlayerData("uuid-async")).thenReturn(data);
        when(sessionService.registerLogin(eq(player), same(data))).thenReturn(session);

        try (MockedStatic<Time> time = Mockito.mockStatic(Time.class);
             MockedStatic<Call> call = Mockito.mockStatic(Call.class)) {
            handler.onPlayerJoin(new EventType.PlayerJoin(player));

            // The storage phase has run, so the data side is done...
            verify(sessionService).loadPlayerData("uuid-async");
            // ...but no session exists yet and no packet has been sent, because the game
            // thread has not run. Registering now would publish a session whose Mongo
            // write has not landed.
            verify(sessionService, never()).registerLogin(any(), any());
            verifyNoInteractions(observerService, playerDisplayService);
            verify(playerDisplayService, never()).refresh(any());
            assertThat(queued).hasSize(1);

            queued.forEach(Runnable::run);

            verify(sessionService).registerLogin(eq(player), same(data));
            verify(observerService).restore(player);
            verify(playerDisplayService).refresh(session);
            verify(sessionService).markOnline(data, "mini-pvp");
            verify(networkService).post(any(PlayerJoinLeaveV1.class));
        }
    }

    @Test
    @DisplayName("onPlayerJoin skips the session entirely when the player left during the load")
    void onPlayerJoin_skipsSessionWhenThePlayerLeftDuringTheLoad() {
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        ObserverService observerService = mock(ObserverService.class);

        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        ConnectionHandler handler = new ConnectionHandler(sessionService, networkService,
                new TomlXcoreConfig(), new TomlSecretsConfig(), mock(VoteService.class),
                privateMessageService, playerDisplayService,
                mock(DiscordAdminAccessService.class), observerService,
                mock(MapVoteObserverService.class),
                new Async(org.xcore.plugin.concurrent.InlineStorageExecutor.create(), queued::add));

        Player player = onlinePlayer();
        player.name = "Quitter";
        player.con = new DummyNetConnection("2.2.2.2");
        player.con.uuid = "uuid-gone";
        player.con.player = player;

        PlayerData data = new PlayerData("uuid-gone", true);
        data.pid = 9;
        when(sessionService.loadPlayerData("uuid-gone")).thenReturn(data);
        when(sessionService.registerLogin(eq(player), same(data))).thenReturn(mock(Session.class));

        try (MockedStatic<Time> time = Mockito.mockStatic(Time.class);
             MockedStatic<Call> call = Mockito.mockStatic(Call.class)) {
            handler.onPlayerJoin(new EventType.PlayerJoin(player));
        }

        // The player disconnects while the Mongo read is in flight.
        when(player.isAdded()).thenReturn(false);
        queued.forEach(Runnable::run);

        // A session registered here would never be removed: onPlayerLeave already ran and
        // registerLogout found nothing to log out.
        verify(sessionService, never()).registerLogin(any(), any());
        verify(sessionService, never()).markOnline(any(), any());
        verifyNoInteractions(observerService, playerDisplayService);
        verify(networkService, never()).post(any());
    }

    @Test
    @DisplayName("onPlayerJoin still records the new ip and nickname for a player who left mid-load")
    void onPlayerJoin_stillWritesJoinDataForAPlayerWhoLeft() {
        SessionService sessionService = mock(SessionService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);

        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        ConnectionHandler handler = new ConnectionHandler(sessionService,
                mock(NetworkService.class), new TomlXcoreConfig(), new TomlSecretsConfig(),
                mock(VoteService.class), privateMessageService, mock(PlayerDisplayService.class),
                mock(DiscordAdminAccessService.class), mock(ObserverService.class),
                mock(MapVoteObserverService.class),
                new Async(org.xcore.plugin.concurrent.InlineStorageExecutor.create(), queued::add));

        Player player = onlinePlayer();
        player.name = "Quitter";
        player.con = new DummyNetConnection("2.2.2.2");
        player.con.uuid = "uuid-gone2";
        player.con.player = player;

        PlayerData data = new PlayerData("uuid-gone2", true);
        data.pid = 11;
        data.ip = "1.1.1.1";
        data.nickname = "OldName";
        when(sessionService.loadPlayerData("uuid-gone2")).thenReturn(data);

        try (MockedStatic<Time> time = Mockito.mockStatic(Time.class);
             MockedStatic<Call> call = Mockito.mockStatic(Call.class)) {
            handler.onPlayerJoin(new EventType.PlayerJoin(player));
        }

        when(player.isAdded()).thenReturn(false);
        queued.forEach(Runnable::run);

        // The write happened in the storage phase and is not rolled back: the address and
        // name really did change. Only the session is skipped.
        String expectedName = player.coloredName();
        verify(sessionService).updateConnectionData(same(data), eq("2.2.2.2"), eq(expectedName));
    }

    @Test
    @DisplayName("onPlayerJoin persists nickname with changed ip and revokes unconfirmed admin")
    void onPlayerJoin_persistsNicknameWithChangedIp_andRevokesUnconfirmedAdmin() {
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        ObserverService observerService = mock(ObserverService.class);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();

        ConnectionHandler handler = new ConnectionHandler(
                sessionService,
                networkService,
                config,
                secretsConfig,
                voteService,
                privateMessageService,
                playerDisplayService,
                discordAdminAccessService,
                observerService,
                mock(MapVoteObserverService.class),
                async
        );

        Player player = onlinePlayer();
        player.name = "[red]Renamed[]";
        player.admin = true;
        player.con = new DummyNetConnection("2.2.2.2");
        player.con.uuid = "uuid-1";
        player.con.usid = "usid-1";
        player.con.player = player;

        Administration.PlayerInfo info = new Administration.PlayerInfo();
        info.timesJoined = 5;
        when(admins.getInfo("uuid-1")).thenReturn(info);
        when(admins.isAdmin("uuid-1", "usid-1")).thenReturn(false);
        when(privateMessageService.countUnread("uuid-1")).thenReturn(0L);

        PlayerData data = new PlayerData("uuid-1", true);
        data.pid = 7;
        data.ip = "1.1.1.1";
        data.nickname = "OldName";
        data.admin = true;
        data.exists = true;

        Session session = mock(Session.class);
        session.data = data;
        Localization localization = mock(Localization.class);
        when(session.locale()).thenReturn(localization);
        when(sessionService.loadPlayerData("uuid-1")).thenReturn(data);
        when(sessionService.registerLogin(eq(player), same(data))).thenReturn(session);

        try (MockedStatic<Time> time = org.mockito.Mockito.mockStatic(Time.class);
             MockedStatic<Call> call = org.mockito.Mockito.mockStatic(Call.class)) {
            handler.onPlayerJoin(new EventType.PlayerJoin(player));
        }

        verify(discordAdminAccessService).deactivateRuntimeAdmin(player, "uuid-1");
        verify(observerService).restore(player);
        verify(sessionService).markOnline(data, "mini-pvp");
        verify(sessionService).updateConnectionData(data, "2.2.2.2", "[#00000000][red]Renamed[]");
        verify(localization).send(eq("error-ip-changed"), anyMap());
        verify(playerDisplayService).refresh(session);
        verify(networkService).post(any(PlayerJoinLeaveV1.class));
        verify(sessionService, never()).persistData(data);
    }

    @Test
    @DisplayName("onPlayerJoin persists nickname when ip is unchanged but nickname changed")
    void onPlayerJoin_persistsNicknameWhenIpUnchangedButNicknameChanged() {
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        ObserverService observerService = mock(ObserverService.class);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();

        ConnectionHandler handler = new ConnectionHandler(
                sessionService,
                networkService,
                config,
                secretsConfig,
                voteService,
                privateMessageService,
                playerDisplayService,
                discordAdminAccessService,
                observerService,
                mock(MapVoteObserverService.class),
                async
        );

        Player player = onlinePlayer();
        player.name = "[blue]NewName[]";
        player.admin = false;
        player.con = new DummyNetConnection("1.1.1.1");
        player.con.uuid = "uuid-1";
        player.con.usid = "usid-1";
        player.con.player = player;

        Administration.PlayerInfo info = new Administration.PlayerInfo();
        info.timesJoined = 5;
        when(admins.getInfo("uuid-1")).thenReturn(info);
        when(privateMessageService.countUnread("uuid-1")).thenReturn(0L);

        PlayerData data = new PlayerData("uuid-1", true);
        data.pid = 8;
        data.ip = "1.1.1.1";
        data.nickname = "OldName";
        data.admin = false;
        data.exists = true;

        Session session = mock(Session.class);
        session.data = data;
        Localization localization = mock(Localization.class);
        when(session.locale()).thenReturn(localization);
        when(sessionService.loadPlayerData("uuid-1")).thenReturn(data);
        when(sessionService.registerLogin(eq(player), same(data))).thenReturn(session);

        try (MockedStatic<Time> time = org.mockito.Mockito.mockStatic(Time.class);
             MockedStatic<Call> call = org.mockito.Mockito.mockStatic(Call.class)) {
            handler.onPlayerJoin(new EventType.PlayerJoin(player));
        }

        verify(observerService).restore(player);
        verify(sessionService).updateConnectionData(data, "1.1.1.1", "[#00000000][blue]NewName[]");
        verify(playerDisplayService).refresh(session);
        verify(discordAdminAccessService, never()).deactivateRuntimeAdmin(any(), any());
    }

    @Test
    void onPlayerLeaveUnregistersMapObserverEvenWithoutPlayerData() {
        SessionService sessionService = mock(SessionService.class);
        MapVoteObserverService mapObserver = mock(MapVoteObserverService.class);
        VoteService voteService = mock(VoteService.class);
        ConnectionHandler handler = new ConnectionHandler(sessionService,
                mock(NetworkService.class), new TomlXcoreConfig(), new TomlSecretsConfig(), voteService,
                mock(PrivateMessageService.class), mock(PlayerDisplayService.class),
                mock(DiscordAdminAccessService.class), mock(ObserverService.class), mapObserver, async);
        Player player = onlinePlayer();
        player.con = new DummyNetConnection("1.1.1.1");
        player.con.uuid = "uuid-left";

        handler.onPlayerLeave(new EventType.PlayerLeave(player));

        verify(mapObserver).unregisterViewing("uuid-left");
        verify(voteService).handleLeave(player);
    }

    @Test
    @DisplayName("onPlayerLeave flags the player as offline")
    void onPlayerLeave_marksPlayerOffline() {
        SessionService sessionService = mock(SessionService.class);
        NetworkService networkService = mock(NetworkService.class);
        ConnectionHandler handler = new ConnectionHandler(sessionService,
                networkService, new TomlXcoreConfig(), new TomlSecretsConfig(),
                mock(VoteService.class), mock(PrivateMessageService.class),
                mock(PlayerDisplayService.class), mock(DiscordAdminAccessService.class),
                mock(ObserverService.class), mock(MapVoteObserverService.class), async);

        Player player = onlinePlayer();
        player.name = "Leaver";
        player.con = new DummyNetConnection("1.1.1.1");
        player.con.uuid = "uuid-left";

        PlayerData data = new PlayerData("uuid-left", true);
        data.pid = 42;
        data.online = true;
        Session session = mock(Session.class);
        session.data = data;
        when(sessionService.registerLogout(player)).thenReturn(session);

        handler.onPlayerLeave(new EventType.PlayerLeave(player));

        verify(sessionService).markOffline(data);
        verify(networkService).post(any(PlayerJoinLeaveV1.class));
    }

    @Test
    @DisplayName("onPlayerJoin kicks the player when the storage phase fails")
    void onPlayerJoin_kicksThePlayerWhenStorageFails() {
        SessionService sessionService = mock(SessionService.class);
        ConnectionHandler handler = new ConnectionHandler(sessionService,
                mock(NetworkService.class), new TomlXcoreConfig(), new TomlSecretsConfig(),
                mock(VoteService.class), mock(PrivateMessageService.class),
                mock(PlayerDisplayService.class), mock(DiscordAdminAccessService.class),
                mock(ObserverService.class), mock(MapVoteObserverService.class),
                new Async(failingStorage(), Runnable::run));

        Player player = onlinePlayer();
        player.name = "Unlucky";
        player.con = new DummyNetConnection("1.1.1.1");
        player.con.uuid = "uuid-mongo-down";
        player.con.player = player;

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        // The player is already in the world at this point, so leaving them there with no
        // session is the failure: session-backed guards read null and pass them through,
        // and onPlayerLeave finds nothing to clear.
        verify(sessionService, never()).registerLogin(any(), any());
        // The player is a mock, so the real kick body does not run; what matters is that
        // it was asked to leave.
        verify(player).kick(org.mockito.ArgumentMatchers.anyString());
    }

    /** A storage executor whose task fails, the way an unreachable Mongo would. */
    private static org.xcore.plugin.concurrent.StorageExecutor failingStorage() {
        org.xcore.plugin.concurrent.StorageExecutor executor =
                Mockito.mock(org.xcore.plugin.concurrent.StorageExecutor.class);
        when(executor.supply(Mockito.<Callable<Object>>any()))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                        new java.util.concurrent.TimeoutException("mongo unreachable")));
        return executor;
    }

    /**
     * A player that reports itself as added, which is the state NetServer.connectConfirm has
     * already put it in by the time it fires PlayerJoin. The join continuation drops its work
     * for players that are no longer online, so the fixture has to reflect that.
     */
    private static Player onlinePlayer() {
        Player player = Mockito.spy(Player.create());
        when(player.isAdded()).thenReturn(true);
        return player;
    }

    private static final class DummyNetConnection extends NetConnection {

        boolean closed;
        String kickReason;

        private DummyNetConnection(String address) {
            super(address);
            this.lastReceivedClientSnapshot = 0;
        }

        @Override
        public void send(Object object, boolean reliable) {
        }

        @Override
        public void close() {
            closed = true;
        }

        /** Player.kick(String, long) routes here rather than through close(). */
        @Override
        public void kick(String reason, long duration) {
            kickReason = reason;
            closed = true;
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }
}
