package org.xcore.plugin.rating.ladder;

import com.mongodb.client.ClientSession;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.integration.idempotency.PluginIdempotencyLedger;
import org.xcore.plugin.integration.idempotency.PluginIdempotencyLedgerFactory;

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
    private final ConcurrentMap<String, Ladder> ladders = new ConcurrentHashMap<>();

    @Inject
    public LadderService(LadderStore store, PluginIdempotencyLedgerFactory ledgerFactory) {
        this(store, ledgerFactory.create(LEDGER_ID));
    }

    public LadderService(LadderStore store, PluginIdempotencyLedger ledger) {
        this.store = Objects.requireNonNull(store, "store");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /**
     * Registers a ladder, or returns the one already registered under the same definition.
     *
     * @throws IllegalArgumentException when the ID is taken by a different definition
     */
    public Ladder register(LadderDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Ladder ladder = ladders.computeIfAbsent(definition.id(), _ -> new Ladder(definition, store, ledger));
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
     * ladders hosted by other servers. Blocking.
     *
     * @return number of source standings merged
     */
    public int mergePlayer(@Nullable ClientSession session, String sourceUuid, String targetUuid) {
        return store.mergePlayer(session, sourceUuid, targetUuid);
    }

    /** Blocking. Re-reads cached standings after they were changed behind the ladders' backs. */
    public void reloadCaches() {
        ladders.values().forEach(Ladder::reloadCache);
    }
}
