package org.xcore.plugin.utils.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.xcore.plugin.utils.models.AdminData;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

public class AdminDataExecutor {
    private final MongoCollection<AdminData> collection;

    public AdminDataExecutor(MongoCollection<AdminData> collection) {
        this.collection = collection;
    }

    public AdminData getAdminData(String uuid) {
        return Optional.ofNullable(collection.find(eq("uuid", uuid)).first())
                .orElse(new AdminData(uuid));
    }

    public void setAdminData(AdminData data) {
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }
}
