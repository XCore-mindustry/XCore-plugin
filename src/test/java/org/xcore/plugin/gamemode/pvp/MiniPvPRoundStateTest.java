package org.xcore.plugin.gamemode.pvp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.Async;
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
                mock(TomlXcoreConfig.class),
                mock(SessionService.class),
                mock(PlayerDataRepository.class),
                mock(LeaderboardService.class),
                mock(TopMenuCacheService.class),
                observerService,
                mock(Async.class)
        );

        miniPvP.defeatedPlayers.add("uuid-1");
        miniPvP.defeatedPlayers.add("uuid-2");
        miniPvP.roundHadMultipleTeams = true;

        miniPvP.clearRoundState();

        verify(observerService).resetObserverState("uuid-1");
        verify(observerService).resetObserverState("uuid-2");
        assertThat(miniPvP.defeatedPlayers).isEmpty();
        assertThat(miniPvP.roundHadMultipleTeams).isFalse();
    }

    @Test
    @DisplayName("checkPvPGameOver ignores non-mini-pvp or when roundHadMultipleTeams is false")
    void checkPvPGameOver_ignoresWhenNotReady() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        MiniPvP miniPvP = new MiniPvP(
                config,
                mock(SessionService.class),
                mock(PlayerDataRepository.class),
                mock(LeaderboardService.class),
                mock(TopMenuCacheService.class),
                mock(ObserverService.class),
                mock(Async.class)
        );

        // roundHadMultipleTeams is false
        miniPvP.checkPvPGameOver();
        assertThat(miniPvP.roundHadMultipleTeams).isFalse();

        // different server
        config.server.name = "attack";
        miniPvP.roundHadMultipleTeams = true;
        miniPvP.checkPvPGameOver();
        assertThat(config.server.name).isEqualTo("attack");
    }

    @Test
    @DisplayName("countActivePlayers ignores null, derelict, and observer team")
    void countActivePlayers_ignoresInvalidTeams() {
        ObserverService observerService = mock(ObserverService.class);
        MiniPvP miniPvP = new MiniPvP(
                mock(TomlXcoreConfig.class),
                mock(SessionService.class),
                mock(PlayerDataRepository.class),
                mock(LeaderboardService.class),
                mock(TopMenuCacheService.class),
                observerService,
                mock(Async.class)
        );

        assertThat(miniPvP.countActivePlayers(null)).isEqualTo(0);
        assertThat(miniPvP.countActivePlayers(mindustry.game.Team.derelict)).isEqualTo(0);
    }
}
