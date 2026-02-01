package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.model.MuteData;

import static com.mongodb.client.model.Filters.eq;

@Singleton
public class MuteDataRepository {
    private final MongoCollection<MuteData> collection;

    @Inject
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
