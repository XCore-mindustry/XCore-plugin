package org.xcore.plugin.rating.ladder;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.integration.idempotency.LedgerClaim;
import org.xcore.plugin.integration.idempotency.PluginIdempotencyLedger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A registered ladder: the one place a mode reads standings from and settles matches into.
 *
 * <p>Methods documented as blocking hit storage and must stay off the Mindustry main
 * thread; the {@code cached*} accessors never block.</p>
 */
public final class Ladder {
    public static final int FIRST_SEASON = 1;
    private static final String OPERATION_TYPE = "RATING_SETTLEMENT";

    private final LadderDefinition definition;
    private final LadderStore store;
    private final PluginIdempotencyLedger ledger;
    private final LadderSeasons seasons;
    /** Standings of one season only; season {@code 0} marks the cache as not yet loaded. */
    private volatile SeasonCache cache = new SeasonCache(0, new ConcurrentHashMap<>());

    Ladder(LadderDefinition definition, LadderStore store, PluginIdempotencyLedger ledger, LadderSeasons seasons) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.store = Objects.requireNonNull(store, "store");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
    }

    public LadderDefinition definition() {
        return definition;
    }

    public String id() {
        return definition.id();
    }

    /** Never blocks. */
    public int currentSeason() {
        return seasons.current(id());
    }

    /**
     * The player's standing in the current season. A player who has not played in it yet
     * gets a virtual standing at the rating they would start from. Blocks on the first
     * lookup of a player.
     */
    public LadderStanding standing(String uuid) {
        SeasonCache current = currentCache();
        LadderStanding cached = current.standings().get(uuid);
        if (cached != null) {
            return cached;
        }
        LadderStanding loaded = load(current.season(), uuid);
        // A settlement may have cached a newer standing while this one was loading.
        LadderStanding raced = current.standings().putIfAbsent(uuid, loaded);
        return raced != null ? raced : loaded;
    }

    /**
     * Blocking. Always reads storage and leaves the cache alone, for servers that do not
     * host the mode and therefore never see its settlements.
     */
    public LadderStanding fetchStanding(String uuid) {
        return load(currentSeason(), uuid);
    }

    /** Blocks on the first lookup of a player; unknown or blank players have the default rating. */
    public int rating(@Nullable String uuid) {
        return uuid == null || uuid.isBlank() ? defaultRating() : standing(uuid).rating();
    }

    /** Never blocks. Empty until the player's standing in the current season has been loaded. */
    public Optional<LadderStanding> cachedStanding(@Nullable String uuid) {
        SeasonCache current = cache;
        if (uuid == null || current.season() != currentSeason()) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.standings().get(uuid));
    }

    /** Never blocks: the default rating stands in until {@link #standing(String)} has loaded the player. */
    public int cachedRating(@Nullable String uuid) {
        return cachedStanding(uuid).map(LadderStanding::rating).orElseGet(this::defaultRating);
    }

    /**
     * Records a finished match exactly once, across retries and server restarts. Blocking.
     * The match counts towards the season that was running when it ended.
     *
     * @throws IllegalStateException when the match was already settled with a different result
     */
    public synchronized SettlementResult settle(MatchSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        String operationId = operationId(settlement.matchId(), settlement.algorithmVersion());
        LedgerClaim claim = ledger.claim(operationId, OPERATION_TYPE, settlement.resultHash());
        if (!claim.acquired()) {
            return SettlementResult.duplicate(operationId, claim.entry().status().toLowerCase());
        }
        if (!settlement.rated()) {
            ledger.markSkipped(operationId, settlement.skipReason());
            return SettlementResult.skipped(operationId, settlement.skipReason());
        }

        int season = seasons.at(id(), settlement.endedAt() != null ? settlement.endedAt() : Instant.now());
        // A match that ended just before a rollover settles into the season that is closing,
        // which the cache no longer holds.
        SeasonCache current = currentCache();
        boolean cached = current.season() == season;
        Map<String, LadderStanding> standings = new LinkedHashMap<>();
        boolean changed = false;
        for (StandingMutation mutation : settlement.mutations()) {
            LadderStanding known = cached ? current.standings().get(mutation.uuid()) : null;
            StandingSeed seed = known != null && known.placed()
                    ? StandingSeed.fresh(known.rating())
                    : seed(season, mutation.uuid());
            // Not applied means this player's effect survived an earlier, interrupted attempt;
            // carry on so the remaining participants are recovered.
            LadderStore.ApplyResult result = store.applyOnce(id(), season, operationId, mutation,
                    seed, definition.policy().minimumRating());
            changed |= result.applied();
            if (cached) {
                current.standings().put(mutation.uuid(), result.standing());
            }
            standings.put(mutation.uuid(), result.standing());
        }
        // A replay that found every player already settled was counted by the first attempt.
        if (changed) {
            seasons.matchSettled(id(), season);
        }
        if (!ledger.markCompleted(operationId, settlement.resultHash())) {
            throw new IllegalStateException("Rating settlement was applied but ledger completion failed: " + operationId);
        }
        return SettlementResult.applied(operationId, standings);
    }

    /** Blocking. */
    public StandingPage top(int limit, @Nullable String cursor) {
        return top(currentSeason(), limit, cursor);
    }

    /** Blocking. A past season's leaderboard is the same query with another season. */
    public StandingPage top(int season, int limit, @Nullable String cursor) {
        return store.top(id(), season, limit, cursor);
    }

    /** Blocking. Empty when the player has not played a rated match this season. */
    public OptionalLong rankOf(@Nullable String uuid) {
        return rankOf(currentSeason(), uuid);
    }

    /** Blocking. Empty when the player did not play a rated match in {@code season}. */
    public OptionalLong rankOf(int season, @Nullable String uuid) {
        return uuid == null || uuid.isBlank() ? OptionalLong.empty() : store.rankOf(id(), season, uuid);
    }

    /** Blocking. */
    public long count() {
        return count(currentSeason());
    }

    /** Blocking. Number of players with a standing in {@code season}. */
    public long count(int season) {
        return store.count(id(), season);
    }

    /** Blocking. The standing a player finished or currently holds in {@code season}, if they played in it. */
    public Optional<LadderStanding> played(int season, @Nullable String uuid) {
        return uuid == null || uuid.isBlank() ? Optional.empty() : store.find(id(), season, uuid);
    }

    /** Blocking. The player's standings in the seasons before the current one, most recent first. */
    public List<LadderStanding> history(@Nullable String uuid, int limit) {
        return uuid == null || uuid.isBlank() ? List.of() : store.history(id(), currentSeason(), uuid, limit);
    }

    /**
     * Blocking. Re-reads every cached player's standing in the current season, for when
     * storage changed behind the ladder's back or a new season began.
     */
    public synchronized void reloadCache() {
        int season = currentSeason();
        ConcurrentMap<String, LadderStanding> standings = new ConcurrentHashMap<>();
        for (String uuid : cache.standings().keySet()) {
            standings.put(uuid, load(season, uuid));
        }
        cache = new SeasonCache(season, standings);
    }

    public String operationId(String matchId, String algorithmVersion) {
        return id() + ":" + matchId + ":rating:" + algorithmVersion;
    }

    private SeasonCache currentCache() {
        SeasonCache current = cache;
        if (current.season() != currentSeason()) {
            reloadCache();
            current = cache;
        }
        return current;
    }

    private LadderStanding load(int season, String uuid) {
        return store.find(id(), season, uuid)
                .orElseGet(() -> LadderStanding.unplaced(id(), season, uuid, seed(season, uuid).rating()));
    }

    /** The rating a player without a standing in {@code season} would enter it with. */
    private StandingSeed seed(int season, String uuid) {
        if (season <= FIRST_SEASON) {
            return StandingSeed.fresh(defaultRating());
        }
        return store.latestBefore(id(), season, uuid)
                .map(previous -> StandingSeed.carried(seasons.seed(definition, previous.rating()), previous))
                .orElseGet(() -> StandingSeed.fresh(defaultRating()));
    }

    private int defaultRating() {
        return definition.policy().defaultRating();
    }

    private record SeasonCache(int season, ConcurrentMap<String, LadderStanding> standings) {
    }
}
