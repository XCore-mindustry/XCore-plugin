package org.xcore.plugin.service.stats;

import arc.util.Log;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadPreference;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.MongoTransactions;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.metrics.Tags;
import org.xcore.plugin.metrics.XcoreMetrics;
import org.xcore.plugin.startup.ShutdownOrder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.min;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.setOnInsert;
import static com.mongodb.client.model.Updates.unset;
import static org.xcore.plugin.service.stats.ParticipantOutcomes.RULES_VERSION;
import static org.xcore.plugin.service.stats.ParticipantOutcomes.number;
import static org.xcore.plugin.service.stats.ParticipantOutcomes.of;
import static org.xcore.plugin.service.stats.ParticipantOutcomes.participants;

/** Durable materialized profile, fenced against live writes, merges and expired workers. */
@Singleton
public class PlayerTotalsStore {
    private static final String BACKFILL_ID = "player_totals_backfill";
    private static final long LEASE_MS = 120_000;
    private static final long LEASE_RENEW_MS = 30_000;
    private static final long BACKFILL_LEASE_MS = 600_000;
    private static final long RETRY_BASE_MS = 30_000;
    private static final long RETRY_MAX_MS = 30 * 60_000;
    private static final long SAMPLE_INTERVAL_MS = 60_000;
    private static final int REBUILDS_PER_TICK = 20;
    private static final int BACKFILL_BATCH = 1_000;

    private final MongoCollection<Document> totals;
    private final MongoCollection<Document> games;
    private final MongoCollection<Document> settings;
    private final MongoTransactions transactions;
    private final StorageExecutor storage;
    private final TomlXcoreConfig config;
    private final TomlSecretsConfig secrets;
    private final @Nullable MetricsService metrics;
    private final ScheduledTasks tasks = new ScheduledTasks(PlayerTotalsStore.class);
    private final AtomicBoolean running = new AtomicBoolean();
    private long sampledAt;

    @Inject
    public PlayerTotalsStore(MongoDatabase database, MongoTransactions transactions, StorageExecutor storage,
                             TomlXcoreConfig config, TomlSecretsConfig secrets, @Nullable MetricsService metrics) {
        this.totals = database.getCollection("player_totals").withReadPreference(ReadPreference.primary());
        this.games = database.getCollection("games_v2").withReadPreference(ReadPreference.primary());
        this.settings = database.getCollection("settings");
        this.transactions = transactions;
        this.storage = storage;
        this.config = config;
        this.secrets = secrets;
        this.metrics = metrics;
    }

    public PlayerTotalsStore(MongoDatabase database, MongoTransactions transactions, StorageExecutor storage,
                             TomlXcoreConfig config, TomlSecretsConfig secrets) {
        this(database, transactions, storage, config, secrets, null);
    }

    public boolean enabled() {
        return config.statistics.enabled && !secrets.database.readOnly;
    }

    public boolean readsEnabled() {
        return config.statistics.readEnabled;
    }

    public Document find(String uuid) {
        return totals.find(eq("_id", uuid)).first();
    }

    @PostConstruct
    public void start() {
        if (!enabled()) return;
        tasks.every(1, 5, () -> {
            if (!running.compareAndSet(false, true)) return;
            storage.supply(() -> {
                try {
                    backfillOnce();
                    for (int i = 0; i < REBUILDS_PER_TICK && rebuildOne(); i++) {
                        // A bounded batch per tick leaves room for other storage work.
                    }
                    sampleBacklog();
                } finally {
                    running.set(false);
                }
                return null;
            }).exceptionally(error -> {
                running.set(false);
                Log.err("[Stats] Background rebuild failed", error);
                return null;
            });
        });
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        tasks.close();
    }

