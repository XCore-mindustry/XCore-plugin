package org.xcore.plugin.database.repository;

import arc.struct.ObjectMap;
import arc.util.Log;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.xcore.plugin.model.MapData;

import java.util.Optional;

import static com.mongodb.client.model.Filters.*;

@Singleton
public class MapDataRepository extends DataRepository<MapData> {

    @Inject
    public MapDataRepository(MongoDatabase database) {
        super(database, "maps", MapData.class);

        collection.createIndex(new Document("name", -1));
        collection.createIndex(new Document("author", -1));
        collection.createIndex(new Document("gameMode", -1));
        collection.createIndex(new Document("popularity", -1));
        collection.createIndex(new Document("reputation", -1));
        collection.createIndex(new Document("interest", -1));
        collection.createIndex(new Document("playedTimesYear", 1));
    }

    public Optional<MapData> find(String name, String author, String gameMode) {
    return Optional.ofNullable(
        collection.find(and(
            eq("name", name),
            eq("author", author),
            eq("gameMode", gameMode)
        )).first()
    );
}

    public MapData findOrCreate(String name, String fileName, String author, String gameMode) {
        return find(name, author, gameMode).orElseGet(() -> {

            MapData existing = collection.find(and(eq("name", name), eq("gameMode", gameMode))).first();

            if (existing != null) {
                existing.author = author;
                existing.fileName = fileName;
                save(existing);
                return existing;
            }

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
        var lastDecayDoc = counters.find(eq("_id", "last_map_decay")).first();

        long now = System.currentTimeMillis();
        long dayMillis = 24 * 60 * 60 * 1000L;

        if (lastDecayDoc == null) {
            counters.insertOne(new Document("_id", "last_map_decay").append("time", now));
            return;
        }

        long lastTime = lastDecayDoc.getLong("time");

        if (now - lastTime >= dayMillis) {
            Log.info("[XCore] Starting daily degradation of map popularity and interest...");
            decayPopularity(0.1);
            decayInterest(0.1);

            counters.updateOne(eq("_id", "last_map_decay"), Updates.set("time", now));
        }
    }
}
