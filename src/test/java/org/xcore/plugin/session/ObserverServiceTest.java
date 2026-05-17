package org.xcore.plugin.session;

import mindustry.game.Team;
import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.network.RedisObserverStateStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ObserverServiceTest {

    @Test
    @DisplayName("isObserving player returns true when cached session is observing")
    void isObserving_playerReturnsTrueWhenCachedSessionIsObserving() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        ObserverService observerService = new ObserverService(sessionService);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);

        when(player.uuid()).thenReturn("uuid-1");
        when(session.observing()).thenReturn(true);
        sessionService.update(session);

        boolean observing = observerService.isObserving(player);

        assertThat(observing).isTrue();
    }

    @Test
    @DisplayName("isObserving player returns false when player is derelict without observing session")
    void isObserving_playerReturnsFalseWhenPlayerIsDerelictWithoutObservingSession() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        ObserverService observerService = new ObserverService(sessionService);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);

        when(player.uuid()).thenReturn("uuid-1");
        when(player.team()).thenReturn(Team.derelict);
        when(session.observing()).thenReturn(false);
        sessionService.update(session);

        boolean observing = observerService.isObserving(player);

        assertThat(observing).isFalse();
    }

    @Test
    @DisplayName("enter marks session observing, stores return team, and derelicts player")
    void enter_marksSessionObservingStoresReturnTeamAndDerelictsPlayer() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);
        session.player = player;

        when(session.observing()).thenReturn(false);
        when(player.team()).thenReturn(Team.sharded);

        boolean changed = observerService.enter(session);

        assertThat(changed).isTrue();
        verify(session).beginObserving(Team.sharded);
        verify(observerStateStore).put("uuid-1", Team.sharded);
        verify(player).clearUnit();
        verify(player).team(ObserverService.OBSERVER_TEAM);
    }

    @Test
    @DisplayName("enter returns false when session already observing and preserves stored observer state")
    void enter_returnsFalseWhenSessionAlreadyObservingAndPreservesStoredObserverState() {
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class)), observerStateStore);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);
        session.player = player;

        when(session.observing()).thenReturn(true);
        when(session.observerReturnTeam()).thenReturn(Team.crux);

        boolean changed = observerService.enter(session);

        assertThat(changed).isFalse();
        verify(session, never()).beginObserving(any());
        verify(observerStateStore).put("uuid-1", Team.crux);
        verify(player).clearUnit();
        verify(player).team(ObserverService.OBSERVER_TEAM);
    }

    @Test
    @DisplayName("enter stores null return team when player already belongs to derelict")
    void enter_storesNullReturnTeamWhenPlayerAlreadyBelongsToDerelict() {
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class)), observerStateStore);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);
        session.player = player;

        when(session.observing()).thenReturn(false);
        when(player.team()).thenReturn(Team.derelict);

        boolean changed = observerService.enter(session);

        assertThat(changed).isTrue();
        verify(session).beginObserving(null);
        verify(observerStateStore).put("uuid-1", null);
        verify(player).clearUnit();
        verify(player).team(ObserverService.OBSERVER_TEAM);
    }

    @Test
    @DisplayName("exit restores prior team and clears observer state")
    void exit_restoresPriorTeamAndClearsObserverState() {
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class)), observerStateStore);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);
        session.player = player;

        when(session.observing()).thenReturn(true);
        when(session.endObserving()).thenReturn(Team.crux);

        Team restored = observerService.exit(session);

        assertThat(restored).isEqualTo(Team.crux);
        verify(observerStateStore).delete("uuid-1");
        verify(player).team(Team.crux);
    }

    @Test
    @DisplayName("exit returns null and does not change player team when session is not observing")
    void exit_returnsNullAndDoesNotChangePlayerTeamWhenSessionIsNotObserving() {
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class)), observerStateStore);
        Session session = mock(Session.class);
        Player player = mock(Player.class);
        session.player = player;

        when(session.observing()).thenReturn(false);

        Team restored = observerService.exit(session);

        assertThat(restored).isNull();
        verify(session, never()).endObserving();
        verify(observerStateStore, never()).delete(any());
        verifyNoInteractions(player);
    }

    @Test
    @DisplayName("restore reapplies derelict observer state for observing player")
    void restore_reappliesDerelictObserverStateForObservingPlayer() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);
        Session session = mock(Session.class);
        session.player = mock(Player.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);

        when(player.uuid()).thenReturn("uuid-1");
        when(session.observing()).thenReturn(true);
        sessionService.update(session);

        observerService.restore(player);

        verify(observerStateStore, never()).get(any());
        verify(player).clearUnit();
        verify(player).team(ObserverService.OBSERVER_TEAM);
    }

    @Test
    @DisplayName("restore loads observer state from Redis when session is not yet observing")
    void restore_loadsObserverStateFromRedisWhenSessionIsNotYetObserving() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);

        when(player.uuid()).thenReturn("uuid-1");
        when(session.observing()).thenReturn(false);
        when(observerStateStore.get("uuid-1")).thenReturn(new RedisObserverStateStore.CachedObserverState(Team.crux.id, System.currentTimeMillis()));
        when(observerStateStore.resolveReturnTeam(any())).thenReturn(Team.crux);
        sessionService.update(session);

        observerService.restore(player);

        verify(session).beginObserving(Team.crux);
        verify(player).clearUnit();
        verify(player).team(ObserverService.OBSERVER_TEAM);
    }

    @Test
    @DisplayName("restore does nothing when cached observer state is absent")
    void restore_doesNothingWhenCachedObserverStateIsAbsent() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);
        Player player = mock(Player.class);

        when(player.uuid()).thenReturn("uuid-1");
        when(session.observing()).thenReturn(false);
        when(observerStateStore.get("uuid-1")).thenReturn(null);
        sessionService.update(session);

        observerService.restore(player);

        verify(session, never()).beginObserving(any());
        verify(player, never()).clearUnit();
        verify(player, never()).team(ObserverService.OBSERVER_TEAM);
    }

    @Test
    @DisplayName("resetObserverState clears session observer flag and cached restore state")
    void resetObserverState_clearsSessionObserverFlagAndCachedRestoreState() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);
        Session session = mock(Session.class);
        session.data = new org.xcore.plugin.model.PlayerData("uuid-1", true);

        when(session.observing()).thenReturn(true);
        sessionService.update(session);

        observerService.resetObserverState("uuid-1");

        verify(session).endObserving();
        verify(observerStateStore).delete("uuid-1");
    }

    @Test
    @DisplayName("resetObserverState clears cached restore state without session")
    void resetObserverState_clearsCachedRestoreStateWithoutSession() {
        SessionService sessionService = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);
        ObserverService observerService = new ObserverService(sessionService, observerStateStore);

        observerService.resetObserverState("uuid-1");

        verify(observerStateStore).delete("uuid-1");
    }
}
