package org.xcore.plugin.database.repository;

import arc.util.Log;
import com.mongodb.MongoException;
import com.mongodb.MongoNodeIsRecoveringException;
import com.mongodb.MongoNotPrimaryException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoDatabase;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.Document;
import org.bson.codecs.EncoderContext;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.metrics.Tags;
import org.xcore.plugin.metrics.XcoreMetrics;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.MongoAsync;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.GameData;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.model.enums.GameStatsCategory;
import org.xcore.plugin.service.stats.PlayerTotalsStore;
import org.xcore.plugin.service.stats.PlayerTotalsView;
import org.xcore.plugin.startup.ShutdownOrder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@Singleton
public class GameDataRepository extends DataRepository<GameData> {
    private static final String COLLECTION_NAME = "games_v2";

    /** Delays before the second and third attempt of a match write. */
    long[] retryDelaysMs = {5_000, 30_000};
    /** Written by the statistics policy at storage time, so a replay of a stored match carries them. */
    private static final List<String> OUTCOME_FIELDS = List.of("outcome", "outcome_reason", "outcome_rules_version", "present_ms");
    /** How long shutdown waits for accepted match writes before storage closes. */
    long drainTimeoutMs = 15_000;

    /** Match writes accepted and not yet finished, retries included. */
    private final Set<CompletableFuture<Boolean>> pending = ConcurrentHashMap.newKeySet();
    /** Completed on shutdown: a write waiting for its retry delay tries again at once. */
    private final CompletableFuture<Void> draining = new CompletableFuture<>();

    private PlayerTotalsStore totalsStore;
    private StorageExecutor storage;
    private @Nullable MetricsService metrics;

    @Inject
    public GameDataRepository(MongoDatabase database, ReactiveMongoStore reactiveMongoStore, TomlSecretsConfig secretsConfig,
                              PlayerTotalsStore totalsStore, StorageExecutor storage, @Nullable MetricsService metrics) {
        this(database, reactiveMongoStore, secretsConfig);
        this.totalsStore = totalsStore;
        this.storage = storage;
        this.metrics = metrics;
    }

    public GameDataRepository(MongoDatabase database, ReactiveMongoStore reactiveMongoStore, TomlSecretsConfig secretsConfig) {
        super(database, reactiveMongoStore, COLLECTION_NAME, GameData.class, secretsConfig);

        collection.createIndex(new Document("map", 1));
        collection.createIndex(new Document("event", 1));
        collection.createIndex(new Document("server_name", 1));
        collection.createIndex(new Document("created_at", -1));
        collection.createIndex(new Document("player_stats.uuid", 1));
        collection.createIndex(new Document("match_id", 1), new IndexOptions().unique(true).sparse(true));
    }

