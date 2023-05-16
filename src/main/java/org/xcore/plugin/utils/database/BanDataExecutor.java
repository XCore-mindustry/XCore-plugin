package org.xcore.plugin.utils.database;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.Threads;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.changestream.FullDocumentBeforeChange;
import com.mongodb.client.model.changestream.OperationType;
import org.bson.conversions.Bson;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.models.BanData;

import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import static org.xcore.plugin.PluginVars.gson;

public class BanDataExecutor {
    private final MongoCollection<BanData> collection;

    public BanDataExecutor(MongoCollection<BanData> collection) {
        this.collection = collection;

        collection.createIndex(Indexes.ascending("unbanDate"), new IndexOptions().expireAfter(0L, TimeUnit.MILLISECONDS));

        if (SockCommunicator.isSocketServer()) {
            Threads.thread(() -> collection.watch()
                    .fullDocumentBeforeChange(FullDocumentBeforeChange.REQUIRED)
                    .forEach(event -> {
                        if (event.getOperationType() != OperationType.DELETE) return;

                        BanData data = event.getFullDocumentBeforeChange();
                        if (data == null) return;

                        Bot.sendUnban(data);
                    }));
        }
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
}