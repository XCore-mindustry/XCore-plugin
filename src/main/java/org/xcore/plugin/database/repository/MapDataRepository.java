package org.xcore.plugin.database.repository;

import arc.struct.ObjectMap;
import arc.util.Strings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.MongoAsync;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.MapData;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.*;

@Singleton
public class MapDataRepository extends DataRepository<MapData> {

    @Inject
    public MapDataRepository(MongoDatabase database, ReactiveMongoStore reactiveMongoStore, TomlSecretsConfig secretsConfig) {
        super(database, reactiveMongoStore, "maps", MapData.class, secretsConfig);

        collection.createIndex(new Document("name", -1));
        collection.createIndex(new Document("author", -1));
        collection.createIndex(new Document("file_name", -1));
        collection.createIndex(new Document("game_mode", -1));
        collection.createIndex(new Document("popularity", -1));
        collection.createIndex(new Document("reputation", -1));
        collection.createIndex(new Document("interest", -1));
        collection.createIndex(new Document("played_times_year", 1));
    }

    public MapDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        this(database, null, secretsConfig);
    }

    public Optional<MapData> find(String name, String author, String gameMode) {
        if (name == null || author == null || gameMode == null) {
            return Optional.empty();
        }

        MapData doc = collection.find(and(
                eq("name", name),
                eq("author", author),
                eq("game_mode", gameMode)
        )).first();

        if (doc == null) {
            String plainName = Strings.stripColors(name);
            String plainAuthor = Strings.stripColors(author);
            var matches = collection.find(and(
                    regex("name", "^" + Pattern.quote(plainName) + "$", "i"),
                    regex("author", "^" + Pattern.quote(plainAuthor) + "$", "i"),
                    regex("game_mode", "^" + Pattern.quote(gameMode) + "$", "i")
            )).limit(2).into(new java.util.ArrayList<>());
            doc = matches.size() == 1 ? matches.getFirst() : null;
        }

        return Optional.ofNullable(doc);
    }

    public Optional<MapData> findByFileName(String fileName, String gameMode) {
        if (fileName == null || fileName.isBlank() || gameMode == null || gameMode.isBlank()) {
            return Optional.empty();
        }

        MapData doc = collection.find(and(
                eq("file_name", fileName),
                eq("game_mode", gameMode)
        )).first();

        if (doc == null) {
            var matches = collection.find(and(
                    regex("file_name", "^" + Pattern.quote(fileName) + "$", "i"),
                    regex("game_mode", "^" + Pattern.quote(gameMode) + "$", "i")
            )).limit(2).into(new java.util.ArrayList<>());
            doc = matches.size() == 1 ? matches.getFirst() : null;
        }

        return Optional.ofNullable(doc);
    }

    public Optional<MapData> findByFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }
        MapData doc = collection.find(eq("file_name", fileName)).first();
        if (doc == null) {
            var matches = collection.find(regex("file_name", "^" + Pattern.quote(fileName) + "$", "i"))
                    .limit(2).into(new java.util.ArrayList<>());
            doc = matches.size() == 1 ? matches.getFirst() : null;
        }
        return Optional.ofNullable(doc);
    }

    public List<MapData> findAll() {
        return collection.find().into(new ArrayList<>());
    }

    public java.util.concurrent.CompletionStage<List<MapData>> findAllAsync() {
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("Reactive MongoDB store is required for findAllAsync"));
        }
        return MongoAsync.list(reactiveCollection.find());
    }

    /** Read-only compatibility lookup. Never creates or relinks identity from a filename/alias. */
    public java.util.concurrent.CompletionStage<MapData> findExistingAsync(
            String name, String fileName, String author, String gameMode) {
        if (fileName == null || fileName.isBlank() || gameMode == null || gameMode.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("Reactive MongoDB store is required for map resolution"));
        }
        String cleanName = name != null ? Strings.stripColors(name) : "";
        String cleanAuthor = author != null ? Strings.stripColors(author) : "";
        var fileFilter = and(eq("file_name", fileName), eq("game_mode", gameMode));
        return MongoAsync.list(reactiveCollection.find(fileFilter).limit(2)).thenCompose(exact -> {
            if (!exact.isEmpty()) {
                if (exact.size() == 1) return java.util.concurrent.CompletableFuture.completedFuture(exact.getFirst());
                var sorted = new java.util.ArrayList<>(exact);
                sorted.sort((a, b) -> Long.compare(b.playedTimes, a.playedTimes));
                return java.util.concurrent.CompletableFuture.completedFuture(sorted.getFirst());
            }
            // Legacy aliases may help read an existing record
            return MongoAsync.list(reactiveCollection.find(and(
                    regex("game_mode", "^" + Pattern.quote(gameMode) + "$", "i"),
                    or(regex("file_name", "^" + Pattern.quote(fileName) + "$", "i"),
                            and(regex("name", "^" + Pattern.quote(cleanName) + "$", "i"),
                                    regex("author", "^" + Pattern.quote(cleanAuthor) + "$", "i"))))).limit(5))
                    .thenCompose(matches -> {
                        if (!matches.isEmpty()) {
                            if (matches.size() == 1) return java.util.concurrent.CompletableFuture.completedFuture(matches.getFirst());
                            var sorted = new java.util.ArrayList<>(matches);
                            sorted.sort((a, b) -> Long.compare(b.playedTimes, a.playedTimes));
                            return java.util.concurrent.CompletableFuture.completedFuture(sorted.getFirst());
                        }
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    });
        });
    }

    public MapData findOrCreate(String name, String fileName, String author, String gameMode) {
        MapData data = findByFileName(fileName, gameMode)
                .or(() -> find(name, author, gameMode)).orElse(null);
        boolean created = data == null;
        if (created) data = new MapData(name, fileName, author, gameMode);
        if (data.discordUpload == null && fileName != null && fileName.equals(data.fileName)) {
            data.discordUpload = findDiscordUpload(fileName);
        }
        if (created) save(data);
        return data;
    }

    /** Add advisory metadata only; never overwrite an existing identity or unrelated statistics. */
    public java.util.concurrent.CompletionStage<Boolean> updateMapContentHashAsync(
            ObjectId id, String fileName, org.xcore.plugin.map.domain.MapContentHash hash) {
        if (id == null || fileName == null || hash == null || isReadOnly()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("Reactive MongoDB store is required for content hash updates"));
        }
        return MongoAsync.first(reactiveCollection.updateOne(
                and(eq("_id", id), eq("file_name", fileName), eq("content_hash", null)),
                Updates.set("content_hash", hash.asHex())))
                .thenApply(result -> result != null && result.getMatchedCount() > 0);
    }

    /** Called on the storage executor after the engine accepted a downloaded map. */
    public boolean recordDiscordUpload(String name, String fileName, String author, String gameMode,
                                       String contentHash, org.xcore.plugin.model.MapDiscordUpload upload) {
        if (isReadOnly()) return false;
        // Writes use exact physical names. Case-insensitive read aliases must not join distinct files.
        MapData data = collection.find(and(eq("file_name", fileName), eq("game_mode", gameMode))).first();
        if (data == null) {
            data = new MapData(name, fileName, author, gameMode);
            data.contentHash = contentHash;
            data.discordUpload = upload;
            if (!save(data)) return false;
        }
        // Submission belongs to the file, regardless of the statistics' game mode.
        // Update only metadata; concurrent votes and game statistics are preserved.
        return collection.updateMany(eq("file_name", fileName), Updates.combine(
                Updates.set("name", name),
                Updates.set("author", author),
                Updates.set("content_hash", contentHash),
                upload == null ? Updates.unset("discord_upload") : Updates.set("discord_upload", upload),
                Updates.set("updated_at", System.currentTimeMillis())
        )).getMatchedCount() > 0;
    }

    /** Attribution is shared by all statistics partitions of this installed file. Off-thread only. */
    public org.xcore.plugin.model.MapDiscordUpload findDiscordUpload(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        try {
            MapData source = collection.find(and(eq("file_name", fileName), exists("discord_upload", true)))
                    .sort(new Document("discord_upload.uploadedAt", -1)).first();
            return source == null ? null : source.discordUpload;
        } catch (RuntimeException failure) {
            PLog.warn("Map attribution lookup failed for @: @", fileName, failure.toString());
            return null;
        }
    }

    /** Enrich an in-memory read result without creating or changing a persistent map identity. */
    public java.util.concurrent.CompletionStage<MapData> enrichDiscordUploadAsync(MapData data) {
        return enrichDiscordUploadAsync(data, data == null ? null : data.fileName);
    }

    public java.util.concurrent.CompletionStage<MapData> enrichDiscordUploadAsync(MapData data, String installedFileName) {
        if (data != null && !java.util.Objects.equals(data.fileName, installedFileName)) {
            // The statistics lookup may use an old display alias; its attribution is not proof for this file.
            data.discordUpload = null;
            return java.util.concurrent.CompletableFuture.completedFuture(data);
        }
        if (data == null || data.discordUpload != null || data.fileName == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(data);
        }
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for map attribution reads"));
        }
        return MongoAsync.first(reactiveCollection.find(and(
                eq("file_name", data.fileName), exists("discord_upload", true)))
                .sort(new Document("discord_upload.uploadedAt", -1)))
                .thenApply(source -> {
                    if (source != null) data.discordUpload = source.discordUpload;
                    return data;
                }).exceptionally(failure -> {
                    PLog.warn("Map attribution lookup failed for @: @", data.fileName, failure.toString());
                    return data;
                });
    }

    public void incrementStats(ObjectId id, double popularityDelta, double interestDelta, int reputationDelta) {
        if (id == null) return;
        collection.updateOne(
                eq("_id", id),
                Updates.combine(
                        Updates.inc("popularity", popularityDelta),
                        Updates.inc("interest", interestDelta),
                        Updates.inc("reputation", reputationDelta)
                )
        );
    }

    /** Write-behind vote persistence; shared state is already mutated on the tick thread. */
    public java.util.concurrent.CompletionStage<Boolean> applyVoteAsync(
            ObjectId id, int reputationDelta, double popularityDelta, int likeDelta, int dislikeDelta) {
        if (id == null || isReadOnly()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for applyVoteAsync"));
        }
        return MongoAsync.first(reactiveCollection.updateOne(
                eq("_id", id),
                Updates.combine(
                        Updates.inc("reputation", reputationDelta),
                        Updates.inc("popularity", popularityDelta),
                        Updates.inc("like", likeDelta),
                        Updates.inc("dislike", dislikeDelta),
                        Updates.set("updated_at", System.currentTimeMillis())
                ))).thenApply(result -> result != null && result.getMatchedCount() > 0);
    }

    public boolean applyVote(ObjectId id, int reputationDelta, double popularityDelta, int likeDelta, int dislikeDelta) {
        return updateById(id, Updates.combine(
                Updates.inc("reputation", reputationDelta),
                Updates.inc("popularity", popularityDelta),
                Updates.inc("like", likeDelta),
                Updates.inc("dislike", dislikeDelta)
        ));
    }

    public boolean markSkip(ObjectId id) {
        return updateById(id, Updates.combine(
                Updates.inc("popularity", -1.0),
                Updates.inc("interest", -0.5)
        ));
    }

    public boolean bumpPopularity(ObjectId id, double delta) {
        return updateById(id, Updates.inc("popularity", delta));
    }

    public boolean registerGameStats(ObjectId id,
                                     long duration,
                                     boolean isWin,
                                     String author,
                                     String mode,
                                     long playedTimes,
                                     long averageGameTime,
                                     long minimumGameTime,
                                     long maximumGameTime,
                                     int playedTimesYear,
                                     long lastPlayedTime,
                                     double popularity,
                                     double interest) {
        return updateById(id, Updates.combine(
                Updates.set("author", author),
                Updates.set("game_mode", mode),
                Updates.set("play_count", playedTimes),
                Updates.set("average_duration", averageGameTime),
                Updates.set("minimum_duration", minimumGameTime),
                Updates.set("maximum_duration", maximumGameTime),
                Updates.set("played_times_year", playedTimesYear),
                Updates.set("last_played_at", lastPlayedTime),
                Updates.set("popularity", popularity),
                Updates.set("interest", interest)
        ));
    }

    public void decayPopularity(double amount) {
        collection.updateMany(gt("popularity", 0), Updates.inc("popularity", -amount));
        collection.updateMany(lt("popularity", 0), Updates.set("popularity", 0.0));
    }

    public void decayInterest(double amount) {
        collection.updateMany(gt("interest", 0), Updates.inc("interest", -amount));
        collection.updateMany(lt("interest", 0), Updates.set("interest", 0.0));
    }

    public ObjectMap<String, MapData> findAllAsMap() {
        ObjectMap<String, MapData> map = new ObjectMap<>();
        for (MapData data : collection.find()) {
            map.put(genKey(data.name, data.author, data.gameMode), data);
        }
        return map;
    }

    public static String genKey(String name, String author, String mode) {
        return name + "|" + author + "|" + mode;
    }

    public void checkMapDecay() {
        var counters = database.getCollection("counters");

        long now = System.currentTimeMillis();
        long dayMillis = 24 * 60 * 60 * 1000L;

        try {
            counters.updateOne(
                    eq("_id", "last_map_decay"),
                    Updates.setOnInsert("time", now),
                    new UpdateOptions().upsert(true)
            );
        } catch (MongoWriteException ignored) {
        }

        var claimed = counters.findOneAndUpdate(
                and(
                        eq("_id", "last_map_decay"),
                        lte("time", now - dayMillis)
                ),
                Updates.set("time", now),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE)
        );

        if (claimed != null) {
            PLog.info("Map popularity degradation started.");
            decayPopularity(0.1);
            decayInterest(0.1);
        }
    }

    private boolean updateById(ObjectId id, org.bson.conversions.Bson update) {
        if (id == null || update == null || isReadOnly()) {
            return false;
        }

        return collection.updateOne(
                eq("_id", id),
                Updates.combine(
                        update,
                        Updates.set("updated_at", System.currentTimeMillis())
                )
        ).getMatchedCount() > 0;
    }
}