    public GameDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        this(database, null, secretsConfig);
    }

    /** Saves a match once, returning false when the match ID was already stored. */
    public boolean saveOnce(GameData game) {
        if (game == null || game.matchId == null || game.matchId.isBlank()) return false;
        if (isReadOnly()) return false;
        if (totalsStore != null && totalsStore.enabled()) return totalsStore.write(snapshot(game));
        try {
            collection.insertOne(game);
            return true;
        } catch (MongoWriteException duplicate) {
            // Only a duplicate-key race means that the match was already stored;
            // network, validation, and write-concern failures must remain visible.
            var error = duplicate.getError();
            if (error != null && (error.getCode() == 11000 || error.getCode() == 11001)) {
                return false;
            }
            throw duplicate;
        }
    }

    /** Encode on the caller's thread so no mutable gameplay object escapes to storage. */
    public Document snapshot(GameData game) {
        if (game.id == null) game.id = new ObjectId();
        var bson = new BsonDocument();
        collection.getCodecRegistry().get(GameData.class).encode(new BsonDocumentWriter(bson), game,
                EncoderContext.builder().build());
        var doc = Document.parse(bson.toJson());
        // Ownership and calculated outcomes may change after account merge or rebuild, so they stay out of the hash.
        var payload = Document.parse(doc.toJson());
        payload.remove("_id");
        payload.remove("created_at");
        payload.remove("updated_at");
        payload.remove("deleted_at");
        // A stored match, decoded and sent again, carries its owner and outcome; only who played and how is hashed.
        for (var row : payload.getList("player_stats", Document.class, List.of())) {
            String participant = row.getString("participant_uuid");
            if (participant == null || participant.isBlank()) participant = row.getString("uuid");
            row.remove("uuid");
            row.put("participant_uuid", participant);
            for (String calculated : OUTCOME_FIELDS) row.remove(calculated);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.toJson().getBytes(StandardCharsets.UTF_8));
            doc.put("payload_hash", HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return doc;
    }

    public CompletionStage<Boolean> saveOnceAsync(GameData game) {
        if (game == null || game.matchId == null || game.matchId.isBlank() || isReadOnly()) {
            return CompletableFuture.completedFuture(false);
        }
        if (storage != null) {
            Document snapshot;
            try {
                snapshot = snapshot(game);
            } catch (RuntimeException error) {
                return CompletableFuture.failedFuture(error);
            }
            var write = store(snapshot, 0);
            pending.add(write);
            write.whenComplete((stored, error) -> pending.remove(write));
            return write;
        }
        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for saveOnceAsync"));
        }

        return MongoAsync.first(reactiveCollection.insertOne(game))
                .thenApply(ignored -> true)
                .exceptionally(error -> {
                    Throwable cause = unwrap(error);
                    if (cause instanceof MongoWriteException duplicate) {
                        var writeError = duplicate.getError();
                        if (writeError != null && (writeError.getCode() == 11000 || writeError.getCode() == 11001)) {
                            return false;
                        }
                    }
                    throw new CompletionException(cause);
                });
    }

    /** Replaying the same snapshot is safe: match_id and payload_hash turn an already committed write into a duplicate. */
    private CompletableFuture<Boolean> store(Document snapshot, int attempt) {
        return storage.supply(() -> insert(snapshot)).handle((stored, error) -> {
            if (error == null) {
                countWrite(stored ? "stored" : "duplicate");
                return CompletableFuture.completedFuture(stored);
            }
            Throwable cause = unwrap(error);
            String matchId = snapshot.getString("match_id");
            if (attempt < retryDelaysMs.length && isTransient(cause)) {
                countWrite("retried");
                Log.warn("[Stats] Retrying match @ in @ ms: @", matchId, retryDelaysMs[attempt], cause.toString());
                var later = CompletableFuture.delayedExecutor(retryDelaysMs[attempt], TimeUnit.MILLISECONDS);
                // On shutdown the rest of the delay is skipped, so the retry runs while storage is still open.
                return CompletableFuture.anyOf(CompletableFuture.runAsync(() -> { }, later), draining)
                        .thenCompose(ignored -> store(snapshot, attempt + 1));
            }
            countWrite("failed");
            Log.err("[Stats] Match @ was not saved after @ attempt(s)", matchId, attempt + 1);
            return CompletableFuture.<Boolean>failedFuture(cause);
        }).thenCompose(Function.identity());
    }

    /** Runs before storage closes, so a match waiting for a retry is not lost on a graceful shutdown. */
    @PreDestroy(priority = ShutdownOrder.DRAIN_WRITES)
    public void drain() {
        draining.complete(null);
        var writes = pending.toArray(CompletableFuture[]::new);
        if (writes.length == 0) return;
        Log.info("[Stats] Waiting for @ match write(s) before closing storage", writes.length);
        try {
            CompletableFuture.allOf(writes).get(drainTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.err("[Stats] @ match write(s) did not finish before shutdown", pending.size());
        } catch (ExecutionException e) {
            // Each failed write has already logged its match ID.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean insert(Document snapshot) {
        if (totalsStore.enabled()) return totalsStore.write(snapshot);
        try {
            database.getCollection(COLLECTION_NAME).insertOne(snapshot);
            return true;
        } catch (MongoWriteException e) {
            // insertOne put its _id into the snapshot, so a retry after a lost reply collides on _id_.
            String detail = e.getError().getMessage();
            if (e.getCode() == 11000 && (detail.contains("match_id") || detail.contains("_id_"))) return false;
            throw e;
        }
    }

    private static boolean isTransient(Throwable error) {
        if (error instanceof MongoSocketException || error instanceof MongoTimeoutException
                || error instanceof MongoNotPrimaryException || error instanceof MongoNodeIsRecoveringException
                || error instanceof RejectedExecutionException) {
            return true;
        }
        return error instanceof MongoException mongo
                && (mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                || mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL));
    }

    private void countWrite(String result) {
        if (metrics != null) metrics.increment(XcoreMetrics.STATS_WRITES_TOTAL, Tags.of("result", result));
    }

    public AggregatedPlayerStats aggregatePlayerStats(String uuid) {
        if (totalsStore != null && totalsStore.readsEnabled()) {
            return PlayerTotalsView.overview(totalsStore.read(uuid)).overall();
        }
        if (uuid == null || uuid.isBlank()) {
            return AggregatedPlayerStats.EMPTY;
        }

        var results = database.getCollection(COLLECTION_NAME).aggregate(List.of(
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
                Aggregates.match(Filters.eq("counted_in_stats", true)),
                Aggregates.unwind("$player_stats"),
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
                Aggregates.group(null,
                        Accumulators.sum("gamesPlayed", 1),
                        Accumulators.sum("gamesWon", new Document("$cond", List.of("$player_stats.isWinner", 1, 0))),
                        Accumulators.sum("blocksBuilt", "$player_stats.blocks_built"),
                        Accumulators.sum("blocksDeconstructed", "$player_stats.blocks_deconstructed"),
                        Accumulators.sum("blocksDestroyed", "$player_stats.blocks_destroyed"),
                        Accumulators.sum("unitsProduced", "$player_stats.units_produced"),
                        Accumulators.sum("unitsDestroyed", "$player_stats.units_destroyed")
                ),
                Aggregates.project(Projections.excludeId())
        )).into(new ArrayList<>());

        if (results.isEmpty()) {
            return AggregatedPlayerStats.EMPTY;
        }

        var doc = results.getFirst();
        return new AggregatedPlayerStats(
                doc.getInteger("gamesPlayed", 0),
                doc.getInteger("gamesWon", 0),
                doc.getInteger("blocksBuilt", 0),
                doc.getInteger("blocksDeconstructed", 0),
                doc.getInteger("blocksDestroyed", 0),
                doc.getInteger("unitsProduced", 0),
                doc.getInteger("unitsDestroyed", 0)
        );
    }

    private Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    public PlayerStatsOverview aggregatePlayerStatsOverview(String uuid) {
        if (totalsStore != null && totalsStore.readsEnabled()) {
            var totals = totalsStore.read(uuid);
            return PlayerTotalsView.overview(totals, recentMapNames(totals));
        }
        if (uuid == null || uuid.isBlank()) {
            return new PlayerStatsOverview(AggregatedPlayerStats.EMPTY, ModeStatsSummary.EMPTY, ModeStatsSummary.EMPTY, ModeStatsSummary.EMPTY);
        }

        AggregatedPlayerStats overall = aggregatePlayerStats(uuid);
        var modeStats = aggregateModeStats(uuid);

        return new PlayerStatsOverview(
                overall,
                modeStats.getOrDefault(GameStatsCategory.PVP, ModeStatsSummary.EMPTY),
                modeStats.getOrDefault(GameStatsCategory.SURVIVAL, ModeStatsSummary.EMPTY),
                modeStats.getOrDefault(GameStatsCategory.HEXED, ModeStatsSummary.EMPTY)
        );
    }

    private Map<String, String> recentMapNames(Document totals) {
        if (totals == null) return Map.of();
        var ids = new ArrayList<ObjectId>();
        for (var row : totals.getList("recent", Document.class, List.of())) {
            String id = row.getString("map_id");
            if (id != null && ObjectId.isValid(id)) ids.add(new ObjectId(id));
        }
        if (ids.isEmpty()) return Map.of();
        var names = new HashMap<String, String>();
        for (var map : database.getCollection("maps").find(Filters.in("_id", ids)).projection(Projections.include("name"))) {
            names.put(map.getObjectId("_id").toHexString(), map.getString("name"));
        }
        return names;
    }

    private Map<GameStatsCategory, ModeStatsSummary> aggregateModeStats(String uuid) {
        var rows = database.getCollection(COLLECTION_NAME).aggregate(List.of(
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
                Aggregates.match(Filters.eq("counted_in_stats", true)),
                Aggregates.unwind("$player_stats"),
                Aggregates.match(Filters.eq("player_stats.uuid", uuid)),
                Aggregates.group("$stats_category",
                        Accumulators.sum("gamesPlayed", 1),
                        Accumulators.sum("gamesWon", new Document("$cond", List.of("$player_stats.isWinner", 1, 0))),
                        Accumulators.max("bestWave", "$waves_reached"),
                        Accumulators.avg("averageWave", "$waves_reached"),
                        Accumulators.min("bestPlacement", new Document("$ifNull", List.of("$player_stats.placement", Integer.MAX_VALUE))),
                        Accumulators.sum("top3Finishes", new Document("$cond", List.of(
                                new Document("$and", List.of(
                                        new Document("$ne", Arrays.asList("$player_stats.placement", null)),
                                        new Document("$lte", List.of("$player_stats.placement", 3))
                                )),
                                1,
                                0
                        )))
                ),
                Aggregates.project(Projections.fields(
                        Projections.computed("statsCategory", "$_id"),
                        Projections.include("gamesPlayed", "gamesWon", "bestWave", "averageWave", "bestPlacement", "top3Finishes"),
                        Projections.excludeId()
                ))
        )).into(new ArrayList<>());

        Map<GameStatsCategory, ModeStatsSummary> result = new EnumMap<>(GameStatsCategory.class);
        for (var doc : rows) {
            String categoryName = doc.getString("statsCategory");
            if (categoryName == null || categoryName.isBlank()) {
                continue;
            }

            GameStatsCategory category;
            try {
                category = GameStatsCategory.valueOf(categoryName);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            int bestPlacement = doc.getInteger("bestPlacement", Integer.MAX_VALUE);
            if (bestPlacement == Integer.MAX_VALUE) {
                bestPlacement = 0;
            }

            double averageWave = doc.getDouble("averageWave") == null ? 0.0 : doc.getDouble("averageWave");

            result.put(category, new ModeStatsSummary(
                    doc.getInteger("gamesPlayed", 0),
                    doc.getInteger("gamesWon", 0),
                    doc.getInteger("bestWave", 0),
                    (int) Math.round(averageWave),
                    bestPlacement,
                    doc.getInteger("top3Finishes", 0)
            ));
        }

        return result;
    }

    /** Throws when the merge would make a redirect cycle; call it before the merge writes anything. */
    public void checkMerge(@Nullable ClientSession session, String oldUuid, String newUuid) {
        if (totalsStore != null) totalsStore.checkMerge(session, oldUuid, newUuid);
    }

    public long reassignPlayerMatches(String oldUuid, String newUuid) {
        return reassignPlayerMatches(null, oldUuid, newUuid);
    }

    public long reassignPlayerMatches(ClientSession session, String oldUuid, String newUuid) {
        if (oldUuid == null || newUuid == null || oldUuid.isBlank() || newUuid.isBlank() || oldUuid.equals(newUuid) || isReadOnly()) {
            return 0;
        }
        if (totalsStore != null) totalsStore.merge(session, oldUuid, newUuid);
        var filter = Filters.eq("player_stats.uuid", oldUuid);
        var update = Updates.set("player_stats.$[elem].uuid", newUuid);
        var options = new UpdateOptions().arrayFilters(List.of(Filters.eq("elem.uuid", oldUuid)));
        if (session != null) {
            return collection.updateMany(session, filter, update, options).getModifiedCount();
        }
        return collection.updateMany(filter, update, options).getModifiedCount();
    }
}
