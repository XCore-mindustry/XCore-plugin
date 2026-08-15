package org.xcore.plugin.integration;

import mindustry.gen.Player;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerDisplayRefreshServiceTest {

    @Test
    void refreshPlayerDelegatesForLiveSession() {
        var sessionService = mock(SessionService.class);
        var displayService = mock(PlayerDisplayService.class);
        var refreshService = new PlayerDisplayRefreshService(sessionService, displayService);
        var player = mock(Player.class);
        var session = liveSession(player);
        when(sessionService.get(player)).thenReturn(session);

        assertThat(refreshService.refresh(player)).isTrue();
        verify(displayService).refresh(session);
    }

    @Test
    void refreshPlayerReturnsFalseForMissingOrNonLiveSession() {
        var sessionService = mock(SessionService.class);
        var displayService = mock(PlayerDisplayService.class);
        var refreshService = new PlayerDisplayRefreshService(sessionService, displayService);
        var player = mock(Player.class);
        var incomplete = mock(Session.class);
        incomplete.player = player;
        var lookups = new int[1];
        when(sessionService.get(player)).thenAnswer(invocation -> lookups[0]++ == 0 ? null : incomplete);

        assertThat(refreshService.refresh(player)).isFalse();
        assertThat(refreshService.refresh(player)).isFalse();
        verify(displayService, never()).refresh(incomplete);
    }

    @Test
    void refreshUuidDelegatesForLiveSession() {
        var sessionService = mock(SessionService.class);
        var displayService = mock(PlayerDisplayService.class);
        var refreshService = new PlayerDisplayRefreshService(sessionService, displayService);
        var session = liveSession(mock(Player.class));
        when(sessionService.get("uuid-1")).thenReturn(session);

        assertThat(refreshService.refresh("uuid-1")).isTrue();
        verify(displayService).refresh(session);
    }

    @Test
    void refreshUuidReturnsFalseForMissingSession() {
        var sessionService = mock(SessionService.class);
        var displayService = mock(PlayerDisplayService.class);
        var refreshService = new PlayerDisplayRefreshService(sessionService, displayService);
        when(sessionService.get("missing")).thenReturn(null);

        assertThat(refreshService.refresh("missing")).isFalse();
        verify(displayService, never()).refresh(org.mockito.ArgumentMatchers.any(Session.class));
    }

    @Test
    void refreshAllRefreshesOnlyLiveSessionsAndReturnsCount() {
        var sessionService = mock(SessionService.class);
        var displayService = mock(PlayerDisplayService.class);
        var refreshService = new PlayerDisplayRefreshService(sessionService, displayService);
        var live = liveSession(mock(Player.class));
        var noPlayer = mock(Session.class);
        noPlayer.data = live.data;
        var noData = mock(Session.class);
        noData.player = live.player;
        var sessions = new ArrayList<Session>();
        sessions.addAll(List.of(live, noPlayer, noData));
        sessions.add(null);
        when(sessionService.getAllCachedSnapshot()).thenReturn(sessions);

        assertThat(refreshService.refreshAll()).isEqualTo(1);
        verify(displayService).refresh(live);
        verify(displayService, never()).refresh(noPlayer);
        verify(displayService, never()).refresh(noData);
    }

    private static Session liveSession(Player player) {
        var session = mock(Session.class);
        session.player = player;
        session.data = new org.xcore.plugin.model.PlayerData();
        return session;
    }
}
