package org.xcore.plugin.gamemode.pvp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.LeaderboardService;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MiniPvPRoundStateTest {

    @Test
    @DisplayName("clearRoundState clears stale observer restore state for defeated players")
    void clearRoundState_clearsStaleObserverRestoreStateForDefeatedPlayers() {
        ObserverService observerService = mock(ObserverService.class);
        MiniPvP miniPvP = new MiniPvP(
                mock(Config.class),
                mock(SessionService.class),
                mock(PlayerDataRepository.class),
                mock(LeaderboardService.class),
                mock(TopMenuCacheService.class),
                observerService
        );

        miniPvP.defeatedPlayers.add("uuid-1");
        miniPvP.defeatedPlayers.add("uuid-2");

        miniPvP.clearRoundState();

        verify(observerService).resetObserverState("uuid-1");
        verify(observerService).resetObserverState("uuid-2");
        assertThat(miniPvP.defeatedPlayers).isEmpty();
    }
}
