package org.xcore.plugin.utils.database;

import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.conversions.Bson;
import org.xcore.plugin.utils.models.IPBanData;
import org.xcore.plugin.utils.models.UUIDBanData;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static org.xcore.plugin.PluginVars.config;

public class BanDataExecutor {
    private final MongoCollection<UUIDBanData> uuidCollection;
    private final MongoCollection<IPBanData> ipCollection;

    public BanDataExecutor(MongoCollection<UUIDBanData> uuidCollection, MongoCollection<IPBanData> ipCollection) {
        this.uuidCollection = uuidCollection;
        this.ipCollection = ipCollection;
    }

    // region uuid ban
    public UUIDBanData getUUIDBan(String uuid) {
        return uuidCollection.find(getUUIDBanFilter(uuid)).first();
    }

    public void saveUUIDBan(UUIDBanData data) {
        uuidCollection.replaceOne(getUUIDBanFilter(data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public void deleteUUIDBan(String uuid) {
        uuidCollection.deleteOne(getUUIDBanFilter(uuid));
    }

    public Seq<UUIDBanData> getUUIDBanned() {
        return Seq.with(uuidCollection.find(eq("server", config.server)));
    }

    public Bson getUUIDBanFilter(String uuid) {
        return and(eq("uuid", uuid), eq("server", config.server));
    }

    // endregion
    // region ip ban
    public IPBanData getIPBan(String ip) {
        return ipCollection.find(getIPBanFilter(ip)).first();
    }

    public void saveIPBan(IPBanData data) {
        ipCollection.replaceOne(getIPBanFilter(data.ip), data, new ReplaceOptions().upsert(true));
    }

    public void deleteIPBan(String ip) {
        ipCollection.deleteOne(getIPBanFilter(ip));
    }

    public Seq<IPBanData> getIPBanned() {
        return Seq.with(ipCollection.find());
    }

    public Bson getIPBanFilter(String ip) {
        return eq("ip", ip);
    }
    // endregion
}