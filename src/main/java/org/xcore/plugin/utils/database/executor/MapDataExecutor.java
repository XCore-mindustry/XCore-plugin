package org.xcore.plugin.utils.database.executor;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.xcore.plugin.utils.models.MapData;

import java.util.Optional;
import java.util.ArrayList;

import static com.mongodb.client.model.Filters.*;

import arc.struct.ObjectMap;

public class MapDataExecutor extends Executor<MapData> {

    public MapDataExecutor(MongoCollection<MapData> collection) {
        super(collection);
    }

    public MapData get(String mapName, String author, String gameMode) {
        var candidates = collection.find(and(eq("name", mapName), eq("gameMode", gameMode)))
                                   .into(new ArrayList<>());

        if (candidates.isEmpty()) {
            return new MapData(mapName, author, gameMode);
        }

        if (candidates.size() == 1) {
            MapData found = candidates.get(0);

            if(!found.author.equals(author)) {
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

    @Override
    public void save(MapData data) {
        if (data.id == null) {
            collection.insertOne(data);
        } else {
            collection.replaceOne(eq("_id", data.id), data, new ReplaceOptions().upsert(true));
        }
    }

    public void delete(MapData data) {
        if(data.id != null) {
            collection.deleteOne(eq("_id", data.id));
        }
    }

    public ObjectMap<String, MapData> getAllAsMap() {
        ObjectMap<String, MapData> map = new ObjectMap<>();

        for (MapData data : collection.find()) {
            String key = genKey(data.name, data.author, data.gameMode);
            map.put(key, data);
        }
        return map;
    }

    public static String genKey(String name, String author, String mode) {
        return name + "|" + author + "|" + mode;
    }
}
