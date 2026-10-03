package org.xcore.plugin.rating.ladder;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.integration.idempotency.LedgerClaim;
import org.xcore.plugin.integration.idempotency.PluginIdempotencyLedger;

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
    /** Every ladder runs a single, open-ended season until seasons are scheduled. */
    public static final int FIRST_SEASON = 1;
    private static final String OPERATION_TYPE = "RATING_SETTLEMENT";

    private final LadderDefinition definition;
    private final LadderStore store;
    private final PluginIdempotencyLedger ledger;
    private final ConcurrentMap<String, LadderStanding> cache = new ConcurrentHashMap<>();

    Ladder(LadderDefinition definition, LadderStore store, PluginIdempotencyLedger ledger) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.store = Objects.requireNonNull(store, "store");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public LadderDefinition definition() {
        return definition;
    }

    public String id() {
        return definition.id();
    }

    public int currentSeason() {
        return FIRST_SEASON;
    }

    /**
     * The player's standing in the current season, virtual when they have not played yet.
     * Blocks on the first lookup of a player.
     */
    public LadderStanding standing(String uuid) {
        LadderStanding cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        LadderStanding loaded = fetchStanding(uuid);
        // A settlement may have cached a newer standing while this one was loading.
        LadderStanding raced = cache.putIfAbsent(uuid, loaded);
        return raced != null ? raced : loaded;
    }

    /**
     * Blocking. Always reads storage and leaves the cache alone, for servers that do not
     * host the mode and therefore never see its settlements.
     */
    public LadderStanding fetchStanding(String uuid) {
        int season = currentSeason();
        return store.find(id(), season, uuid)
                .orElseGet(() -> LadderStanding.unplaced(id(), season, uuid, startingRating()));
    }

    /** Blocks on the first lookup of a player; unknown or blank players have the starting rating. */
    public int rating(@Nullable String uuid) {
        return uuid == null || uuid.isBlank() ? startingRating() : standing(uuid).rating();
    }

    public Optional<LadderStanding> cachedStanding(@Nullable String uuid) {
        return uuid == null ? Optional.empty() : Optional.ofNullable(cache.get(uuid));
    }

    /** Never blocks: the starting rating stands in until {@link #standing(String)} has loaded the player. */
    public int cachedRating(@Nullable String uuid) {
        return cachedStanding(uuid).map(LadderStanding::rating).orElseGet(this::startingRating);
    }

    /**
     * Records a finished match exactly once, across retries and server restarts. Blocking.
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

        int season = currentSeason();
        Map<String, LadderStanding> standings = new LinkedHashMap<>();
        for (StandingMutation mutation : settlement.mutations()) {
            // Not applied means this player's effect survived an earlier, interrupted attempt;
            // carry on so the remaining participants are recovered.
            LadderStanding standing = store.applyOnce(id(), season, operationId, mutation,
                    startingRating(), definition.policy().minimumRating()).standing();
            cache.put(mutation.uuid(), standing);
            standings.put(mutation.uuid(), standing);
        }
        if (!ledger.markCompleted(operationId, settlement.resultHash())) {
            throw new IllegalStateException("Rating settlement was applied but ledger completion failed: " + operationId);
        }
        return SettlementResult.applied(operationId, standings);
    }

    /** Blocking. */
    public StandingPage top(int limit, @Nullable String cursor) {
        return store.top(id(), currentSeason(), limit, cursor);
    }

    /** Blocking. Empty when the player has not played a rated match this season. */
    public OptionalLong rankOf(@Nullable String uuid) {
        return uuid == null || uuid.isBlank() ? OptionalLong.empty() : store.rankOf(id(), currentSeason(), uuid);
    }

    /** Blocking. */
    public long count() {
        return store.count(id(), currentSeason());
    }

    /** Blocking. Re-reads every cached standing, for when storage changed behind the ladder's back. */
    public synchronized void reloadCache() {
        for (String uuid : List.copyOf(cache.keySet())) {
            cache.put(uuid, fetchStanding(uuid));
        }
    }

    public String operationId(String matchId, String algorithmVersion) {
        return id() + ":" + matchId + ":rating:" + algorithmVersion;
    }

    private int startingRating() {
        return definition.policy().defaultRating();
    }
}
