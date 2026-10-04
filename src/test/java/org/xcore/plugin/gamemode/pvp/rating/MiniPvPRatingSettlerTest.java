package org.xcore.plugin.gamemode.pvp.rating;

import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.integration.profile.ProfileSectionRegistry;
import org.xcore.plugin.rating.prize.InMemoryPrizeGrantRepository;
import org.xcore.plugin.rating.season.InMemorySeasonStore;
import org.xcore.plugin.rating.season.SeasonResolver;
import org.xcore.plugin.rating.season.SeasonSchedule;
import org.xcore.plugin.rating.view.LadderViews;
import mindustry.game.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.integration.gamehistory.MatchHistoryRecord;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.ladder.InMemoryLadderStore;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.season.SeasonAnnouncer;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MiniPvPRatingSettlerTest {
    private MiniPvPMatchTracker tracker;
    private InMemoryLadderStore store;
    private Ladder ladder;
    private SessionService sessionService;
    private PlayerDataRepository playerRepo;
    private PlayerDisplayRefreshService displayRefresh;
    private GameDataService gameDataService;
    private MiniPvPRatingSettler settler;

    @BeforeEach
    void setUp() {
        tracker = new MiniPvPMatchTracker();
        tracker.startMatch(null);
        store = new InMemoryLadderStore();
        sessionService = mock(SessionService.class);
        playerRepo = mock(PlayerDataRepository.class);
        displayRefresh = mock(PlayerDisplayRefreshService.class);
        gameDataService = mock(GameDataService.class);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        SeasonSchedule schedule = SeasonSchedule.from(new TomlSecretsConfig().rating.seasons);
        MiniPvPLadder miniPvPLadder = new MiniPvPLadder(
                new LadderService(store, new InMemoryIdempotencyLedger()),
                new TopCategoryRegistry(), new ProfileSectionRegistry(),
                new LadderViews(new InMemorySeasonStore(), new SeasonResolver(), schedule, playerRepo,
                        new InMemoryPrizeGrantRepository()),
                mock(SeasonAnnouncer.class), config);
        ladder = miniPvPLadder.ladder();

        settler = new MiniPvPRatingSettler(
                tracker,
                miniPvPLadder,
                sessionService,
                displayRefresh,
                gameDataService,
                // Storage work runs on real threads; the "game thread" continuation runs inline.
                new Async(new StorageExecutor(4), Runnable::run)
        );
    }

    private void twoTeamsOfTwo(long startedAgoMs) {
        long started = System.currentTimeMillis() - startedAgoMs;
        tracker.setStartedAt(started);
        tracker.trackParticipant(new MiniPvPMatchTracker.ParticipantInfo("p1", "Winner 1", Team.sharded.id, started, 0L));
        tracker.trackParticipant(new MiniPvPMatchTracker.ParticipantInfo("p2", "Winner 2", Team.sharded.id, started, 0L));
        tracker.trackParticipant(new MiniPvPMatchTracker.ParticipantInfo("p3", "Loser 1", Team.crux.id, started, 0L));
        tracker.trackParticipant(new MiniPvPMatchTracker.ParticipantInfo("p4", "Loser 2", Team.crux.id, started, 0L));
    }

    private PlayerData onlinePlayer(String uuid, String nickname) {
        Session session = mock(Session.class);
        PlayerData data = new PlayerData(uuid, true);
        data.nickname = nickname;
        session.data = data;
        when(session.locale()).thenReturn(mock(Localization.class));
        when(sessionService.get(uuid)).thenReturn(session);
        return data;
    }

    @Test
    @DisplayName("settle skips a match that ended before the minimum play time")
    void settle_skipsWhenTooShort() {
        twoTeamsOfTwo(10_000L);

        assertThat(settler.settle(Team.sharded).join()).isFalse();

        assertThat(settler.isSettled()).isFalse();
        assertThat(ladder.count()).isZero();
        verifyNoInteractions(gameDataService);
    }

    @Test
    @DisplayName("settle skips a match without an opposing team")
    void settle_skipsWithoutOpponents() {
        long started = System.currentTimeMillis() - 120_000L;
        tracker.setStartedAt(started);
        tracker.trackParticipant(new MiniPvPMatchTracker.ParticipantInfo("p1", "Solo 1", Team.sharded.id, started, 0L));
        tracker.trackParticipant(new MiniPvPMatchTracker.ParticipantInfo("p2", "Solo 2", Team.sharded.id, started, 0L));

        assertThat(settler.settle(Team.sharded).join()).isFalse();
        assertThat(settler.settle(Team.derelict).join()).isFalse();
        assertThat(settler.settle(null).join()).isFalse();

        assertThat(ladder.count()).isZero();
    }

    @Test
    @DisplayName("settle writes the ladder, mirrors the legacy profile fields and notifies once")
    void settle_successfulRoundSettlement() {
        twoTeamsOfTwo(120_000L);
        PlayerData winner = onlinePlayer("p1", "Winner 1");
        PlayerData loser = onlinePlayer("p3", "Loser 1");

        assertThat(settler.settle(Team.sharded).join()).isTrue();
        assertThat(settler.isSettled()).isTrue();

        // The ladder is the source of truth.
        LadderStanding winnerStanding = store.find(MiniPvPLadder.LADDER_ID, Ladder.FIRST_SEASON, "p1").orElseThrow();
        LadderStanding loserStanding = store.find(MiniPvPLadder.LADDER_ID, Ladder.FIRST_SEASON, "p3").orElseThrow();
        assertThat(winnerStanding.rating()).isGreaterThan(1000);
        assertThat(winnerStanding.matches()).isEqualTo(1);
        assertThat(winnerStanding.wins()).isEqualTo(1);
        assertThat(loserStanding.rating()).isLessThan(1000);
        assertThat(loserStanding.wins()).isZero();
        assertThat(ladder.count()).isEqualTo(4);
        assertThat(ladder.cachedRating("p2")).isEqualTo(winnerStanding.rating());

        verifyNoInteractions(playerRepo);

        verify(sessionService.get("p1").locale()).send(eq("pvp-match-settlement-win"), anyMap());
        verify(sessionService.get("p3").locale()).send(eq("pvp-match-settlement-loss"), anyMap());
        verify(displayRefresh).refreshAll();

        var history = org.mockito.ArgumentCaptor.forClass(MatchHistoryRecord.class);
        verify(gameDataService).recordMatch(history.capture());
        assertThat(history.getValue().participants()).hasSize(4);

        // Exactly once: a second call for the same match does nothing.
        assertThat(settler.settle(Team.sharded).join()).isFalse();
        assertThat(ladder.standing("p1").matches()).isEqualTo(1);
    }

    @Test
    @DisplayName("the ledger refuses a match that was already settled, even after the round flag is reset")
    void settle_ledgerBlocksResettlement() {
        twoTeamsOfTwo(120_000L);
        assertThat(settler.settle(Team.sharded).join()).isTrue();
        int rating = ladder.rating("p1");

        settler.onNewRound(); // the tracker still holds the same match
        assertThat(settler.isSettled()).isFalse();

        assertThat(settler.settle(Team.sharded).join()).isFalse();
        assertThat(ladder.rating("p1")).isEqualTo(rating);
        assertThat(ladder.standing("p1").matches()).isEqualTo(1);
        verify(gameDataService, times(1)).recordMatch(any());
        verify(displayRefresh, times(1)).refreshAll();
    }

    @Test
    @DisplayName("ratings carry over from the ladder into the next match")
    void settle_usesLadderRatings() {
        store.put(new LadderStanding(MiniPvPLadder.LADDER_ID, Ladder.FIRST_SEASON, "p3", 1600, 1600, 20, 15, Map.of()));
        store.put(new LadderStanding(MiniPvPLadder.LADDER_ID, Ladder.FIRST_SEASON, "p4", 1600, 1600, 20, 15, Map.of()));
        twoTeamsOfTwo(120_000L);

        assertThat(settler.settle(Team.sharded).join()).isTrue();

        // An even 2v2 pays each winner a quarter of the K-factor; an upset pays more.
        assertThat(ladder.rating("p1") - 1000).isGreaterThan(8);
        LadderStanding favourite = ladder.standing("p3");
        assertThat(favourite.rating()).isLessThan(1600);
        assertThat(favourite.peakRating()).isEqualTo(1600);
        assertThat(favourite.matches()).isEqualTo(21);
        assertThat(favourite.wins()).isEqualTo(15);
    }
}
