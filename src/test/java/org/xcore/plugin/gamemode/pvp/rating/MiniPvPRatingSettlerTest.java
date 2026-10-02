package org.xcore.plugin.gamemode.pvp.rating;

import mindustry.game.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MiniPvPRatingSettlerTest {

    @Test
    @DisplayName("settle skips settlement when match duration is under 30 seconds")
    void settle_skipsWhenTooShort() {
        MiniPvPMatchTracker tracker = mock(MiniPvPMatchTracker.class);
        when(tracker.startedAt()).thenReturn(System.currentTimeMillis() - 10_000L); // 10 seconds ago

        MiniPvPRatingSettler settler = new MiniPvPRatingSettler(
                tracker,
                mock(SessionService.class),
                mock(PlayerDataRepository.class),
                mock(TopMenuCacheService.class),
                mock(PlayerDisplayRefreshService.class),
                mock(GameDataService.class),
                mock(Async.class)
        );

        boolean settled = settler.settle(Team.sharded);
        assertThat(settled).isFalse();
        assertThat(settler.isSettled()).isFalse();
    }

    @Test
    @DisplayName("settle applies rating changes, updates in-memory player data, and executes once")
    void settle_successfulRoundSettlement() {
        MiniPvPMatchTracker tracker = new MiniPvPMatchTracker();
        tracker.startMatch(null);

        long started = System.currentTimeMillis() - 120_000L; // 2 minutes ago
        tracker.setStartedAt(started);
        var p1 = new MiniPvPMatchTracker.ParticipantInfo("p1", "Winner 1", Team.sharded.id, started, 0L);
        var p2 = new MiniPvPMatchTracker.ParticipantInfo("p2", "Winner 2", Team.sharded.id, started, 0L);
        var p3 = new MiniPvPMatchTracker.ParticipantInfo("p3", "Loser 1", Team.crux.id, started, 0L);
        var p4 = new MiniPvPMatchTracker.ParticipantInfo("p4", "Loser 2", Team.crux.id, started, 0L);

        tracker.trackParticipant(p1);
        tracker.trackParticipant(p2);
        tracker.trackParticipant(p3);
        tracker.trackParticipant(p4);

        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository playerRepo = mock(PlayerDataRepository.class);
        TopMenuCacheService topMenuCache = mock(TopMenuCacheService.class);
        PlayerDisplayRefreshService displayRefresh = mock(PlayerDisplayRefreshService.class);
        GameDataService gameDataService = mock(GameDataService.class);
        Async async = mock(Async.class);

        when(playerRepo.updatePvpRatingAndStatsAsync(anyString(), anyInt(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Session s1 = mock(Session.class);
        PlayerData d1 = new PlayerData("p1", true);
        d1.pvpRating = 1000;
        s1.data = d1;
        when(s1.locale()).thenReturn(mock(Localization.class));
        when(sessionService.get("p1")).thenReturn(s1);

        Session s3 = mock(Session.class);
        PlayerData d3 = new PlayerData("p3", true);
        d3.pvpRating = 1000;
        s3.data = d3;
        when(s3.locale()).thenReturn(mock(Localization.class));
        when(sessionService.get("p3")).thenReturn(s3);

        MiniPvPRatingSettler settler = new MiniPvPRatingSettler(
                tracker,
                sessionService,
                playerRepo,
                topMenuCache,
                displayRefresh,
                gameDataService,
                async
        );

        boolean settled = settler.settle(Team.sharded);
        assertThat(settled).isTrue();
        assertThat(settler.isSettled()).isTrue();

        // Winner rating increased
        assertThat(d1.pvpRating).isGreaterThan(1000);
        assertThat(d1.pvpMatches).isEqualTo(1);
        assertThat(d1.pvpWins).isEqualTo(1);

        // Loser rating decreased
        assertThat(d3.pvpRating).isLessThan(1000);
        assertThat(d3.pvpMatches).isEqualTo(1);
        assertThat(d3.pvpWins).isEqualTo(0);

        // Exactly once: second settle call does nothing
        boolean secondSettle = settler.settle(Team.sharded);
        assertThat(secondSettle).isFalse();

        // New round resets settled state
        settler.onNewRound();
        assertThat(settler.isSettled()).isFalse();
    }
}
