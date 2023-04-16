package org.xcore.plugin.modules;


import arc.struct.ObjectMap;
import arc.struct.Seq;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.globalConfig;

public class Database {
    public static MongoClient mongoClient;
    public static MongoDatabase database;
    public static MongoCollection<PlayerData> playersCollection;
    public static MongoCollection<BanData> bansCollection;
    public static ObjectMap<String, PlayerData> cachedPlayerData = new ObjectMap<>();

    public static PlayerData getCached(String uuid) {
        return cachedPlayerData.get(uuid);
    }

    public static void setCached(PlayerData data) {
        cachedPlayerData.put(data.uuid, data);
    }

    public static void removeCached(String uuid) {
        cachedPlayerData.remove(uuid);
    }

    public static void init() {
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        mongoClient = MongoClients.create(globalConfig.mongoConnectionString);
        database = mongoClient.getDatabase("xcore").withCodecRegistry(pojoCodecRegistry);
        playersCollection = database.getCollection("players", PlayerData.class);
        bansCollection = database.getCollection("bans", BanData.class);
    }

    public static PlayerData getPlayerData(Player player) {
        return getPlayerData(player.uuid());
    }

    public static PlayerData getPlayerData(String uuid) {
        var data = playersCollection.find(eq("uuid", uuid)).first();

        if (data == null) {
            data = new PlayerData(uuid, false);
        }

        return data;
    }

    public static Seq<PlayerData> getPlayersData(Iterable<Administration.PlayerInfo> infos) {
        Seq<PlayerData> datas = new Seq<>();
        for (Administration.PlayerInfo info : infos) {
            PlayerData data = getPlayerData(info.id);

            if (data != null) {
                datas.add(data);
            }
        }

        return datas;
    }

    public static void setPlayerData(PlayerData data) {
        playersCollection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public static Seq<PlayerData> getLeaders(String... fields) {
        return Seq.with(playersCollection.find().sort(descending(fields)).limit(10));
    }

    // region ban

    public static BanData getBan(String uuid, String ip) {
        return bansCollection.find(getBanFilter(uuid, ip)).first();
    }

    public static void setBan(BanData data) {
        bansCollection.replaceOne(getBanFilter(data.uuid, data.ip), data, new ReplaceOptions().upsert(true));
    }

    public static void unBan(BanData data) {
        unBan(data.uuid, data.ip);
    }

    public static void unBan(String uuid, String ip) {
        bansCollection.deleteMany(getBanFilter(uuid, ip));
    }

    public static BanData getBanById(long id) {
        return bansCollection.find(eq("bid", id)).first();
    }

    public static BanData unBanById(long id) {
        return bansCollection.findOneAndDelete(eq("bid", id));
    }

    private static Bson getBanFilter(String uuid, String ip) {
        return and(or(eq("uuid", uuid), eq("ip", ip == null ? "no" : ip)),
                or(eq("server", config.server),
                        eq("server", "global")));
    }

    public static Seq<BanData> getBanned(boolean global) {
        Seq<BanData> bans = new Seq<>();
        bansCollection.find(global ? new Document() : eq("server", config.server)).forEach(bans::add);
        return bans;
    }

    // endregion

    @SuppressWarnings("DataFlowIssue")
    public static int getNextSequence(String name) {
        MongoCollection<Document> counters = database.getCollection("counters");

        Document find = new Document().append("_id", name);
        Document update = new Document("$inc", new Document("seq", 1));

        Document result = counters.findOneAndUpdate(find, update);

        return (int) result.get("seq");
    }
}
