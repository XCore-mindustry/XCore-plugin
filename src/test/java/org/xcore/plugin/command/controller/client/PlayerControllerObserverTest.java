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
        verify(observerStateStore).delete("uuid-1");
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
}
