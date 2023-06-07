package org.xcore.plugin.utils.database;


import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Time;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.xcore.plugin.PluginVars;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;
import org.xcore.plugin.utils.models.PunishmentHistory;

import java.util.Optional;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.xcore.plugin.PluginVars.globalConfig;

public class Database {
    public final PlayerDataExecutor playerDataExecutor;
    public final BanDataExecutor banDataExecutor;
    public final PunishmentHistoryExecutor punishmentHistoryExecutor;

    public final MongoClient mongoClient;
    public final MongoDatabase database;

    public final ObjectMap<String, PlayerData> cachedPlayerData = new ObjectMap<>();

    public Database(String mongoConnectionString) {
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        mongoClient = MongoClients.create(mongoConnectionString);
        database = mongoClient.getDatabase("xcore").withCodecRegistry(pojoCodecRegistry);

        playerDataExecutor = new PlayerDataExecutor(database.getCollection("players", PlayerData.class));
        banDataExecutor = new BanDataExecutor(
                database.getCollection("bans", BanData.class));
        punishmentHistoryExecutor = new PunishmentHistoryExecutor(
                database.getCollection("punishments", PunishmentHistory.class));
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
        return Optional.ofNullable(cachedPlayerData.get(uuid)).orElseGet(() -> playerDataExecutor.getPlayerData(uuid));
    }

    public PlayerData getCachedOrDb(int id) {
        return Optional.ofNullable(getCached(id)).orElse(playerDataExecutor.getPlayerDataById(id));
    }

    public void setCached(PlayerData data) {
        cachedPlayerData.put(data.uuid, data);
    }

    public PlayerData removeCached(String uuid) {
        return cachedPlayerData.remove(uuid);
    }

    public PlayerDataExecutor getPlayerDataExecutor() {
        return playerDataExecutor;
    }

    public BanDataExecutor getBanDataExecutor() {
        return banDataExecutor;
    }

    @SuppressWarnings("DataFlowIssue")
    public int getNextSequence(String name) {
        MongoCollection<Document> counters = database.getCollection("counters");

        Document find = new Document().append("_id", name);
        Document update = new Document("$inc", new Document("seq", 1));

        Document result = counters.findOneAndUpdate(find, update);

        return (int) result.get("seq");
    }
}
