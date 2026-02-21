package org.xcore.plugin.database.repository;

import arc.util.Log;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.MuteData;

import static com.mongodb.client.model.Filters.eq;

@Singleton
public class MuteDataRepository extends DataRepository<MuteData> {

    @Inject
    public MuteDataRepository(MongoDatabase database, GlobalConfig globalConfig) {
        super(database,"mutes", MuteData.class, globalConfig);
    }

    public MuteData findByUuid(String uuid) {
        return collection.find(eq("uuid", uuid)).first();
    }

    @Override
    public boolean save(MuteData data) {
        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return false;
        }
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
        return true;
    }

    public void delete(String uuid) {
        collection.deleteOne(eq("uuid", uuid));
    }
}
