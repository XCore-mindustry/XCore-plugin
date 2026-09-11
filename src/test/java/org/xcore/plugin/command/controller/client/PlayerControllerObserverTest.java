package org.xcore.plugin.command.controller.client;

import mindustry.game.Team;
import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionFactory;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.network.RedisObserverStateStore;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.plugin.ui.menu.TopMenu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PlayerControllerObserverTest {

    @Test
    @DisplayName("observer command moves player into observer team instead of derelict")
    void observerCommand_movesPlayerIntoObserverTeamInsteadOfDerelict() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        ObserverService observerService = new ObserverService(sessionService, mock(RedisObserverStateStore.class));
        PlayerController controller = new PlayerController(
                mock(PlayerDataRepository.class),
                sessionService,
                observerService,
                mock(PlayerMenu.class),
                mock(TopMenu.class)
        );

        Player player = Player.create();
        player.con = mock(mindustry.net.NetConnection.class);
        player.con.uuid = "uuid-1";
        player.team(Team.sharded);

        Session session = mock(Session.class);
        Localization localization = mock(Localization.class);
        session.player = player;
        session.data = new PlayerData("uuid-1", true);

        when(session.observing()).thenReturn(false);
        when(session.locale()).thenReturn(localization);

        sessionService.update(session);

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(player);

        controller.observer(sender);

        verify(session).beginObserving(Team.sharded);
        verify(localization).send("commands-observer-success");
        assertThat(player.team()).isNotEqualTo(Team.derelict);
        assertThat(player.team().id).isEqualTo(255);
    }

    @Test
    @DisplayName("observer command exits observer mode when player is already observing")
    void observerCommand_exitsObserverModeWhenPlayerIsAlreadyObserving() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);
        PlayerController controller = new PlayerController(
                mock(PlayerDataRepository.class),
                sessionService,
                observerService,
                mock(PlayerMenu.class),
                mock(TopMenu.class)
        );

        Player player = Player.create();
        player.con = mock(mindustry.net.NetConnection.class);
        player.con.uuid = "uuid-1";
        player.team(Team.get(255));

        Session session = mock(Session.class);
        Localization localization = mock(Localization.class);
        session.player = player;
        session.data = new PlayerData("uuid-1", true);

        when(session.observing()).thenReturn(true);
        when(session.endObserving()).thenReturn(Team.sharded);
        when(session.locale()).thenReturn(localization);

        sessionService.update(session);

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(player);

        controller.observer(sender);

        verify(session).endObserving();
        verify(observerStateStore).deleteAsync("uuid-1");
        verify(localization).send("commands-observer-exit-success");
        assertThat(player.team()).isEqualTo(Team.sharded);
    }

    @Test
    @DisplayName("set-team clears observer state before moving spectator to real team")
    void setTeam_clearsObserverStateBeforeMovingSpectatorToRealTeam() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);
        PlayerController controller = new PlayerController(
                mock(PlayerDataRepository.class),
                sessionService,
                observerService,
                mock(PlayerMenu.class),
                mock(TopMenu.class)
        );

        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");

        Session session = mock(Session.class);
        session.player = player;
        session.data = new PlayerData("uuid-1", true);
        when(session.observing()).thenReturn(true);

        sessionService.update(session);

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(player);

        controller.setTeam(sender, Team.crux.id, -1);

        verify(session).endObserving();
        verify(observerStateStore).deleteAsync("uuid-1");
        verify(player).clearUnit();
        verify(player).team(Team.crux);
    }

    @Test
    @DisplayName("set-team keeps observer state when assigning observer team")
    void setTeam_keepsObserverStateWhenAssigningObserverTeam() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);
        PlayerController controller = new PlayerController(
                mock(PlayerDataRepository.class),
                sessionService,
                observerService,
                mock(PlayerMenu.class),
                mock(TopMenu.class)
        );

        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("uuid-1");

        Session session = mock(Session.class);
        session.player = player;
        session.data = new PlayerData("uuid-1", true);
        when(session.observing()).thenReturn(true);

        sessionService.update(session);

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(player);

        controller.setTeam(sender, 255, -1);

        verify(session, never()).endObserving();
        verify(observerStateStore, never()).delete("uuid-1");
        verify(player).clearUnit();
        verify(player).team(Team.get(255));
    }

    @Test
    @DisplayName("player command fast-path serves self and online players without database")
    void player_servesSelfAndOnlinePlayersWithoutDatabase() {
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        SessionService sessionService = new SessionService(mock(SessionFactory.class), repository);
        ObserverService observerService = new ObserverService(sessionService, mock(RedisObserverStateStore.class));
        PlayerMenu menu = mock(PlayerMenu.class);
        PlayerController controller = new PlayerController(
                repository,
                sessionService,
                observerService,
                menu,
                mock(TopMenu.class)
        );

        Player selfPlayer = mock(Player.class);
        when(selfPlayer.uuid()).thenReturn("uuid-self");

        Session selfSession = mock(Session.class);
        selfSession.player = selfPlayer;
        selfSession.data = new PlayerData("uuid-self", true);
        selfSession.data.pid = 1;
        sessionService.update(selfSession);

        Player otherPlayer = mock(Player.class);
        when(otherPlayer.uuid()).thenReturn("uuid-other");
        Session otherSession = mock(Session.class);
        otherSession.player = otherPlayer;
        otherSession.data = new PlayerData("uuid-other", true);
        otherSession.data.pid = 2;
        sessionService.update(otherSession);

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(selfPlayer);

        // Self lookup (-1)
        controller.player(sender, -1);
        verify(menu).player("uuid-self", selfSession.data);

        // Online lookup by PID (2)
        controller.player(sender, 2);
        verify(menu).player("uuid-self", otherSession.data);

        // No database calls performed
        verify(repository, never()).findByPid(anyInt());
        verify(repository, never()).findByPidAsync(anyInt());
    }

    @Test
    @DisplayName("player command offline target queries async repository and opens menu")
    void player_offlineTargetQueriesAsyncRepositoryAndOpensMenu() {
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerData offlineData = new PlayerData("uuid-offline", true);
        offlineData.pid = 99;

        when(repository.findByPidAsync(99))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(offlineData));

        SessionService sessionService = new SessionService(mock(SessionFactory.class), repository);
        ObserverService observerService = new ObserverService(sessionService, mock(RedisObserverStateStore.class));
        PlayerMenu menu = mock(PlayerMenu.class);
        PlayerController controller = new PlayerController(
                repository,
                sessionService,
                observerService,
                menu,
                mock(TopMenu.class)
        );

        Player selfPlayer = mock(Player.class);
        when(selfPlayer.uuid()).thenReturn("uuid-self");

        Session selfSession = mock(Session.class);
        selfSession.player = selfPlayer;
        selfSession.data = new PlayerData("uuid-self", true);
        selfSession.data.pid = 1;
        sessionService.update(selfSession);

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(selfPlayer);

        controller.player(sender, 99);

        verify(repository).findByPidAsync(99);
        verify(repository, never()).findByPid(99);
        verify(menu).player("uuid-self", offlineData);
    }
}
