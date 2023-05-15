package org.xcore.plugin.utils.database;


import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Time;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.xcore.plugin.PluginVars;
import org.xcore.plugin.utils.models.IPBanData;
import org.xcore.plugin.utils.models.PlayerData;
import org.xcore.plugin.utils.models.UUIDBanData;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.xcore.plugin.PluginVars.globalConfig;

public class Database {
    public final PlayerDataExecutor playerDataExecutor;
    public final BanDataExecutor banDataExecutor;

    public final MongoClient mongoClient;
    public final MongoDatabase database;

    public ObjectMap<String, PlayerData> cachedPlayerData = new ObjectMap<>();

    public Database(String mongoConnectionString) {
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        mongoClient = MongoClients.create(mongoConnectionString);
        database = mongoClient.getDatabase("xcore").withCodecRegistry(pojoCodecRegistry);

        playerDataExecutor = new PlayerDataExecutor(database.getCollection("players", PlayerData.class));
        banDataExecutor = new BanDataExecutor(
                database.getCollection("uuid_bans", UUIDBanData.class),
                database.getCollection("ip_bans", IPBanData.class));
    }

    public static void init() {
        Time.mark();
        PluginVars.database = new Database(globalConfig.mongoConnectionString);
        Log.info("Loaded database in @ms", Time.elapsed());
    }

    public PlayerData getCached(String uuid) {
        return cachedPlayerData.get(uuid);
    }

    public PlayerData getCachedOrDb(String uuid) {
        return cachedPlayerData.get(uuid, () -> playerDataExecutor.getPlayerData(uuid));
    }

    public void setCached(PlayerData data) {
        cachedPlayerData.put(data.uuid, data);
    }

    public void removeCached(String uuid) {
        cachedPlayerData.remove(uuid);
    }

    public PlayerDataExecutor getPlayerDataExecutor() {
        return playerDataExecutor;
    }

    public BanDataExecutor getBanDataExecutor() {
        return banDataExecutor;
    }
}
