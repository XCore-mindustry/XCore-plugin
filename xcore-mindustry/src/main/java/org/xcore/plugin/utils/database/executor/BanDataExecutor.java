package org.xcore.plugin.utils.database.executor;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.conversions.Bson;
import org.xcore.plugin.utils.models.BanData;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

public class BanDataExecutor extends PunishmentExecutor<BanData> {
    public BanDataExecutor(MongoCollection<BanData> collection) {
        super(collection);
    }

    public BanData get(String uuid, String ip) {
        return collection.find(getBanFilter(uuid, ip)).first();
    }

    @Override
    public void save(BanData data) {
        collection.replaceOne(getBanFilter(data.uuid, data.ip), data, new ReplaceOptions().upsert(true));
    }

    public void delete(String uuid, String ip) {
        collection.deleteMany(getBanFilter(uuid, ip));
    }
    public Bson getBanFilter(String uuid, String ip) {
        return or(eq("uuid", uuid), eq("ip", ip));
    }
}