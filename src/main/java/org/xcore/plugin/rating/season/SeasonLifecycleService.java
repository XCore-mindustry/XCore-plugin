package org.xcore.plugin.rating.season;

import arc.util.Log;
import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.integration.idempotency.PluginIdempotencyLedger;
import org.xcore.plugin.integration.idempotency.PluginIdempotencyLedgerFactory;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.AuditAppendResult;
import org.xcore.plugin.model.AuditDetails;
import org.xcore.plugin.model.AuditTarget;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderSeasons;
import org.xcore.plugin.service.moderation.AuditService;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Moves seasons through their life: announces that one is ending, closes it at its
 * deadline, opens the next and archives the finished one.
 *
 * <p>Every server runs the same tick over every ladder's seasons. Each step is a
 * conditional write that only one server can win, so the network performs it once however
 * many servers are up. Blocking methods must stay off the Mindustry main thread.</p>
 */
@Singleton
public class SeasonLifecycleService implements LadderSeasons {
    private static final String LEDGER_ID = "rating";
    private static final String FINALIZE_OPERATION = "SEASON_FINALIZE";
    private static final float TICK_SECONDS = 30f;
    private static final int UPDATE_ATTEMPTS = 3;

    private final SeasonStore store;
    private final SeasonResolver resolver;
    private final SeasonSchedule schedule;
    private final SeasonFinalizer finalizer;
    private final SeasonPrizes prizes;
    private final PluginIdempotencyLedger ledger;
    private final AuditService audit;
    private final TomlSecretsConfig config;
    private final Async async;
    private final Clock clock;

    private final Map<String, Runnable> seasonChangedCallbacks = new ConcurrentHashMap<>();
    private final List<SeasonObserver> observers = new CopyOnWriteArrayList<>();
    private final List<SeasonEvents> events = new CopyOnWriteArrayList<>();
    private final Object refreshLock = new Object();
    private Timer.@Nullable Task tickTask;

    @Inject
    public SeasonLifecycleService(SeasonStore store,
                                  SeasonResolver resolver,
                                  SeasonSchedule schedule,
                                  SeasonFinalizer finalizer,
                                  SeasonPrizes prizes,
                                  PluginIdempotencyLedgerFactory ledgerFactory,
                                  AuditService audit,
                                  TomlSecretsConfig config,
                                  Async async) {
        this(store, resolver, schedule, finalizer, prizes, ledgerFactory.create(LEDGER_ID), audit, config, async,
                Clock.systemUTC());
    }

