package org.xcore.plugin.utils.database.executor;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.xcore.plugin.utils.models.AdminData;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

public class AdminDataExecutor extends Executor<AdminData> {
    public AdminDataExecutor(MongoCollection<AdminData> collection) {
        super(collection);
    }

    public AdminData get(String uuid) {
        return Optional.ofNullable(collection.find(eq("uuid", uuid)).first())
                .orElse(new AdminData(uuid));
    }

    @Override
    public void save(AdminData data) {
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public void delete(String uuid) {
        collection.deleteOne(eq("uuid", uuid));
    }
}
