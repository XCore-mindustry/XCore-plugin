package org.xcore.plugin.event.handler;

import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerJoinLeaveV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteService;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.MockPlayer;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

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

@WithHeadlessWorld
class ConnectionHandlerTest {

    /**
     * Both legs run inline: the storage phase on the calling thread, the game-thread
     * continuation straight after. This keeps the join assertions synchronous without a
     * sleep, and is the only way to observe the end of the chain.
     */
    private final Async async = new Async(InlineStorageExecutor.create(), Runnable::run);

    private HeadlessWorld world;
    private Administration admins;

    @BeforeEach
    void setUp(HeadlessWorld world) {
        this.world = world;
        admins = mock(Administration.class);
        world.netServer().admins = admins;
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

        MockPlayer client = world.addPlayer(b -> b.name("Joiner").address("2.2.2.2").uuid("uuid-async"));
        Player player = client.player();

        PlayerData data = new PlayerData("uuid-async", true);
        data.pid = 3;
        Session session = mock(Session.class);
        session.data = data;
        when(session.locale()).thenReturn(mock(Localization.class));
        when(sessionService.loadPlayerData("uuid-async")).thenReturn(data);
        when(sessionService.registerLogin(eq(player), same(data))).thenReturn(session);

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        // The storage phase has run, so the data side is done...
        verify(sessionService).loadPlayerData("uuid-async");
        // ...but no session exists yet and no packet has been sent, because the game
        // thread has not run. Registering now would publish a session whose Mongo
        // write has not landed.
        verify(sessionService, never()).registerLogin(any(), any());
        verifyNoInteractions(observerService, playerDisplayService);
        verify(playerDisplayService, never()).refresh(any());
        assertThat(client.sentPackets()).isEmpty();
        assertThat(queued).hasSize(1);

        queued.forEach(Runnable::run);

        verify(sessionService).registerLogin(eq(player), same(data));
        verify(observerService).restore(player);
        verify(playerDisplayService).refresh(session);
        verify(sessionService).markOnline(data, "mini-pvp");
        verify(networkService).post(any(PlayerJoinLeaveV1.class));
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

        MockPlayer client = world.addPlayer(b -> b.name("Quitter").address("2.2.2.2").uuid("uuid-gone"));
        Player player = client.player();

        PlayerData data = new PlayerData("uuid-gone", true);
        data.pid = 9;
        when(sessionService.loadPlayerData("uuid-gone")).thenReturn(data);
        when(sessionService.registerLogin(eq(player), same(data))).thenReturn(mock(Session.class));

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        // The player disconnects while the Mongo read is in flight.
        world.removePlayer(client);
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

        MockPlayer client = world.addPlayer(b -> b.name("Quitter").address("2.2.2.2").uuid("uuid-gone2"));
        Player player = client.player();

        PlayerData data = new PlayerData("uuid-gone2", true);
        data.pid = 11;
        data.ip = "1.1.1.1";
        data.nickname = "OldName";
        when(sessionService.loadPlayerData("uuid-gone2")).thenReturn(data);

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        world.removePlayer(client);
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

        MockPlayer client = world.addPlayer(b -> b.name("[red]Renamed[]").admin(true).address("2.2.2.2").uuid("uuid-1").usid("usid-1"));
        Player player = client.player();

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

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

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

        MockPlayer client = world.addPlayer(b -> b.name("[blue]NewName[]").admin(false).address("1.1.1.1").uuid("uuid-1").usid("usid-1"));
        Player player = client.player();

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

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

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
        MockPlayer client = world.addPlayer(b -> b.address("1.1.1.1").uuid("uuid-left"));
        Player player = client.player();

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

        MockPlayer client = world.addPlayer(b -> b.name("Leaver").address("1.1.1.1").uuid("uuid-left"));
        Player player = client.player();

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
    @DisplayName("an admin's runtime flag is cleared before the storage phase runs")
    void onPlayerJoin_quarantinesAdminUntilTheIpIsChecked() {
        SessionService sessionService = mock(SessionService.class);
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        ConnectionHandler handler = new ConnectionHandler(sessionService, mock(NetworkService.class),
                new TomlXcoreConfig(), new TomlSecretsConfig(), mock(VoteService.class),
                mock(PrivateMessageService.class), mock(PlayerDisplayService.class),
                mock(DiscordAdminAccessService.class), mock(ObserverService.class),
                mock(MapVoteObserverService.class),
                new Async(org.xcore.plugin.concurrent.InlineStorageExecutor.create(), queued::add));

        MockPlayer client = world.addPlayer(b -> b.name("Boss").admin(true).address("2.2.2.2").uuid("uuid-admin"));
        Player player = client.player();
        when(sessionService.loadPlayerData("uuid-admin")).thenReturn(storedData("uuid-admin", "1.1.1.1"));

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        // The storage phase has already run, but the game thread has not. Every admin
        // permission check reads player.admin, so if it is still true here, the player can
        // act as admin for as long as Mongo takes.
        assertThat(player.admin).as("cleared while the restore is pending").isFalse();
        assertThat(queued).as("the restore is still pending").hasSize(1);
    }

    @Test
    @DisplayName("an admin from the same ip gets the flag back")
    void onPlayerJoin_restoresAdminWhenTheIpIsUnchanged() {
        SessionService sessionService = mock(SessionService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        ConnectionHandler handler = new ConnectionHandler(sessionService, mock(NetworkService.class),
                new TomlXcoreConfig(), new TomlSecretsConfig(), mock(VoteService.class),
                privateMessageService, mock(PlayerDisplayService.class),
                mock(DiscordAdminAccessService.class), mock(ObserverService.class),
                mock(MapVoteObserverService.class), async);

        MockPlayer client = world.addPlayer(b -> b.name("Boss").admin(true).address("1.1.1.1").uuid("uuid-admin"));
        Player player = client.player();

        PlayerData data = new PlayerData("uuid-admin", true);
        data.pid = 9;
        data.ip = "1.1.1.1";
        data.nickname = "Boss";
        data.exists = true;
        Session session = mock(Session.class);
        session.data = data;
        when(session.locale()).thenReturn(mock(Localization.class));
        when(sessionService.loadPlayerData("uuid-admin")).thenReturn(data);
        when(sessionService.registerLogin(eq(player), same(data))).thenReturn(session);

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        assertThat(player.admin).isTrue();
    }

    @Test
    @DisplayName("an admin from a new ip never gets the flag back")
    void onPlayerJoin_doesNotRestoreAdminWhenTheIpChanged() {
        SessionService sessionService = mock(SessionService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);
        ConnectionHandler handler = new ConnectionHandler(sessionService, mock(NetworkService.class),
                new TomlXcoreConfig(), new TomlSecretsConfig(), mock(VoteService.class),
                mock(PrivateMessageService.class), mock(PlayerDisplayService.class),
                discordAdminAccessService, mock(ObserverService.class),
                mock(MapVoteObserverService.class), async);

        MockPlayer client = world.addPlayer(b -> b.name("Boss").admin(true).address("9.9.9.9").uuid("uuid-admin"));
        Player player = client.player();

        PlayerData data = new PlayerData("uuid-admin", true);
        data.pid = 9;
        data.ip = "1.1.1.1";
        data.nickname = "Boss";
        data.exists = true;
        Session session = mock(Session.class);
        session.data = data;
        when(session.locale()).thenReturn(mock(Localization.class));
        when(sessionService.loadPlayerData("uuid-admin")).thenReturn(data);
        when(sessionService.registerLogin(eq(player), same(data))).thenReturn(session);

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        assertThat(player.admin).isFalse();
        verify(discordAdminAccessService).deactivateRuntimeAdmin(player, "uuid-admin");
    }

    @Test
    @DisplayName("a non-admin never has the flag touched")
    void onPlayerJoin_leavesNonAdminFlagAlone() {
        SessionService sessionService = mock(SessionService.class);
        ConnectionHandler handler = new ConnectionHandler(sessionService, mock(NetworkService.class),
                new TomlXcoreConfig(), new TomlSecretsConfig(), mock(VoteService.class),
                mock(PrivateMessageService.class), mock(PlayerDisplayService.class),
                mock(DiscordAdminAccessService.class), mock(ObserverService.class),
                mock(MapVoteObserverService.class), async);

        MockPlayer client = world.addPlayer(b -> b.name("Rando").address("1.1.1.1").uuid("uuid-plain"));
        Player player = client.player();
        when(sessionService.loadPlayerData("uuid-plain")).thenReturn(storedData("uuid-plain", "1.1.1.1"));

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        assertThat(player.admin).isFalse();
    }

    /** A stored row with a known IP, so the join has something to compare against. */
    private static PlayerData storedData(String uuid, String ip) {
        PlayerData data = new PlayerData(uuid, true);
        data.pid = 5;
        data.ip = ip;
        data.nickname = "Someone";
        data.exists = true;
        return data;
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

        MockPlayer client = world.addPlayer(b -> b.name("Unlucky").address("1.1.1.1").uuid("uuid-mongo-down"));
        Player player = client.player();

        handler.onPlayerJoin(new EventType.PlayerJoin(player));

        // The player is already in the world at this point, so leaving them there with no
        // session is the failure: session-backed guards read null and pass them through,
        // and onPlayerLeave finds nothing to clear.
        verify(sessionService, never()).registerLogin(any(), any());
        assertThat(client.isKicked()).isTrue();
    }

    @Test
    void casesJoinPreparationRunsBeforeSessionAndDeliveryRunsAfterRegistration() throws Exception {
        var loop = new org.xcore.plugin.cases.CasesTestLoop();
        SessionService sessions = mock(SessionService.class);
        var notices = mock(org.xcore.plugin.cases.CaseNoticeService.class);
        var recent = mock(org.xcore.plugin.cases.RecentPlayers.class);
        var notice = new org.xcore.plugin.cases.CaseNotice("n", "case-player", "report_received",
                java.util.Map.of("number", "57"), null, null);
        var prepared = java.util.List.of(notice);
        when(notices.prepareJoin("case-player")).thenReturn(prepared);
        var handler = new ConnectionHandler(sessions, mock(NetworkService.class), new TomlXcoreConfig(),
                new TomlSecretsConfig(), mock(VoteService.class), mock(PrivateMessageService.class),
                mock(PlayerDisplayService.class), mock(DiscordAdminAccessService.class), mock(ObserverService.class),
                mock(MapVoteObserverService.class), loop.async, null, notices, recent);
        MockPlayer client = world.addPlayer(b -> b.name("Case Player").address("1.1.1.1").uuid("case-player"));
        Player player = client.player();
        var data = new PlayerData("case-player", true);
        data.pid = 5;
        var session = mock(Session.class);
        session.data = data;
        session.player = player;
        when(session.locale()).thenReturn(mock(Localization.class));
        when(sessions.loadPlayerData("case-player")).thenReturn(data);
        when(sessions.registerLogin(player, data)).thenReturn(session);
        handler.onPlayerJoin(new EventType.PlayerJoin(player));
        loop.worker();
        verify(notices).prepareJoin("case-player");
        verify(notices, never()).completeJoin(any(), any());
        verify(sessions, never()).registerLogin(any(), any());
        loop.main.runTurn();
        var order = Mockito.inOrder(sessions, recent, notices);
        order.verify(sessions).registerLogin(player, data);
        order.verify(recent).joined("case-player");
        order.verify(notices).completeJoin(session, prepared);
    }

    @Test
    void casesLeaveSnapshotsStaffBeforeSessionRemoval() {
        SessionService sessions = mock(SessionService.class);
        var recent = mock(org.xcore.plugin.cases.RecentPlayers.class);
        var handler = new ConnectionHandler(sessions, mock(NetworkService.class), new TomlXcoreConfig(),
                new TomlSecretsConfig(), mock(VoteService.class), mock(PrivateMessageService.class),
                mock(PlayerDisplayService.class), mock(DiscordAdminAccessService.class), mock(ObserverService.class),
                mock(MapVoteObserverService.class), async, null, mock(org.xcore.plugin.cases.CaseNoticeService.class), recent);
        MockPlayer client = world.addPlayer(b -> b.address("1.1.1.1").uuid("leaving").admin(true));
        Player player = client.player();
        var session = mock(Session.class);
        session.player = player;
        session.data = new PlayerData("leaving", true);
        session.permissions = new org.xcore.plugin.permission.PermissionService();
        when(sessions.get(player)).thenReturn(session);
        handler.onPlayerLeave(new EventType.PlayerLeave(player));
        var order = Mockito.inOrder(recent, sessions);
        order.verify(recent).left(session, true);
        order.verify(sessions).registerLogout(player);
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

}
