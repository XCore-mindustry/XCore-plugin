package org.xcore.plugin.modules.database;

import arc.func.Boolf;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import mindustry.gen.Groups;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.utils.VersionComparator;
import org.xcore.plugin.utils.database.repository.*;
import org.xcore.plugin.utils.models.*;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Singleton
public class DatabaseService {

    private final GlobalConfig globalConfig;

    @Getter private MongoDatabase database;
    @Getter private MongoClient client;

    @Getter private PlayerDataRepository playerDataRepository;
    @Getter private AdminDataRepository adminDataRepository;
    @Getter private BanDataRepository banDataRepository;
    @Getter private MuteDataRepository muteDataRepository;
    @Getter private MapDataRepository mapDataRepository;

    public final ObjectMap<String, PlayerData> cachedPlayerData = new ObjectMap<>();

    @Inject
    public DatabaseService(GlobalConfig globalConfig) {
        this.globalConfig = globalConfig;
    }

    @PostConstruct
    public void init() {
        Time.mark();

        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        client = MongoClients.create(globalConfig.mongoConnectionString);
        database = client.getDatabase("xcore").withCodecRegistry(pojoCodecRegistry);

        playerDataRepository = new PlayerDataRepository(database);
        adminDataRepository = new AdminDataRepository(database);
        banDataRepository = new BanDataRepository(database);
        muteDataRepository = new MuteDataRepository(database);
        mapDataRepository = new MapDataRepository(database);

        Log.info("Loaded database in @ms", Time.elapsed());
    }

    public PlayerData getCached(String uuid) {
        return cachedPlayerData.get(uuid);
    }

    public PlayerData getCachedOrDb(String uuid) {
        PlayerData cached = cachedPlayerData.get(uuid);
        return cached != null ? cached : playerDataRepository.findByUuid(uuid);
    }

    public PlayerData getCachedOrDb(int id) {
        for(var entry : cachedPlayerData) {
            if(entry.value.pid == id) return entry.value;
        }
        return playerDataRepository.findById(id);
    }

    public void setCached(PlayerData data) {
        cachedPlayerData.put(data.uuid, data);
    }

    public void reloadCache() {
        cachedPlayerData.clear();
        Groups.player.copy(new Seq<>()).each(p -> {
            var data = playerDataRepository.findByUuid(p.uuid());
            if (data != null) cachedPlayerData.put(data.uuid, data);
        });
    }

    public PlayerData removeCached(String uuid) {
        return cachedPlayerData.remove(uuid);
    }

    public void getCachedAdminTools(String version, Boolf<Integer> versionCompare, Cons<PlayerData> cons) {
        for (var data : cachedPlayerData.values()) {
            if (versionCompare.get(VersionComparator.compareVersions(data.adminModVersion, version))) {
                cons.get(data);
            }
        }
    }

    public void checkMapDecay() {
        var counters = database.getCollection("counters");
        var lastDecayDoc = counters.find(Filters.eq("_id", "last_map_decay")).first();

        long now = System.currentTimeMillis();
        long dayMillis = 24 * 60 * 60 * 1000L;

        if (lastDecayDoc == null) {
            counters.insertOne(new Document("_id", "last_map_decay").append("time", now));
            return;
        }

        long lastTime = lastDecayDoc.getLong("time");

        if (now - lastTime >= dayMillis) {
            Log.info("[XCore] Starting daily degradation of map popularity and interest...");
            mapDataRepository.decayPopularity(0.1);
            mapDataRepository.decayInterest(0.1);

            counters.updateOne(Filters.eq("_id", "last_map_decay"), Updates.set("time", now));
        }
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
