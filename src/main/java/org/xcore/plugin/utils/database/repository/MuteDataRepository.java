package org.xcore.plugin.utils.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.xcore.plugin.utils.models.MuteData;

import static com.mongodb.client.model.Filters.eq;

public class MuteDataRepository {
    private final MongoCollection<MuteData> collection;

    public MuteDataRepository(MongoDatabase database) {
        this.collection = database.getCollection("mutes", MuteData.class);
    }

    public MuteData findByUuid(String uuid) {
        return collection.find(eq("uuid", uuid)).first();
    }

    public void save(MuteData data) {
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public void delete(String uuid) {
        collection.deleteOne(eq("uuid", uuid));
    }
}
