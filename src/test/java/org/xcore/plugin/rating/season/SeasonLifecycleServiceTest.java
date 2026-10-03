package org.xcore.plugin.rating.season;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.InMemoryLadderStore;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.ladder.MatchSettlement;
import org.xcore.plugin.rating.ladder.StandingMutation;
import org.xcore.plugin.rating.ladder.StandingSeed;
import org.xcore.plugin.service.moderation.AuditService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonLifecycleServiceTest {
    private static final RatingPolicy POLICY = new RatingPolicy(1000, 100, 32, 2);
    private static final LadderDefinition DUEL = new LadderDefinition("duel", "duel-name", POLICY);
    private static final Instant START = Instant.parse("2026-10-03T12:00:00Z");
    /** Three months after {@link #START}, at midnight UTC. */
    private static final Instant END = Instant.parse("2027-01-03T00:00:00Z");
    private static final AuditActor CONSOLE = AuditActor.builder()
            .type(AuditActorType.SERVER_CONSOLE).id("console").nameSnapshot("Console").build();

    private InMemorySeasonStore seasons;
    private InMemoryLadderStore standings;
    private InMemoryIdempotencyLedger ledger;
    private PlayerDataRepository players;
    private AuditService audit;
    private TomlSecretsConfig config;
    private MutableClock clock;

    private Server server;
    private Ladder ladder;

    /** One game server's view of the shared stores. */
    private final class Server {
        final SeasonResolver resolver = new SeasonResolver();
        final SeasonLifecycleService lifecycle;
        final LadderService ladders;
        final List<SeasonChange> observed = new ArrayList<>();
        final RecordingEvents events = new RecordingEvents();

        Server() {
            SeasonSchedule schedule = SeasonSchedule.from(config.rating.seasons);
            lifecycle = new SeasonLifecycleService(seasons, resolver, schedule,
                    new SeasonFinalizer(standings, players, schedule), ledger, audit, config,
                    // The "game thread" continuation runs inline.
                    new Async(new StorageExecutor(4), Runnable::run), clock);
            lifecycle.addObserver(observed::add);
            lifecycle.addEvents(events);
            ladders = new LadderService(standings, ledger, lifecycle);
        }
    }

    /** What {@link SeasonEvents} was told, in order, as short strings. */
    private static final class RecordingEvents implements SeasonEvents {
        final List<String> told = new ArrayList<>();

        @Override
        public void started(Season previous, Season season) {
            told.add("started " + previous.number() + "->" + season.number());
        }

        @Override
        public void noticeDue(Season season, SeasonNotice notice) {
            told.add("notice " + notice.key());
        }

        @Override
        public void ended(Season archived) {
            told.add("ended " + archived.number() + " podium=" + archived.podium().size());
        }

        @Override
        public void rescheduled(Season before, Season after, AuditActor actor, String reason) {
            told.add("rescheduled " + before.endsAt() + "->" + after.endsAt() + " by " + actor.getNameSnapshot());
        }
    }

    private List<String> told(Server... servers) {
        List<String> all = new ArrayList<>();
        for (Server each : servers) {
            all.addAll(each.events.told);
        }
        return all;
    }

    @BeforeEach
    void setUp() {
        seasons = new InMemorySeasonStore();
        standings = new InMemoryLadderStore();
        ledger = new InMemoryIdempotencyLedger();
        players = mock(PlayerDataRepository.class);
        when(players.findByUuids(anyCollection())).thenReturn(List.of());
        audit = mock(AuditService.class);
        config = new TomlSecretsConfig();
        clock = new MutableClock(START);

        server = new Server();
        ladder = server.ladders.register(DUEL);
    }

    private Season season(int number) {
        return seasons.find("duel", number).orElseThrow();
    }

    private static MatchSettlement match(String matchId, Instant endedAt, StandingMutation... mutations) {
        return MatchSettlement.rated(matchId, "v1", MatchSettlement.hash(matchId), List.of(mutations))
                .withEndedAt(endedAt);
    }

    private void standing(int season, String uuid, int rating, int matches) {
        standings.put(new LadderStanding("duel", season, uuid, rating, rating, matches, matches / 2, Map.of()));
    }

    @Test
    @DisplayName("registering a ladder opens its first season, once for the whole network")
    void register_opensFirstSeason() {
        Season first = season(1);
        assertThat(first.status()).isEqualTo(SeasonStatus.ACTIVE);
        assertThat(first.startsAt()).isEqualTo(START);
        assertThat(first.endsAt()).isEqualTo(END);
        assertThat(ladder.currentSeason()).isEqualTo(1);

        clock.advance(Duration.ofDays(1));
        Server other = new Server();
        Ladder otherLadder = other.ladders.register(DUEL);

        assertThat(seasons.list("duel")).containsExactly(first);
        assertThat(otherLadder.currentSeason()).isEqualTo(1);
        assertThat(server.observed).isEmpty();
        assertThat(other.observed).isEmpty();
    }

    @Test
    @DisplayName("a due notice is recorded once and observed by every server")
    void tick_claimsNotices() {
        Server other = new Server();
        other.ladders.register(DUEL);

        clock.set(END.minus(Duration.ofDays(8)));
        server.lifecycle.tick();
        assertThat(server.observed).isEmpty();

        clock.set(END.minus(Duration.ofDays(7)));
        server.lifecycle.tick();
        other.lifecycle.tick();
        server.lifecycle.tick();

        assertThat(season(1).sentNotices()).containsExactly("7d");
        assertThat(season(1).revision()).isEqualTo(1);
        assertThat(server.observed).containsExactly(new SeasonChange.NoticeDue(season(1), Set.of("7d")));
        assertThat(other.observed).containsExactly(new SeasonChange.NoticeDue(season(1), Set.of("7d")));

        // Several notices crossed between two ticks arrive as one change.
        clock.set(END.minus(Duration.ofMinutes(30)));
        other.lifecycle.tick();
        assertThat(season(1).sentNotices()).containsExactlyInAnyOrder("7d", "3d", "24h", "1h");
        assertThat(other.observed).hasSize(2);
        assertThat(((SeasonChange.NoticeDue) other.observed.getLast()).notices())
                .containsExactlyInAnyOrder("3d", "24h", "1h");
    }

    @Test
    @DisplayName("a server that starts late in a season does not replay notices already sent")
    void lateServer_doesNotReplayNotices() {
        clock.set(END.minus(Duration.ofDays(2)));
        server.lifecycle.tick();

        Server late = new Server();
        late.ladders.register(DUEL);
        late.lifecycle.tick();

        assertThat(late.observed).isEmpty();
    }

    @Test
    @DisplayName("at the deadline the season closes and the next one starts where it ended")
    void tick_closesAndOpensNext() {
        Server other = new Server();
        Ladder otherLadder = other.ladders.register(DUEL);

        clock.set(END.plusSeconds(20));
        server.lifecycle.tick();

        assertThat(season(1).status()).isEqualTo(SeasonStatus.CLOSING);
        Season second = season(2);
        assertThat(second.status()).isEqualTo(SeasonStatus.ACTIVE);
        assertThat(second.startsAt()).isEqualTo(END);
        assertThat(second.endsAt()).isEqualTo(Instant.parse("2027-04-03T00:00:00Z"));
        assertThat(ladder.currentSeason()).isEqualTo(2);
        assertThat(server.observed).containsExactly(new SeasonChange.Started(season(1), second));

        // The other server has nothing left to do but notices the rollover on its own tick.
        assertThat(otherLadder.currentSeason()).isEqualTo(1);
        other.lifecycle.tick();
        assertThat(otherLadder.currentSeason()).isEqualTo(2);
        assertThat(other.observed).containsExactly(new SeasonChange.Started(season(1), second));
        assertThat(seasons.list("duel")).hasSize(2);
    }

    @Test
    @DisplayName("after a long outage the next season is not opened already over")
    void longOutage_opensSeasonInTheFuture() {
        clock.set(Instant.parse("2028-02-10T08:00:00Z"));
        server.lifecycle.tick();

        Season second = season(2);
        assertThat(second.startsAt()).isEqualTo(END);
        assertThat(second.endsAt()).isEqualTo(Instant.parse("2028-05-10T00:00:00Z"));
        assertThat(seasons.list("duel")).hasSize(2);
    }

    @Test
    @DisplayName("a match counts towards the season that was running when it ended")
    void settle_usesMatchEndTime() {
        ladder.settle(match("m1", START.plusSeconds(60), new StandingMutation("a", 16, true)));

        clock.set(END.plusSeconds(30));
        server.lifecycle.tick();

        // Ended a second before the deadline, settled during the grace window.
        ladder.settle(match("m2", END.minusSeconds(1), new StandingMutation("a", 10, true)));
        // Ended after the deadline.
        ladder.settle(match("m3", END.plusSeconds(10), new StandingMutation("b", 5, true)));

        assertThat(standings.find("duel", 1, "a").orElseThrow().rating()).isEqualTo(1026);
        assertThat(standings.find("duel", 1, "a").orElseThrow().matches()).isEqualTo(2);
        assertThat(standings.find("duel", 2, "a")).isEmpty();
        assertThat(standings.find("duel", 2, "b").orElseThrow().rating()).isEqualTo(1005);
        assertThat(season(1).matches()).isEqualTo(2);
        assertThat(season(2).matches()).isEqualTo(1);

        // Once the grace window is over the old season accepts nothing more.
        clock.set(END.plus(Duration.ofMinutes(5)));
        ladder.settle(match("m4", END.minusSeconds(1), new StandingMutation("c", 7, true)));
        assertThat(standings.find("duel", 1, "c")).isEmpty();
        assertThat(standings.find("duel", 2, "c").orElseThrow().rating()).isEqualTo(1007);
    }

    @Test
    @DisplayName("a match that outlives the season rolls it over without waiting for the tick")
    void settle_rollsOverOverdueSeason() {
        clock.set(END.plusSeconds(5));

        ladder.settle(match("m1", END.plusSeconds(2), new StandingMutation("a", 16, true)));

        assertThat(season(1).status()).isEqualTo(SeasonStatus.CLOSING);
        assertThat(season(2).status()).isEqualTo(SeasonStatus.ACTIVE);
        assertThat(standings.find("duel", 2, "a").orElseThrow().rating()).isEqualTo(1016);
        assertThat(standings.find("duel", 1, "a")).isEmpty();
        assertThat(ladder.currentSeason()).isEqualTo(2);
    }

    @Test
    @DisplayName("replaying a settled match does not count it twice")
    void settle_replayIsCountedOnce() {
        MatchSettlement settlement = match("m1", START.plusSeconds(60), new StandingMutation("a", 16, true));
        ladder.settle(settlement);
        ladder.settle(settlement);

        assertThat(season(1).matches()).isEqualTo(1);
    }

    @Test
    @DisplayName("a returning player starts the new season from a softened rating, a newcomer from the default")
    void newSeason_seedsRatingsLazily() {
        standing(1, "veteran", 1400, 30);
        standing(1, "struggler", 700, 30);
        assertThat(ladder.standing("veteran").rating()).isEqualTo(1400);

        clock.set(END);
        server.lifecycle.tick();

        // The ladder's cache follows the season without being asked.
        assertThat(ladder.cachedRating("veteran")).isEqualTo(1200);
        LadderStanding veteran = ladder.standing("veteran");
        assertThat(veteran.season()).isEqualTo(2);
        assertThat(veteran.placed()).isFalse();
        assertThat(ladder.rating("struggler")).isEqualTo(850);
        assertThat(ladder.rating("newcomer")).isEqualTo(1000);
        // Nothing is written until the player actually plays.
        assertThat(standings.count("duel", 2)).isZero();

        ladder.settle(match("m1", END.plusSeconds(60),
                new StandingMutation("veteran", 10, true), new StandingMutation("newcomer", -10, false)));

        LadderStanding played = standings.find("duel", 2, "veteran").orElseThrow();
        assertThat(played.rating()).isEqualTo(1210);
        assertThat(played.matches()).isEqualTo(1);
        assertThat(standings.seedOf("duel", 2, "veteran")).contains(new StandingSeed(1200, 1, 1400));
        assertThat(standings.find("duel", 2, "newcomer").orElseThrow().rating()).isEqualTo(990);
        assertThat(standings.seedOf("duel", 2, "newcomer")).isEmpty();
        assertThat(standings.find("duel", 1, "veteran").orElseThrow().rating()).isEqualTo(1400);
        assertThat(ladder.cachedRating("veteran")).isEqualTo(1210);
    }

    @Test
    @DisplayName("a player who skipped a season is softened once, from the last season they played")
    void skippedSeason_seedsFromLatestStanding() {
        standing(1, "veteran", 1400, 30);
        clock.set(END);
        server.lifecycle.tick();
        clock.set(season(2).endsAt());
        server.lifecycle.tick();
        assertThat(ladder.currentSeason()).isEqualTo(3);

        ladder.settle(match("m1", clock.instant().plusSeconds(60), new StandingMutation("veteran", 0, false)));

        assertThat(standings.find("duel", 3, "veteran").orElseThrow().rating()).isEqualTo(1200);
        assertThat(standings.seedOf("duel", 3, "veteran")).contains(new StandingSeed(1200, 1, 1400));
    }

    @Test
    @DisplayName("after the grace window the season is archived with final ranks, a podium and totals")
    void tick_archivesAfterGrace() {
        standing(1, "ace", 1500, 12);
        standing(1, "casual", 1400, 3);
        standing(1, "regular", 1300, 10);
        ladder.settle(match("m1", START.plusSeconds(60), new StandingMutation("regular", 0, false)));
        when(players.findByUuids(anyCollection())).thenReturn(List.of(PlayerData.builder()
                .uuid("ace").pid(7).nickname("Ace").discordId("1234").discordUsername("ace").build()));

        clock.set(END);
        server.lifecycle.tick();
        clock.set(END.plus(Duration.ofMinutes(4)));
        server.lifecycle.tick();
        assertThat(season(1).status()).isEqualTo(SeasonStatus.CLOSING);
        assertThat(standings.finalRankOf("duel", 1, "ace")).isEmpty();

        clock.set(END.plus(Duration.ofMinutes(5)));
        server.lifecycle.tick();

        Season archived = season(1);
        assertThat(archived.status()).isEqualTo(SeasonStatus.ARCHIVED);
        assertThat(archived.summary()).isEqualTo(new SeasonSummary(3, 1));
        // "casual" outranks "regular" but has not played enough to stand on the podium.
        assertThat(archived.podium()).containsExactly(
                new SeasonPodiumEntry(1, "ace", 7, "Ace", 1500, RatingLeague.fromRating(1500).name(), 12, 6, "1234", "ace"),
                new SeasonPodiumEntry(2, "regular", -1, "Unknown", 1300, RatingLeague.fromRating(1300).name(), 11, 5, "", ""));
        assertThat(standings.finalRankOf("duel", 1, "ace")).hasValue(1);
        assertThat(standings.finalRankOf("duel", 1, "casual")).hasValue(2);
        assertThat(standings.finalRankOf("duel", 1, "regular")).hasValue(3);
        assertThat(ledger.find("season:duel:1:finalize").orElseThrow().status()).isEqualTo("COMPLETED");

        long revision = archived.revision();
        server.lifecycle.tick();
        assertThat(season(1).revision()).isEqualTo(revision);
        assertThat(seasons.open()).containsExactly(season(2));
    }

    @Test
    @DisplayName("a season being finalised by another server is left alone until that server's lease expires")
    void archive_respectsAnotherServersClaim() {
        clock.set(END);
        server.lifecycle.tick();
        ledger.claim("season:duel:1:finalize", "SEASON_FINALIZE", "duel:1");

        clock.set(END.plus(Duration.ofMinutes(10)));
        server.lifecycle.tick();
        assertThat(season(1).status()).isEqualTo(SeasonStatus.CLOSING);

        ledger.expireLeases();
        server.lifecycle.tick();
        assertThat(season(1).status()).isEqualTo(SeasonStatus.ARCHIVED);
    }

    @Test
    @DisplayName("a close interrupted before the next season was stored is repaired by the next tick")
    void tick_repairsMissingSuccessor() {
        clock.set(END);
        seasons.update(season(1), season(1).close());

        server.lifecycle.tick();

        assertThat(season(2).status()).isEqualTo(SeasonStatus.ACTIVE);
        assertThat(ladder.currentSeason()).isEqualTo(2);
    }

    @Test
    @DisplayName("extending a season moves its end, logs the change and re-arms notices that are no longer due")
    void reschedule_movesEnd() {
        clock.set(END.minus(Duration.ofDays(2)));
        server.lifecycle.tick();
        assertThat(season(1).sentNotices()).containsExactlyInAnyOrder("7d", "3d");

        Instant newEnd = END.plus(Duration.ofDays(3));
        Season moved = server.lifecycle.reschedule("duel", newEnd, CONSOLE, " holidays ");

        assertThat(moved.endsAt()).isEqualTo(newEnd);
        assertThat(moved.status()).isEqualTo(SeasonStatus.ACTIVE);
        // Five days are left: the 7-day notice still stands, the 3-day one will be sent again.
        assertThat(moved.sentNotices()).containsExactly("7d");
        assertThat(moved.rescheduled()).containsExactly(
                new SeasonReschedule(END, newEnd, "server_console:console", clock.instant(), "holidays"));
        assertThat(server.resolver.current("duel")).contains(moved);

        ArgumentCaptor<AuditAppendCommand> command = ArgumentCaptor.forClass(AuditAppendCommand.class);
        verify(audit).append(command.capture());
        assertThat(command.getValue().action()).isEqualTo(AuditAction.NOTE);
        assertThat(command.getValue().actor()).isEqualTo(CONSOLE);
        assertThat(command.getValue().reason()).isEqualTo("holidays");
        assertThat(command.getValue().target().uuid).isEqualTo("season:duel:1");
        assertThat(command.getValue().details().extra)
                .containsEntry("event", "season_rescheduled")
                .containsEntry("ends_at_before", END.toString())
                .containsEntry("ends_at_after", newEnd.toString());

        clock.set(newEnd.minus(Duration.ofDays(3)));
        server.lifecycle.tick();
        assertThat(season(1).sentNotices()).containsExactlyInAnyOrder("7d", "3d");
        assertThat(server.observed.getLast())
                .isEqualTo(new SeasonChange.NoticeDue(season(1), Set.of("3d")));
    }

    @Test
    @DisplayName("a season cannot be moved into the past, to the same moment, or once it is over")
    void reschedule_rejectsInvalidChanges() {
        assertThatThrownBy(() -> server.lifecycle.reschedule("duel", START.minusSeconds(1), CONSOLE, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> server.lifecycle.reschedule("duel", START, CONSOLE, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> server.lifecycle.reschedule("duel", END, CONSOLE, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("already ends");
        assertThatThrownBy(() -> server.lifecycle.reschedule("unknown", END, CONSOLE, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("no running season");

        assertThat(season(1).endsAt()).isEqualTo(END);
        assertThat(season(1).rescheduled()).isEmpty();
        verify(audit, never()).append(any());

        // A closing season is out of reach; the change lands on the season that replaced it.
        clock.set(END);
        server.lifecycle.tick();
        Instant newEnd = END.plus(Duration.ofDays(200));
        assertThat(server.lifecycle.reschedule("duel", newEnd, CONSOLE, null).number()).isEqualTo(2);
        assertThat(season(1).endsAt()).isEqualTo(END);
    }

    @Test
    @DisplayName("ending a season now closes it on the spot and starts the next one")
    void endNow_closesImmediately() {
        clock.set(START.plus(Duration.ofDays(10)));
        Instant now = clock.instant();

        Season ended = server.lifecycle.endNow("duel", CONSOLE, "restart");

        assertThat(ended.number()).isEqualTo(1);
        assertThat(ended.status()).isEqualTo(SeasonStatus.CLOSING);
        assertThat(ended.endsAt()).isEqualTo(now);
        assertThat(ended.rescheduled()).containsExactly(
                new SeasonReschedule(END, now, "server_console:console", now, "restart"));
        assertThat(season(2).status()).isEqualTo(SeasonStatus.ACTIVE);
        assertThat(season(2).startsAt()).isEqualTo(now);
        assertThat(season(2).endsAt()).isEqualTo(Instant.parse("2027-01-13T00:00:00Z"));
        assertThat(ladder.currentSeason()).isEqualTo(2);
        assertThat(server.observed).containsExactly(new SeasonChange.Started(season(1), season(2)));
        verify(audit).append(any());
    }

    @Test
    @DisplayName("a read-only server follows seasons but never writes them")
    void readOnly_onlyObserves() {
        config.database.readOnly = true;
        Server readOnly = new Server();
        Ladder mirror = readOnly.ladders.register(new LadderDefinition("ffa", "ffa-name", POLICY));
        Ladder duel = readOnly.ladders.register(DUEL);

        assertThat(seasons.list("ffa")).isEmpty();
        assertThat(mirror.currentSeason()).isEqualTo(Ladder.FIRST_SEASON);

        clock.set(END);
        readOnly.lifecycle.tick();
        assertThat(season(1).status()).isEqualTo(SeasonStatus.ACTIVE);
        assertThatThrownBy(() -> readOnly.lifecycle.reschedule("duel", END.plusSeconds(60), CONSOLE, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("read-only");

        config.database.readOnly = false;
        server.lifecycle.tick();
        config.database.readOnly = true;
        readOnly.lifecycle.tick();
        assertThat(duel.currentSeason()).isEqualTo(2);
    }

    @Test
    @DisplayName("each transition is announced to the network once, by the server that performed it")
    void events_fireOncePerTransition() {
        Server other = new Server();
        other.ladders.register(DUEL);

        clock.set(END.minus(Duration.ofDays(7)));
        server.lifecycle.tick();
        other.lifecycle.tick();
        assertThat(told(server, other)).containsExactly("notice 7d");

        clock.set(END);
        other.lifecycle.tick();
        server.lifecycle.tick();
        assertThat(told(server, other)).containsExactlyInAnyOrder("notice 7d", "started 1->2");

        clock.set(END.plus(Duration.ofMinutes(5)));
        server.lifecycle.tick();
        other.lifecycle.tick();
        // Which server wins each step is up to the race; that it is exactly one is the point.
        assertThat(told(server, other)).containsExactlyInAnyOrder("notice 7d", "started 1->2", "ended 1 podium=0");
    }

    @Test
    @DisplayName("when several notices fall due together only the one closest to the end is announced")
    void events_announceMostUrgentNotice() {
        clock.set(END.minus(Duration.ofMinutes(30)));
        server.lifecycle.tick();

        assertThat(season(1).sentNotices()).containsExactlyInAnyOrder("7d", "3d", "24h", "1h");
        assertThat(told(server)).containsExactly("notice 1h");
    }

    @Test
    @DisplayName("moving a season announces the change, ending it now announces the start of the next")
    void events_coverAdministration() {
        Instant newEnd = END.plus(Duration.ofDays(3));
        server.lifecycle.reschedule("duel", newEnd, CONSOLE, null);
        assertThat(told(server)).containsExactly("rescheduled " + END + "->" + newEnd + " by Console");

        server.lifecycle.endNow("duel", CONSOLE, null);
        assertThat(told(server)).contains("started 1->2");
    }

    @Test
    @DisplayName("extend moves the end relative to where it is now")
    void extend_isRelativeToCurrentEnd() {
        Season moved = server.lifecycle.extend("duel", end -> end.plus(Duration.ofDays(14)), CONSOLE, null);

        assertThat(moved.endsAt()).isEqualTo(END.plus(Duration.ofDays(14)));
        assertThatThrownBy(() -> server.lifecycle.extend("nowhere", end -> end.plusSeconds(1), CONSOLE, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("no running season");
    }

    @Test
    @DisplayName("a listener that fails does not stop the others or the season")
    void events_isolateFailingListeners() {
        server.lifecycle.addEvents(new SeasonEvents() {
            @Override
            public void rescheduled(Season before, Season after, AuditActor actor, String reason) {
                throw new IllegalStateException("redis is down");
            }
        });
        RecordingEvents later = new RecordingEvents();
        server.lifecycle.addEvents(later);

        server.lifecycle.reschedule("duel", END.plus(Duration.ofDays(1)), CONSOLE, null);

        assertThat(later.told).hasSize(1);
        assertThat(season(1).endsAt()).isEqualTo(END.plus(Duration.ofDays(1)));
    }
}
