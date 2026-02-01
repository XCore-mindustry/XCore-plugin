package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.xcore.plugin.database.MongoUtils;
import org.xcore.plugin.database.PagedDataResult;
import org.xcore.plugin.model.BanData;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

@Singleton
public class BanDataRepository {
    private final MongoCollection<BanData> collection;

    @Inject
    public BanDataRepository(MongoDatabase database) {
        this.collection = database.getCollection("bans", BanData.class);
    }

    public BanData find(String uuid, String ip) {
        return collection.find(or(eq("uuid", uuid), eq("ip", ip))).first();
    }

    public void save(BanData data) {
        collection.replaceOne(or(eq("uuid", data.uuid), eq("ip", data.ip)), data, new ReplaceOptions().upsert(true));
    }

    public void delete(String uuid, String ip) {
        collection.deleteMany(or(eq("uuid", uuid), eq("ip", ip)));
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
}
