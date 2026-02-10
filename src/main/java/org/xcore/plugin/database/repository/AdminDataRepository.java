package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.model.AdminData;

import static com.mongodb.client.model.Filters.eq;

@Singleton
public class AdminDataRepository extends DataRepository<AdminData> {

    @Inject
    public AdminDataRepository(MongoDatabase database) {
        super(database, "admins", AdminData.class);
    }

    public AdminData findByUuid(String uuid) {
        var found = collection.find(eq("uuid", uuid)).first();
        return found != null ? found : new AdminData(uuid);
    }

    public void delete(String uuid) {
        collection.deleteOne(eq("uuid", uuid));
    }
}
