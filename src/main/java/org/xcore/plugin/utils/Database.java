package org.xcore.plugin.utils;


import arc.struct.ObjectMap;
import arc.struct.Seq;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mindustry.gen.Player;
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

    public static void setPlayerData(PlayerData data) {
        playersCollection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public static Seq<PlayerData> getLeaders(String column) {
        Seq<PlayerData> datas = new Seq<>();

        playersCollection.find(descending(column)).limit(10).forEach(datas::add);

        return datas;
    }

    public static BanData getBan(String uuid, String ip) {
        return bansCollection.find(getBanFilter(uuid, ip)).first();
    }

    public static UpdateResult setBan(BanData data) {
        return bansCollection.replaceOne(getBanFilter(data.uuid, data.ip), data, new ReplaceOptions().upsert(true));
    }

    public static DeleteResult unBan(BanData data) {
        return unBan(data.uuid, data.ip);
    }

    public static DeleteResult unBan(String uuid, String ip) {
        return bansCollection.deleteMany(getBanFilter(uuid, ip));
    }

    public static BanData getBanById(long id) {
        return bansCollection.find(eq("bid", id)).first();
    }

    private static Bson getBanFilter(String uuid, String ip) {
        return or(and(eq("uuid", uuid), eq("server", config.server)), and(eq("ip", ip), eq("server", config.server)));
    }

    public static Seq<BanData> getBanned() {
        Seq<BanData> bans = new Seq<>();
        bansCollection.find(eq("server", config.server)).forEach(bans::add);
        return bans;
    }
}
