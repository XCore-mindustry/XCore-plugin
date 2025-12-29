package org.xcore.plugin.utils.database;


import arc.func.Boolf;
import arc.func.Cons;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.IndexOptions;
import lombok.Getter;
import mindustry.gen.Groups;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.xcore.plugin.PluginVars;
import org.xcore.plugin.utils.database.executor.*;
import org.xcore.plugin.utils.models.*;

import java.util.Optional;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mongodb.client.model.Filters.regex;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.xcore.plugin.PluginVars.globalConfig;
import static org.xcore.plugin.utils.Utils.compareVersions;

public class Database {
    @Getter
    public final PlayerDataExecutor playerDatas;
    @Getter
    public final AdminDataExecutor adminDatas;
    @Getter
    public final BanDataExecutor banDatas;
    @Getter
    public final PunishmentExecutor<MuteData> muteDatas;
    @Getter
    public final MapDataExecutor mapDatas;

    public final MongoClient mongoClient;
    public final MongoDatabase database;

    public final ObjectMap<String, PlayerData> cachedPlayerData = new ObjectMap<>();

    public Database(String mongoConnectionString) {
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        mongoClient = MongoClients.create(mongoConnectionString);
        database = mongoClient.getDatabase("xcore").withCodecRegistry(pojoCodecRegistry);

        playerDatas = new PlayerDataExecutor(database.getCollection("players", PlayerData.class));
        adminDatas = new AdminDataExecutor(database.getCollection("admins", AdminData.class));
        banDatas = new BanDataExecutor(database.getCollection("bans", BanData.class));
        muteDatas = new PunishmentExecutor<>(database.getCollection("mutes", MuteData.class));
        mapDatas = new MapDataExecutor(database.getCollection("maps", MapData.class));

        playerDatas.getCollection().createIndex(new Document("uuid", 1), new IndexOptions().unique(true));
        playerDatas.getCollection().createIndex(new Document("pid", 1));
        playerDatas.getCollection().createIndex(new Document("nickname", 1));

        mapDatas.getCollection().createIndex(new Document("popularity", -1));
        mapDatas.getCollection().createIndex(new Document("reputation", -1));
        mapDatas.getCollection().createIndex(new Document("interest", -1));
        mapDatas.getCollection().createIndex(new Document("playedTimesYear", 1));
    }

    public static void init() {
        Time.mark();
        PluginVars.database = new Database(globalConfig.mongoConnectionString);
        Log.info("Loaded database in @ms", Time.elapsed());
    }

    public PlayerData getCached(String uuid) {
        return cachedPlayerData.get(uuid);
    }

    public PlayerData getCached(int id) {
        for (var data : cachedPlayerData.values())
            if (data.pid == id) return data;

        return null;    
    }

    public PlayerData getCachedOrDb(String uuid) {
        return Optional.ofNullable(cachedPlayerData.get(uuid)).orElseGet(() -> playerDatas.get(uuid));
    }

    public PlayerData getCachedOrDb(int id) {
        return Optional.ofNullable(getCached(id)).orElseGet(() -> playerDatas.getById(id));
    }

    public void setCached(PlayerData data) {
        cachedPlayerData.put(data.uuid, data);
    }

    public void getCachedAdminTools(String version, Boolf<Integer> versionCompare, Cons<PlayerData> cons) {
        for (var data : cachedPlayerData.values())
            if (versionCompare.get(compareVersions(data.adminModVersion, version))) cons.get(data);
    }

    public void reloadCache() {
        cachedPlayerData.clear();
        Groups.player.copy(new Seq<>()).map(playerDatas::get).each(data -> { if (data != null) cachedPlayerData.put(data.uuid, data);});
    }

    public PlayerData removeCached(String uuid) {
        return cachedPlayerData.remove(uuid);
    }

    public static <I> PagedDataResult<I> search(MongoCollection<I> collection, String field, String value, int limit, int page) {
        var filter = regex(field, value, "i");

        long matchedDocs = collection.countDocuments(filter);
        if (matchedDocs == 0) return null;

        return new PagedDataResult<>((int) matchedDocs, Mathf.ceil((float) matchedDocs / limit), pagedData(
                filter, collection, limit, page
        ));
    }

    public static <I> FindIterable<I> pagedData(Bson filter, MongoCollection<I> collection, int limit, int page) {
        int skips = (page - 1) * limit;

        return collection.find(filter)
                .skip(skips)
                .limit(limit);
    }

    @SuppressWarnings("DataFlowIssue")
    public int getNextSequence(String name) {
        MongoCollection<Document> counters = database.getCollection("counters");

        Document find = new Document("_id", name);
        Document update = new Document("$inc", new Document("seq", 1));

        var options = new com.mongodb.client.model.FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(com.mongodb.client.model.ReturnDocument.AFTER);

        Document result = counters.findOneAndUpdate(find, update, options);

        return result.getInteger("seq");
    }

    public void setCounter(String name, int value) {
        MongoCollection<Document> counters = database.getCollection("counters");

        Document find = new Document().append("_id", name);
        Document update = new Document("$set", new Document("seq", value));

        counters.findOneAndUpdate(find, update);
    }

    public void checkMapDecay() {
        MongoCollection<Document> counters = database.getCollection("counters");
        Document lastDecayDoc = counters.find(Filters.eq("_id", "last_map_decay")).first();

        long now = System.currentTimeMillis();
        long dayMillis = 24 * 60 * 60 * 1000L;

        if (lastDecayDoc == null) {
            counters.insertOne(new Document("_id", "last_map_decay").append("time", now));
            return;
        }

        long lastTime = lastDecayDoc.getLong("time");

        if (now - lastTime >= dayMillis) {
            Log.info("[XCore] Starting daily degradation of map popularity and interest...");
            mapDatas.decayPopularity(0.1);
            mapDatas.decayiInterest(0.1);

            counters.updateOne(Filters.eq("_id", "last_map_decay"), Updates.set("time", now));
        }
    }
}
