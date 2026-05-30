package org.xcore.plugin.database.repository;

import arc.util.Log;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.MuteData;

import static com.mongodb.client.model.Filters.eq;

@Singleton
public class MuteDataRepository extends DataRepository<MuteData> {

    @Inject
    public MuteDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        super(database,"mutes", MuteData.class, secretsConfig);
    }

    public MuteData findByUuid(String uuid) {
        return collection.find(eq("uuid", uuid)).first();
    }

    @Override
    public boolean save(MuteData data) {
        if (data == null) {
            return false;
        }
        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return false;
        }
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
        return true;
    }

    public boolean delete(String uuid) {
        if (uuid == null) {
            return false;
        }
        return collection.deleteOne(eq("uuid", uuid)).getDeletedCount() > 0;
    }
}
