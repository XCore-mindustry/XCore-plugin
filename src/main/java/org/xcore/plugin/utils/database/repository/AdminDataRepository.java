package org.xcore.plugin.utils.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.xcore.plugin.utils.models.AdminData;

import static com.mongodb.client.model.Filters.eq;

public class AdminDataRepository {
    private final MongoCollection<AdminData> collection;

    public AdminDataRepository(MongoDatabase database) {
        this.collection = database.getCollection("admins", AdminData.class);
    }

    public AdminData findByUuid(String uuid) {
        var found = collection.find(eq("uuid", uuid)).first();
        return found != null ? found : new AdminData(uuid);
    }

    public void save(AdminData data) {
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public void delete(String uuid) {
        collection.deleteOne(eq("uuid", uuid));
    }
}
