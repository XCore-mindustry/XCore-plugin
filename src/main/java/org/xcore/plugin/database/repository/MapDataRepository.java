package org.xcore.plugin.database.repository;

import arc.struct.ObjectMap;
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
import org.xcore.plugin.model.MapData;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

@Singleton
public class MapDataRepository extends DataRepository<MapData> {

    @Inject
    public MapDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        super(database, "maps", MapData.class, secretsConfig);

        collection.createIndex(new Document("name", -1));
        collection.createIndex(new Document("author", -1));
        collection.createIndex(new Document("file_name", -1));
        collection.createIndex(new Document("game_mode", -1));
        collection.createIndex(new Document("popularity", -1));
        collection.createIndex(new Document("reputation", -1));
        collection.createIndex(new Document("interest", -1));
        collection.createIndex(new Document("played_times_year", 1));
    }

    public Optional<MapData> find(String name, String author, String gameMode) {
    return Optional.ofNullable(
        collection.find(and(
            eq("name", name),
            eq("author", author),
            eq("game_mode", gameMode)
        )).first()
    );
}

    public Optional<MapData> findByFileName(String fileName, String gameMode) {
        if (fileName == null || fileName.isBlank() || gameMode == null || gameMode.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                collection.find(and(
                        eq("file_name", fileName),
                        eq("game_mode", gameMode)
                )).first()
        );
    }

    public List<MapData> findAll() {
        return collection.find().into(new ArrayList<>());
    }

    public MapData findOrCreate(String name, String fileName, String author, String gameMode) {
        return findByFileName(fileName, gameMode)
                .or(() -> find(name, author, gameMode))
                .orElseGet(() -> {
                    MapData newData = new MapData(name, fileName, author, gameMode);
                    save(newData);
                    return newData;
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
