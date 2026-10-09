package org.xcore.plugin.rating.ladder;

import com.mongodb.client.ClientSession;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.integration.idempotency.PluginIdempotencyLedger;
import org.xcore.plugin.integration.idempotency.PluginIdempotencyLedgerFactory;
import org.xcore.plugin.rating.match.InMemoryMatchStore;
import org.xcore.plugin.rating.match.MatchStore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Registry of the ladders this server knows about; modes register theirs at startup. */
@Singleton
public class LadderService {
    private static final String LEDGER_ID = "rating";

    private final LadderStore store;
    private final PluginIdempotencyLedger ledger;
    private final LadderSeasons seasons;
    private final MatchStore matches;
    private final ConcurrentMap<String, Ladder> ladders = new ConcurrentHashMap<>();

    @Inject
    public LadderService(LadderStore store, PluginIdempotencyLedgerFactory ledgerFactory, LadderSeasons seasons,
                         MatchStore matches) {
        this(store, ledgerFactory.create(LEDGER_ID), seasons, matches);
    }

    /** Ladders with one open-ended season, and a match history kept in memory. */
    public LadderService(LadderStore store, PluginIdempotencyLedger ledger) {
        this(store, ledger, LadderSeasons.single());
    }

    /** Ladders with a match history kept in memory. */
    public LadderService(LadderStore store, PluginIdempotencyLedger ledger, LadderSeasons seasons) {
        this(store, ledger, seasons, new InMemoryMatchStore());
    }

    public LadderService(LadderStore store, PluginIdempotencyLedger ledger, LadderSeasons seasons,
                         MatchStore matches) {
        this.store = Objects.requireNonNull(store, "store");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.matches = Objects.requireNonNull(matches, "matches");
    }

    /**
     * Registers a ladder, or returns the one already registered under the same definition.
     * Blocks while the ladder's seasons are looked up.
     *
     * @throws IllegalArgumentException when the ID is taken by a different definition
     */
    public Ladder register(LadderDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Ladder ladder = ladders.computeIfAbsent(definition.id(), _ -> {
            Ladder created = new Ladder(definition, store, ledger, seasons, matches);
            seasons.open(definition, created::reloadCache);
            return created;
        });
        if (!ladder.definition().equals(definition)) {
            throw new IllegalArgumentException("A ladder is already registered with a different definition: "
                    + definition.id());
        }
        return ladder;
    }

    public Optional<Ladder> find(@Nullable String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(ladders.get(id));
    }

    public List<Ladder> all() {
        return List.copyOf(ladders.values());
    }

    /**
     * Folds one account's standings into another across every ladder and season, including
     * ladders hosted by other servers, and moves its matches in the history along. Blocking.
     *
     * @return number of source standings merged
     */
    public int mergePlayer(@Nullable ClientSession session, String sourceUuid, String targetUuid) {
        int merged = store.mergePlayer(session, sourceUuid, targetUuid);
        matches.reassignPlayer(session, sourceUuid, targetUuid);
        return merged;
    }

    /** Blocking. Re-reads cached standings after they were changed behind the ladders' backs. */
    public void reloadCaches() {
        ladders.values().forEach(Ladder::reloadCache);
    }
}
