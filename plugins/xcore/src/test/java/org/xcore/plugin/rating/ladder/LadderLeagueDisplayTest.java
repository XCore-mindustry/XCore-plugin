package org.xcore.plugin.rating.ladder;

import mindustry.gen.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.RatingPolicy;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LadderLeagueDisplayTest {
    private InMemoryLadderStore store;
    private Ladder ladder;
    private PlayerDisplayRefreshService refreshService;
    private CountDownLatch refreshed;
    private LadderLeagueDisplay display;

    @BeforeEach
    void setUp() {
        store = spy(new InMemoryLadderStore());
        ladder = new LadderService(store, new InMemoryIdempotencyLedger())
                .register(new LadderDefinition("duel", "duel-name", RatingPolicy.teamEloV1()));
        refreshService = mock(PlayerDisplayRefreshService.class);
        refreshed = new CountDownLatch(1);
        when(refreshService.refresh(anyString())).thenAnswer(_ -> {
            refreshed.countDown();
            return true;
        });
        display = new LadderLeagueDisplay("duel-league", 10, ladder, refreshService,
                new Async(new StorageExecutor(2), Runnable::run));
    }

    private static Player player(String uuid) {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn(uuid);
        return player;
    }

    @Test
    @DisplayName("resolve never touches storage and shows the starting league until the standing is loaded")
    void resolve_usesCacheOnly() {
        store.put(new LadderStanding("duel", 1, "p1", 1650, 1650, 30, 20, Map.of()));

        assertThat(display.resolve(new PlayerData("p1", true), null))
                .isEqualTo(RatingLeague.fromRating(1000).icon());
        verify(store, never()).find(anyString(), anyInt(), anyString());

        assertThat(display.resolve(new PlayerData("", true), null)).isEmpty();
        assertThat(display.resolve(null, null)).isEmpty();
    }

    @Test
    @DisplayName("preload loads the standing once and redraws the player's name")
    void preload_loadsStandingAndRefreshes() throws InterruptedException {
        store.put(new LadderStanding("duel", 1, "p1", 1650, 1650, 30, 20, Map.of()));
        Player player = player("p1");

        display.preload(player);

        assertThat(refreshed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(display.resolve(null, player)).isEqualTo(RatingLeague.fromRating(1650).icon());
        verify(refreshService).refresh("p1");

        display.preload(player); // already cached
        verify(store, times(1)).find("duel", 1, "p1");
    }

    @Test
    @DisplayName("a settlement changes the icon without another load")
    void resolve_followsSettlements() {
        ladder.settle(MatchSettlement.rated("m1", "v1", MatchSettlement.hash("m1"),
                java.util.List.of(new StandingMutation("p1", 250, true))));

        assertThat(display.resolve(new PlayerData("p1", true), null))
                .isEqualTo(RatingLeague.fromRating(1250).icon());
    }
}