    public SeasonLifecycleService(SeasonStore store,
                                  SeasonResolver resolver,
                                  SeasonSchedule schedule,
                                  SeasonFinalizer finalizer,
                                  SeasonPrizes prizes,
                                  PluginIdempotencyLedger ledger,
                                  AuditService audit,
                                  TomlSecretsConfig config,
                                  Async async,
                                  Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.finalizer = Objects.requireNonNull(finalizer, "finalizer");
        this.prizes = Objects.requireNonNull(prizes, "prizes");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.config = Objects.requireNonNull(config, "config");
        this.async = Objects.requireNonNull(async, "async");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @PostConstruct
    public void start() {
        tickTask = Timer.schedule(() -> async.run(this::tickSafely), TICK_SECONDS, TICK_SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public void addObserver(SeasonObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public void addEvents(SeasonEvents listener) {
        events.add(Objects.requireNonNull(listener, "listener"));
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Blocking. Performs whatever is due right now and brings this server's view up to date. */
    public void tick() {
        if (!readOnly()) {
            Instant now = clock.instant();
            for (Season season : store.open()) {
                try {
                    step(season, now);
                } catch (RuntimeException e) {
                    Log.err("Season " + season.id() + " could not be advanced", e);
                }
            }
        }
        refresh();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException e) {
            Log.err("Season tick failed", e);
        }
    }

    private void step(Season season, Instant now) {
        switch (season.status()) {
            case ACTIVE -> {
                if (!now.isBefore(season.endsAt())) {
                    close(season, now);
                } else {
                    claimNotices(season, now);
                }
            }
            case CLOSING -> {
                openSuccessor(season, now);
                if (!now.isBefore(season.endsAt().plus(schedule.settlementGrace()))) {
                    archive(season);
                }
            }
            case ARCHIVED -> {
            }
        }
    }

    /** Ends a season whose deadline has passed and starts the next one. */
    private void close(Season season, Instant now) {
        store.update(season, season.close()).ifPresent(closed -> {
            PLog.info("Season @ is over; waiting @ for matches still settling", closed.id(),
                    schedule.settlementGrace());
            openSuccessor(closed, now);
        });
    }

    /** Also repairs a close that was interrupted before the next season was stored. */
    private void openSuccessor(Season closed, Instant now) {
        int number = closed.number() + 1;
        if (store.find(closed.ladderId(), number).isPresent()) {
            return;
        }
        Instant startsAt = closed.endsAt();
        Instant endsAt = schedule.endOf(startsAt);
        if (!endsAt.isAfter(now)) {
            // The network was down for longer than a season; do not open one that is already over.
            endsAt = schedule.endOf(now);
        }
        Season opened = Season.opening(closed.ladderId(), number, startsAt, endsAt);
        if (store.create(opened)) {
            PLog.info("Season @ has started and runs until @", opened.id(), endsAt);
            publish(listener -> listener.started(closed, opened));
        }
    }

    private void claimNotices(Season season, Instant now) {
        Set<String> due = schedule.dueNotices(now, season.endsAt());
        if (season.sentNotices().containsAll(due)) {
            return;
        }
        Set<String> sent = new LinkedHashSet<>(season.sentNotices());
        sent.addAll(due);
        store.update(season, season.withSentNotices(sent)).ifPresent(claimed -> {
            Set<String> fresh = new LinkedHashSet<>(due);
            fresh.removeAll(season.sentNotices());
            mostUrgent(fresh).ifPresent(notice -> publish(listener -> listener.noticeDue(claimed, notice)));
        });
    }

    /** When several notices fall due together, only the one closest to the end is worth saying. */
    private Optional<SeasonNotice> mostUrgent(Set<String> keys) {
        return keys.stream()
                .map(schedule::notice)
                .flatMap(Optional::stream)
                .min(Comparator.comparing(SeasonNotice::lead));
    }

    private void archive(Season season) {
        String operationId = "season:" + season.id() + ":finalize";
        if (!ledger.claim(operationId, FINALIZE_OPERATION, season.id()).acquired()) {
            return; // Another server is finalising it, or already has.
        }
        Season finalized = finalizer.archive(season);
        // Before the season is stored as archived, so a crash in between is retried with the grants intact.
        prizes.award(finalized);
        Season archived = store.update(season, finalized)
                // Left claimed on purpose: the lease expires and the finalisation is retried.
                .orElseThrow(() -> new IllegalStateException("Season changed while it was being finalised"));
        ledger.markCompleted(operationId, season.id());
        PLog.info("Season @ archived: @ participants, @ matches, podium of @", archived.id(),
                archived.summary().participants(), archived.summary().matches(), archived.podium().size());
        publish(listener -> listener.ended(archived));
    }

    private void publish(Consumer<SeasonEvents> delivery) {
        for (SeasonEvents listener : events) {
            try {
                delivery.accept(listener);
            } catch (RuntimeException e) {
                Log.err("A season event listener failed", e);
            }
        }
    }

    /** Re-reads the open seasons and tells this server's ladders and observers what changed. */
    private void refresh() {
        List<SeasonChange> changes;
        // Read and apply as one step, or a slow reader could put an older view over a newer one.
        // Ladders are told outside the lock: they take their own, which a settlement may hold
        // while it waits for this one.
        synchronized (refreshLock) {
            changes = resolver.update(store.open());
        }
        if (changes.isEmpty()) {
            return;
        }
        for (SeasonChange change : changes) {
            if (!(change instanceof SeasonChange.Started)) {
                continue;
            }
            Runnable seasonChanged = seasonChangedCallbacks.get(change.season().ladderId());
            if (seasonChanged != null) {
                try {
                    seasonChanged.run();
                } catch (RuntimeException e) {
                    Log.err("Ladder " + change.season().ladderId() + " failed to switch seasons", e);
                }
            }
        }
        async.main(() -> {
            for (SeasonChange change : changes) {
                for (SeasonObserver observer : observers) {
                    observer.seasonChanged(change);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Ladder integration
    // ------------------------------------------------------------------

    @Override
    public void open(LadderDefinition definition, Runnable onSeasonChanged) {
        seasonChangedCallbacks.put(definition.id(), onSeasonChanged);
        if (!readOnly() && store.list(definition.id()).isEmpty()) {
            Instant now = clock.instant();
            Season first = Season.opening(definition.id(), Ladder.FIRST_SEASON, now, schedule.endOf(now));
            if (store.create(first)) {
                PLog.info("Season @ has started and runs until @", first.id(), first.endsAt());
            }
        }
        refresh();
    }

    @Override
    public int current(String ladderId) {
        return resolver.current(ladderId).map(Season::number).orElse(Ladder.FIRST_SEASON);
    }

    @Override
    public int at(String ladderId, Instant when) {
        Optional<Season> current = resolver.current(ladderId);
        if (current.isPresent() && current.get().active() && !when.isBefore(current.get().endsAt())) {
            // The match outlived the season, but the tick has not rolled it over here yet.
            try {
                closeOverdue(ladderId);
            } catch (RuntimeException e) {
                Log.err("Season of ladder " + ladderId + " could not be rolled over before a settlement", e);
            }
        }
        return resolve(ladderId, when);
    }

    private void closeOverdue(String ladderId) {
        if (!readOnly()) {
            Instant now = clock.instant();
            for (Season season : store.list(ladderId)) {
                if (season.active() && !now.isBefore(season.endsAt())) {
                    close(season, now);
                }
            }
        }
        refresh();
    }

    private int resolve(String ladderId, Instant when) {
        Season current = resolver.current(ladderId).orElse(null);
        if (current == null) {
            return Ladder.FIRST_SEASON;
        }
        if (when.isBefore(current.startsAt())) {
            Instant now = clock.instant();
            for (Season closing : resolver.closing(ladderId)) {
                if (closing.covers(when) && now.isBefore(closing.endsAt().plus(schedule.settlementGrace()))) {
                    return closing.number();
                }
            }
        }
        // Anything that missed its own season's grace window counts towards the running one.
        return current.number();
    }

    @Override
    public int seed(LadderDefinition definition, int previousRating) {
        return schedule.reset().seed(previousRating, definition.policy());
    }

    @Override
    public void matchSettled(String ladderId, int season) {
        try {
            store.countMatch(ladderId, season);
        } catch (RuntimeException e) {
            // The counter is a statistic; losing one increment must not fail a settled match.
            Log.err("Failed to count a match towards season " + Season.id(ladderId, season), e);
        }
    }

    // ------------------------------------------------------------------
    // Administration
    // ------------------------------------------------------------------

    /**
     * Blocking. Moves the end of a ladder's running season.
     *
     * @return the season with its new deadline
     * @throws SeasonException when there is no running season or the new end is not in the future
     */
    public Season reschedule(String ladderId, Instant newEnd, AuditActor actor, @Nullable String reason) {
        Objects.requireNonNull(newEnd, "newEnd");
        for (int attempt = 0; attempt < UPDATE_ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            Season season = running(ladderId);
            if (!newEnd.isAfter(now)) {
                throw new SeasonException("The new end must be in the future; end-now finishes a season immediately");
            }
            if (newEnd.equals(season.endsAt())) {
                throw new SeasonException("Season " + season.id() + " already ends at " + newEnd);
            }
            Optional<Season> moved = move(season, newEnd, now, actor, reason);
            if (moved.isPresent()) {
                recordAudit(season, moved.get(), actor, reason);
                publish(listener -> listener.rescheduled(season, moved.get(), actor, reason));
                refresh();
                return moved.get();
            }
        }
        throw new SeasonException("The season kept changing while it was being rescheduled; try again");
    }

    /**
     * Blocking. Moves the end of a ladder's running season relative to where it is now.
     *
     * @param newEnd computes the new deadline from the current one
     * @throws SeasonException as {@link #reschedule}
     */
    public Season extend(String ladderId, UnaryOperator<Instant> newEnd, AuditActor actor, @Nullable String reason) {
        return reschedule(ladderId, newEnd.apply(running(ladderId).endsAt()), actor, reason);
    }

    /**
     * Blocking. Finishes a ladder's running season right now and starts the next one.
     *
     * @return the season that was ended
     * @throws SeasonException when there is no running season
     */
    public Season endNow(String ladderId, AuditActor actor, @Nullable String reason) {
        for (int attempt = 0; attempt < UPDATE_ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            Season season = running(ladderId);
            Optional<Season> moved = move(season, now, now, actor, reason);
            if (moved.isPresent()) {
                recordAudit(season, moved.get(), actor, reason);
                // Losing this race means a tick on another server closed it first.
                close(moved.get(), now);
                refresh();
                return store.find(ladderId, season.number()).orElse(moved.get());
            }
        }
        throw new SeasonException("The season kept changing while it was being ended; try again");
    }

    /**
     * Blocking. Adds a prize to the running season.
     *
     * @throws SeasonException when there is no running season or the prize cannot be delivered
     */
    public Season addPrize(String ladderId, SeasonPrize prize, AuditActor actor) {
        Objects.requireNonNull(prize, "prize");
        prizes.validate(prize);
        return changePrizes(ladderId, current -> {
            List<SeasonPrize> changed = new ArrayList<>(current);
            changed.add(prize);
            return changed;
        }, actor, "season_prize_added", prize.places() + " " + prize.kind() + " " + prize.value());
    }

    /**
     * Blocking. Removes the prizes of the running season that lie entirely within {@code from..to}.
     *
     * @throws SeasonException when there is no running season or no such prize
     */
    public Season removePrizes(String ladderId, int from, int to, AuditActor actor) {
        if (from < 1 || to < from) {
            throw new SeasonException("Places must be positive and in order, such as 1 or 1-3");
        }
        return changePrizes(ladderId, current -> {
            List<SeasonPrize> kept = current.stream().filter(prize -> !prize.within(from, to)).toList();
            if (kept.size() == current.size()) {
                throw new SeasonException("No prize lies within places " + from + "-" + to);
            }
            return kept;
        }, actor, "season_prize_removed", from + "-" + to);
    }

    private Season changePrizes(String ladderId, UnaryOperator<List<SeasonPrize>> change, AuditActor actor,
                                String event, String summary) {
        for (int attempt = 0; attempt < UPDATE_ATTEMPTS; attempt++) {
            Season season = running(ladderId);
            Season edited = season.withPrizes(change.apply(season.prizes()));
            Optional<Season> stored = store.update(season, edited);
            if (stored.isPresent()) {
                recordAudit(stored.get(), event, summary, Map.of("prizes", String.valueOf(edited.prizes().size())),
                        actor, "Season prizes changed");
                return stored.get();
            }
        }
        throw new SeasonException("The season kept changing while its prizes were edited; try again");
    }

    private Season running(String ladderId) {
        if (readOnly()) {
            throw new SeasonException("The database is read-only on this server");
        }
        return store.list(ladderId).stream()
                .filter(Season::active)
                .findFirst()
                .orElseThrow(() -> new SeasonException("Ladder '" + ladderId + "' has no running season"));
    }

    private Optional<Season> move(Season season, Instant newEnd, Instant now, AuditActor actor,
                                  @Nullable String reason) {
        SeasonReschedule change = new SeasonReschedule(season.endsAt(), newEnd, actorKey(actor), now,
                reason == null ? "" : reason.strip());
        // Notices that are no longer due under the new deadline will be announced again.
        Set<String> stillSent = new LinkedHashSet<>(season.sentNotices());
        stillSent.retainAll(schedule.dueNotices(now, newEnd));
        return store.update(season, season.reschedule(change, stillSent));
    }

    private void recordAudit(Season before, Season after, AuditActor actor, @Nullable String reason) {
        recordAudit(after, "season_rescheduled", after.endsAt().toString(), Map.of(
                "ends_at_before", before.endsAt().toString(),
                "ends_at_after", after.endsAt().toString()), actor,
                reason == null || reason.isBlank() ? "Season end moved" : reason.strip());
    }

    private void recordAudit(Season after, String event, String summary, Map<String, String> extra,
                             AuditActor actor, String reason) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("event", event);
        details.put("season", after.id());
        details.put("change", summary);
        details.putAll(extra);
        try {
            AuditAppendResult result = audit.append(AuditAppendCommand.builder()
                    .action(AuditAction.NOTE)
                    .actor(actor)
                    .target(AuditTarget.builder()
                            .uuid("season:" + after.id())
                            .nameSnapshot("Season " + after.id())
                            .build())
                    .reason(reason)
                    .details(AuditDetails.builder().extra(details).build())
                    .build());
            if (result != null && !result.isSuccess()) {
                PLog.warn("Season @ changed (@) but the audit record was not stored", after.id(), event);
            }
        } catch (RuntimeException e) {
            // The change is already recorded on the season itself.
            Log.err("Season " + after.id() + " changed (" + event + ") but the audit record failed", e);
        }
    }

    private static String actorKey(AuditActor actor) {
        String type = actor.type == null ? "system" : actor.type.name().toLowerCase();
        return type + ":" + (actor.id == null || actor.id.isBlank() ? "unknown" : actor.id);
    }

    private boolean readOnly() {
        return config.database.readOnly;
    }
}
