package org.xcore.plugin.modules;


import arc.struct.ObjectMap;
import arc.struct.Seq;

import arc.util.Log;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import mindustry.gen.Player;

import org.xcore.plugin.modules.models.PlayerData;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mongodb.client.model.Sorts.descending;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.xcore.plugin.PluginVars.globalConfig;

public class Database {
    public static MongoCollection<PlayerData> playersCollection;
    public static ObjectMap<String, PlayerData> cachedPlayerData = new ObjectMap<>();

    public static void init() {
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        MongoClient mongoClient = MongoClients.create(globalConfig.mongoConnectionString);
        MongoDatabase database = mongoClient.getDatabase("xcore").withCodecRegistry(pojoCodecRegistry);
        playersCollection = database.getCollection("players", PlayerData.class);

        playersCollection.find().forEach(data->Log.info(data.toString()));
    }

    public static PlayerData getPlayerData(Player player) {
        return getPlayerData(player.uuid());
    }

    public static PlayerData getPlayerData(String uuid) {
        var data = playersCollection.find(Filters.eq("uuid", uuid)).first();

        if (data == null) {
            data = new PlayerData(uuid, false);
        }

        return data;
    }

    public static void setPlayerData(PlayerData data) {
        playersCollection.replaceOne(Filters.eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public static Seq<PlayerData> getLeaders(String column) {
        Seq<PlayerData> datas = new Seq<>();

        playersCollection.find(descending(column)).limit(10).forEach(datas::add);

        return datas;
    }
}