    /** The initial scan runs on one server at a time, guarded by an expiring lease. */
    private void backfillOnce() {
        var seed = settings.find(eq("_id", BACKFILL_ID)).first();
        if (seed == null || Boolean.TRUE.equals(seed.getBoolean("done"))) return;
        long now = System.currentTimeMillis();
        String token = UUID.randomUUID().toString();
        var claimed = settings.findOneAndUpdate(
                and(eq("_id", BACKFILL_ID), ne("done", true), or(exists("lease_until", false), lt("lease_until", now))),
                combine(set("lease_until", now + BACKFILL_LEASE_MS), set("token", token)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (claimed == null) return;
        rebuildAll();
        settings.updateOne(and(eq("_id", BACKFILL_ID), eq("token", token)), set("done", true));
    }

    private void sampleBacklog() {
        long now = System.currentTimeMillis();
        if (metrics == null || now - sampledAt < SAMPLE_INTERVAL_MS) return;
        sampledAt = now;
        var dirty = eq("rebuild.dirty", true);
        metrics.gauge(XcoreMetrics.STATS_DIRTY_PLAYERS).set(totals.countDocuments(dirty));
        var oldest = totals.find(dirty).sort(new Document("rebuild.queued_at", 1))
                .projection(new Document("rebuild.queued_at", 1)).first();
        long queuedAt = oldest == null ? now : number(oldest.get("rebuild", Document.class), "queued_at");
        metrics.gauge(XcoreMetrics.STATS_OLDEST_DIRTY_SECONDS).set(Math.max(0, now - queuedAt) / 1000);
    }

    public boolean write(Document snapshot) {
        if (!enabled()) throw new IllegalStateException("Materialized statistics writer is disabled");
        String matchId = snapshot.getString("match_id");
        if (matchId == null || matchId.isBlank()) throw new IllegalArgumentException("Missing match_id");
        String hash = snapshot.getString("payload_hash");
        if (hash == null || hash.isBlank()) throw new IllegalArgumentException("Missing immutable payload hash");
        try {
            return transactions.runRequired(session -> {
                // A fresh copy per attempt: withTransaction may run this body again.
                Document game = Document.parse(snapshot.toJson());
                var touched = new TreeSet<String>();
                for (var row : game.getList("player_stats", Document.class, List.of())) {
                    String uuid = row.getString("uuid");
                    if (uuid == null || uuid.isBlank()) continue;
                    row.putIfAbsent("participant_uuid", uuid);
                    row.put("uuid", resolve(session, uuid, touched));
                }
                var players = participants(game);
                for (var rows : players.values()) {
                    var result = of(game, rows);
                    for (var row : rows) {
                        row.put("outcome", result.outcome().name());
                        row.put("outcome_reason", result.reason());
                        row.put("outcome_rules_version", RULES_VERSION);
                        row.put("present_ms", result.presentMs());
                    }
                }
                games.insertOne(session, game);
                for (String uuid : touched) {
                    var current = totals.find(session, eq("_id", uuid)).first();
                    if (players.containsKey(uuid) && appliesIncrementally(current, game)) {
                        PlayerTotals.apply(current, game, of(game, players.get(uuid)));
                        current.put("revision", number(current, "revision") + 1);
                        current.put("updated_at", System.currentTimeMillis());
                        totals.replaceOne(session, eq("_id", uuid), current);
                    } else {
                        invalidate(session, uuid);
                    }
                }
                return true;
            });
        } catch (MongoWriteException e) {
            if (!isSameMatch(e, snapshot)) throw e;
            return false;
        }
    }

    /** Only a clean, current projection may take a match that comes after everything it has seen. */
    private static boolean appliesIncrementally(@Nullable Document current, Document game) {
        if (current == null || !"ready".equals(current.getString("status"))) return false;
        if (number(current, "rules_version") != RULES_VERSION) return false;
        var rebuild = current.get("rebuild", Document.class);
        return rebuild != null && !Boolean.TRUE.equals(rebuild.getBoolean("dirty"))
                && PlayerTotals.after(game, current.get("last_order", Document.class));
    }

    /** A duplicate key is a replay only when the stored match carries the same payload. */
    private boolean isSameMatch(MongoWriteException e, Document snapshot) {
        if (e.getCode() != 11000) return false;
        String detail = e.getError().getMessage();
        boolean byMatchId = detail.contains("match_id");
        if (!byMatchId && !detail.contains("_id_")) return false;
        var previous = games.find(eq("match_id", snapshot.getString("match_id"))).first();
        if (previous == null || !Objects.equals(previous.getString("payload_hash"), snapshot.getString("payload_hash"))) {
            return false;
        }
        return byMatchId || Objects.equals(previous.get("_id"), snapshot.get("_id"));
    }

    private String resolve(ClientSession session, String uuid, Set<String> touched) {
        var seen = new HashSet<String>();
        while (true) {
            if (!seen.add(uuid)) throw new IllegalStateException("Cyclic player redirect");
            touched.add(uuid);
            String next = redirect(session, uuid);
            if (next == null || next.isBlank()) return uuid;
            uuid = next;
        }
    }

    private @Nullable String redirect(@Nullable ClientSession session, String uuid) {
        var state = session == null
                ? totals.find(eq("_id", uuid)).first()
                : totals.find(session, eq("_id", uuid)).first();
        return state == null ? null : state.getString("redirect_uuid");
    }

    private static Bson invalidation() {
        // $min keeps the place of a player already waiting; a publish replaces rebuild and drops queued_at.
        return combine(inc("revision", 1L), set("rebuild.dirty", true), min("rebuild.queued_at", System.currentTimeMillis()),
                setOnInsert("status", "pending"), setOnInsert("schema_version", 1));
    }

    public void invalidate(@Nullable ClientSession session, String uuid) {
        if (uuid == null || uuid.isBlank()) return;
        var options = new UpdateOptions().upsert(true);
        if (session == null) totals.updateOne(eq("_id", uuid), invalidation(), options);
        else totals.updateOne(session, eq("_id", uuid), invalidation(), options);
    }

    /** Readers follow redirect chains, so a target already leading back to the source would loop. */
    public void checkMerge(@Nullable ClientSession session, String source, String target) {
        var seen = new HashSet<String>();
        for (String next = target; next != null && !next.isBlank() && seen.add(next); next = redirect(session, next)) {
            if (next.equals(source)) throw new IllegalStateException("Target was merged into the source before");
        }
    }

    public void merge(@Nullable ClientSession session, String source, String target) {
        if (enabled() && session == null) throw new IllegalStateException("Statistics account merge requires a transaction");
        checkMerge(session, source, target);
        invalidate(session, source);
        invalidate(session, target);
        if (session == null) totals.updateOne(eq("_id", source), set("redirect_uuid", target));
        else totals.updateOne(session, eq("_id", source), set("redirect_uuid", target));
    }

    public void rebuildAll() {
        if (secrets.database.readOnly) throw new IllegalStateException("Read-only database");
        var batch = new ArrayList<WriteModel<Document>>(BACKFILL_BATCH);
        var options = new UpdateOptions().upsert(true);
        var pipeline = List.of(new Document("$unwind", "$player_stats"),
                new Document("$group", new Document("_id", "$player_stats.uuid")));
        try (var cursor = games.aggregate(pipeline).allowDiskUse(true).iterator()) {
            while (cursor.hasNext()) {
                if (!(cursor.next().get("_id") instanceof String uuid) || uuid.isBlank()) continue;
                batch.add(new UpdateOneModel<>(eq("_id", uuid), invalidation(), options));
                if (batch.size() == BACKFILL_BATCH) {
                    totals.bulkWrite(batch, new BulkWriteOptions().ordered(false));
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) totals.bulkWrite(batch, new BulkWriteOptions().ordered(false));
        // Includes zero-history sources after a merge and already materialized players.
        totals.updateMany(new Document(), combine(inc("revision", 1L), set("rebuild.dirty", true),
                min("rebuild.queued_at", System.currentTimeMillis())));
    }

    /** Read-only policy dry-run: safe to run against a restored backup. */
    public Document audit() {
        var counts = new Document();
        long rows = 0;
        long pairs = 0;
        try (var cursor = games.find().iterator()) {
            while (cursor.hasNext()) {
                var game = cursor.next();
                rows += game.getList("player_stats", Document.class, List.of()).size();
                var players = participants(game);
                pairs += players.size();
                for (var participant : players.values()) {
                    var result = of(game, participant);
                    String key = game.getString("stats_category") + ":" + result.outcome() + ":" + result.reason();
                    counts.put(key, number(counts, key) + 1);
                }
            }
        }
        return new Document("rows", rows).append("pairs", pairs).append("outcomes", counts);
    }

    /** Rebuilds the oldest due dirty player; returns false when nothing is due. */
    public boolean rebuildOne() {
        long now = System.currentTimeMillis();
        String token = UUID.randomUUID().toString();
        var state = totals.findOneAndUpdate(and(eq("rebuild.dirty", true),
                        or(exists("rebuild.retry_at", false), lte("rebuild.retry_at", now)),
                        or(exists("rebuild.lease_until", false), lt("rebuild.lease_until", now)),
                        // During a rules rollout an older server must not downgrade totals a newer one published.
                        or(exists("rules_version", false), lte("rules_version", RULES_VERSION))),
                combine(set("rebuild.lease_token", token), set("rebuild.lease_until", now + LEASE_MS)),
                new FindOneAndUpdateOptions().sort(new Document("rebuild.queued_at", 1)).returnDocument(ReturnDocument.AFTER));
        if (state == null) return false;
        var owner = and(eq("_id", state.getString("_id")), eq("rebuild.lease_token", token));
        try {
            countRebuild(replay(state, token, now) ? "published" : "superseded", now);
        } catch (RuntimeException e) {
            long attempts = number(state.get("rebuild", Document.class), "attempts");
            long backoff = Math.min(RETRY_MAX_MS, RETRY_BASE_MS << Math.min(attempts, 16));
            totals.updateOne(owner, combine(inc("rebuild.attempts", 1), set("rebuild.error", e.getClass().getSimpleName()),
                    set("rebuild.retry_at", System.currentTimeMillis() + backoff),
                    unset("rebuild.lease_token"), unset("rebuild.lease_until")));
            countRebuild("failed", now);
            throw e;
        }
        return true;
    }

    /** Publishes only if no write, merge or newer worker touched the player meanwhile. */
    private boolean replay(Document state, String token, long startedAt) {
        String uuid = state.getString("_id");
        long revision = number(state, "revision");
        var owner = and(eq("_id", uuid), eq("rebuild.lease_token", token));
        var result = PlayerTotals.empty(uuid);
        var projection = new Document("player_stats", 1).append("ended_at", 1).append("started_at", 1)
                .append("match_id", 1).append("stats_category", 1).append("winning_team", 1)
                .append("counted_in_stats", 1).append("waves_reached", 1).append("map", 1).append("game_mode", 1);
        try (var cursor = games.find(eq("player_stats.uuid", uuid)).sort(new Document("ended_at", 1).append("_id", 1))
                .projection(projection).iterator()) {
            long renewed = startedAt;
            while (cursor.hasNext()) {
                var game = cursor.next();
                PlayerTotals.apply(result, game, of(game, participants(game).getOrDefault(uuid, List.of())));
                if (System.currentTimeMillis() - renewed > LEASE_RENEW_MS) {
                    renewed = System.currentTimeMillis();
                    if (totals.updateOne(owner, set("rebuild.lease_until", renewed + LEASE_MS)).getMatchedCount() == 0) {
                        return false;
                    }
                }
            }
        }
        if (state.containsKey("redirect_uuid")) result.put("redirect_uuid", state.getString("redirect_uuid"));
        result.put("revision", revision + 1);
        result.put("updated_at", System.currentTimeMillis());
        result.put("rebuild", new Document("dirty", false));
        if (totals.replaceOne(and(owner, eq("revision", revision)), result).getMatchedCount() > 0) return true;
        totals.updateOne(owner, combine(unset("rebuild.lease_token"), unset("rebuild.lease_until")));
        return false;
    }

    private void countRebuild(String result, long startedAt) {
        if (metrics == null) return;
        metrics.increment(XcoreMetrics.STATS_REBUILDS_TOTAL, Tags.of("result", result));
        metrics.histogram(XcoreMetrics.STATS_REBUILD_DURATION_SECONDS)
                .observe((System.currentTimeMillis() - startedAt) / 1000.0d);
    }
}
