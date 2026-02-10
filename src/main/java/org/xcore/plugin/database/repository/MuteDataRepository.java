package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.model.MuteData;

import static com.mongodb.client.model.Filters.eq;

@Singleton
public class MuteDataRepository extends DataRepository<MuteData> {

    @Inject
    public MuteDataRepository(MongoDatabase database) {
        super(database,"mutes", MuteData.class);
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
