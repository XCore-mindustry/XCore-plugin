package org.xcore.plugin.rating.view;

import org.xcore.plugin.rating.prize.InMemoryPrizeGrantRepository;
import org.xcore.plugin.rating.season.SeasonPrizes;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.InMemoryLadderStore;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.season.InMemorySeasonStore;
import org.xcore.plugin.rating.season.SeasonFinalizer;
import org.xcore.plugin.rating.season.SeasonLifecycleService;
import org.xcore.plugin.rating.season.SeasonResolver;
import org.xcore.plugin.rating.season.SeasonSchedule;
import org.xcore.plugin.service.moderation.AuditService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One server with the "duel" ladder on in-memory stores and a clock the test moves, for
 * exercising what players are shown about seasons.
 */
final class RatingWorld {
    static final Instant START = Instant.parse("2026-10-03T12:00:00Z");
    /** Three months after {@link #START}, at midnight UTC. */
    static final Instant END = Instant.parse("2027-01-03T00:00:00Z");

    final InMemorySeasonStore seasons = new InMemorySeasonStore();
    final InMemoryLadderStore standings = new InMemoryLadderStore();
    final PlayerDataRepository players = mock(PlayerDataRepository.class);
    final SeasonResolver resolver = new SeasonResolver();
    final SeasonLifecycleService lifecycle;
    final Ladder ladder;
    final LadderViews views;

    private Instant now = START;
    private final Clock clock = new Clock() {
        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    };

    final InMemoryPrizeGrantRepository grants = new InMemoryPrizeGrantRepository();

    RatingWorld() {
        when(players.findByUuids(anyCollection())).thenReturn(List.of());
        TomlSecretsConfig config = new TomlSecretsConfig();
        SeasonSchedule schedule = SeasonSchedule.from(config.rating.seasons);
        InMemoryIdempotencyLedger ledger = new InMemoryIdempotencyLedger();
        lifecycle = new SeasonLifecycleService(seasons, resolver, schedule,
                new SeasonFinalizer(standings, players, schedule), SeasonPrizes.NONE, ledger, mock(AuditService.class), config,
                new Async(InlineStorageExecutor.create(), Runnable::run), clock);
        ladder = new LadderService(standings, ledger, lifecycle)
                .register(new LadderDefinition("duel", "top-menu-category-duel", RatingPolicy.teamEloV1()));
        views = new LadderViews(seasons, resolver, schedule, players, grants, clock);
    }

    void setTime(Instant instant) {
        now = instant;
    }

    /** Lets the running season end, be archived and the next one begin. */
    void finishSeason() {
        Instant end = resolver.current("duel").orElseThrow().endsAt();
        now = end;
        lifecycle.tick();
        now = end.plus(Duration.ofMinutes(5));
        lifecycle.tick();
    }

    void standing(int season, String uuid, int rating, int peak, int matches, int wins) {
        standings.put(new LadderStanding("duel", season, uuid, rating, peak, matches, wins, Map.of()));
    }

    /** A localisation that answers every key with the key and its arguments, e.g. {@code key{a=1, b=2}}. */
    static Localization echo() {
        Localization local = mock(Localization.class);
        when(local.t(anyString())).thenAnswer(call -> call.getArgument(0));
        when(local.t(anyString(), any())).thenAnswer(call ->
                call.getArgument(0) + new TreeMap<>(call.<Map<String, Object>>getArgument(1)).toString());
        when(local.getLocale()).thenReturn(Locale.ROOT);
        return local;
    }
}
