package org.xcore.plugin.database.repository;

import arc.util.Log;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.MongoUtils;
import org.xcore.plugin.database.PagedDataResult;
import org.xcore.plugin.model.BanData;
import org.bson.conversions.Bson;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

@Singleton
public class BanDataRepository extends DataRepository<BanData> {

    @Inject
    public BanDataRepository(MongoDatabase database, GlobalConfig globalConfig) {
        super(database, "bans", BanData.class, globalConfig);
    }

    public BanData find(String uuid, String ip) {
        var filter = identifierFilter(uuid, ip);
        if (filter == null) {
            return null;
        }
        return collection.find(filter).first();
    }

    @Override
    public boolean save(BanData data) {
        if (data == null) {
            return false;
        }
        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return false;
        }

        var filter = identifierFilter(data.uuid, data.ip);
        if (filter == null) {
            return false;
        }

        collection.replaceOne(filter, data, new ReplaceOptions().upsert(true));
        return true;
    }

    public boolean delete(String uuid, String ip) {
        var filter = identifierFilter(uuid, ip);
        if (filter == null) {
            return false;
        }
        return collection.deleteMany(filter).getDeletedCount() > 0;
    }

    public PagedDataResult<BanData> search(String value, int limit, int page) {
        return MongoUtils.search(collection, "name", value, limit, page);
    }

    public PagedDataResult<BanData> findAllPaged(int limit, int page) {
        long count = collection.countDocuments();
        if (count == 0) return null;

        int totalPages = (int) Math.ceil((double) count / limit);

        var data = MongoUtils.pagedData(new org.bson.BsonDocument(), collection, limit, page);

        return new PagedDataResult<>((int) count, totalPages, data);
    }

    public Iterable<BanData> findAll() {
        return collection.find();
    }

    private static Bson identifierFilter(String uuid, String ip) {
        if (uuid != null && ip != null) {
            return or(eq("uuid", uuid), eq("ip", ip));
        }
        if (uuid != null) {
            return eq("uuid", uuid);
        }
        if (ip != null) {
            return eq("ip", ip);
        }
        return null;
    }
}
