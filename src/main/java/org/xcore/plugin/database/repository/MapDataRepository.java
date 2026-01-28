package org.xcore.plugin.database.repository;

import arc.struct.ObjectMap;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.xcore.plugin.model.MapData;

import java.util.ArrayList;

import static com.mongodb.client.model.Filters.*;

public class MapDataRepository {
    private final MongoCollection<MapData> collection;

    public MapDataRepository(MongoDatabase database) {
        this.collection = database.getCollection("maps", MapData.class);

        collection.createIndex(new Document("popularity", -1));
        collection.createIndex(new Document("reputation", -1));
        collection.createIndex(new Document("interest", -1));
        collection.createIndex(new Document("playedTimesYear", 1));
    }

    public MapData find(String mapName, String author, String gameMode) {
        var candidates = collection.find(and(eq("name", mapName), eq("gameMode", gameMode)))
                .into(new ArrayList<>());

        if (candidates.isEmpty()) {
            return new MapData(mapName, author, gameMode);
        }

        if (candidates.size() == 1) {
            MapData found = candidates.get(0);
            if (!found.author.equals(author)) {
                found.author = author;
                save(found);
            }
            return found;
        }

        for (MapData data : candidates) {
            if (data.author.equals(author)) {
                return data;
            }
        }

        return new MapData(mapName, author, gameMode);
    }

    public void save(MapData data) {
        if (data.id == null) {
            collection.insertOne(data);
        } else {
            collection.replaceOne(eq("_id", data.id), data, new ReplaceOptions().upsert(true));
        }
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
}
