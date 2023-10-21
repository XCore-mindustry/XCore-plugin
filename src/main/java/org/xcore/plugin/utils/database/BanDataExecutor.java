package org.xcore.plugin.utils.database;

import arc.math.Mathf;
import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.xcore.plugin.utils.models.BanData;

import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;

public class BanDataExecutor {
    private final MongoCollection<BanData> collection;

    public BanDataExecutor(MongoCollection<BanData> collection) {
        this.collection = collection;

        collection.createIndex(Indexes.ascending("unbanDate"), new IndexOptions().expireAfter(0L, TimeUnit.MILLISECONDS));
    }

    public BanData getBan(String uuid, String ip) {
        return collection.find(getBanFilter(uuid, ip)).first();
    }

    public void saveBan(BanData data) {
        collection.replaceOne(getBanFilter(data.uuid, data.ip), data, new ReplaceOptions().upsert(true));
    }

    public void deleteBan(String uuid, String ip) {
        collection.deleteMany(getBanFilter(uuid, ip));
    }

    public Seq<BanData> getBanned() {
        return Seq.with(collection.find());
    }

    public Bson getBanFilter(String uuid, String ip) {
        return or(eq("uuid", uuid), eq("ip", ip));
    }

    public PagedDataResult<BanData> search(String value, int limit, int page) {
        return Database.search(collection, "name", value, limit, page);
    }

    public PagedDataResult<BanData> pagedData(int limit, int page) {
        int count = (int) collection.countDocuments();
        if (count == 0) return null;

        return new PagedDataResult<>(
                count,
                Mathf.ceil((float) count / limit),
                Database.pagedData(new BsonDocument(), collection, limit, page));
    }
}