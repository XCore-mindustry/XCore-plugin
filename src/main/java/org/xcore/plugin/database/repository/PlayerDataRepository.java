package org.xcore.plugin.database.repository;

import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.MongoUtils;
import org.xcore.plugin.database.PagedDataResult;
import org.xcore.plugin.model.PlayerData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Sorts.descending;

@Singleton
public class PlayerDataRepository extends DataRepository<PlayerData> {
    private final MongoCollection<Document> counters;

    @Inject
    public PlayerDataRepository(MongoDatabase database, GlobalConfig globalConfig) {

        super(database, "players", PlayerData.class, globalConfig);
        this.counters = database.getCollection("counters");

        collection.createIndex(new Document("uuid", 1), new IndexOptions().unique(true));
        collection.createIndex(new Document("pid", 1));
        collection.createIndex(new Document("nickname", 1));
    }

    @Override
    public boolean save(PlayerData data) {
        if (data.pid == -1 && !isReadOnly()) {
            data.pid = generatePid();
        }

        if (data.id == null && data.uuid != null && !data.uuid.isBlank()) {
            PlayerData existing = findByUuid(data.uuid);
            if (existing != null && existing.id != null) {
                data.id = existing.id;
            }
        }

        return super.save(data);
    }

    private int generatePid() {
        Document find = new Document("_id", "player_id");
        Document update = new Document("$inc", new Document("seq", 1));
        var options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        Document result = counters.findOneAndUpdate(find, update, options);
        return result != null ? result.getInteger("seq") : 1;
    }

    public PlayerData findByPlayer(Player player) {
        return findByUuid(player.uuid());
    }

    public PlayerData findByUuid(String uuid) {
        return collection.find(eq("uuid", uuid)).first();
    }

    public PlayerData findByPid(int id) {
        return collection.find(eq("pid", id)).first();
    }

    public Bson getQuery(Map<String, StatusEnum> filters) {
        List<Bson> pipeline = new ArrayList<>();
        if (filters != null) {
            StatusEnum admin = filters.get("admin");
            if (admin == StatusEnum.Active) pipeline.add(Filters.eq("is_admin", true));
            if (admin == StatusEnum.Inactive) pipeline.add(Filters.eq("is_admin", false));
        }
        return pipeline.isEmpty() ? new Document() : Filters.and(pipeline);
    }

    public List<PlayerData> findPage(int skip, int limit, Map<String, StatusEnum> filters) {
        return collection.find(getQuery(filters))
                .sort(new Document("pid", -1).append("_id", 1))
                .skip(skip)
                .limit(limit)
                .into(new ArrayList<>());
    }

    public long count(Map<String, StatusEnum> filters) {
        return collection.countDocuments(getQuery(filters));
    }

    public long deleteBots() {
        return collection.deleteMany(lt("total_play_time", 2)).getDeletedCount();
    }

    public PagedDataResult<PlayerData> search(String value, int limit, int page) {
        return MongoUtils.search(collection, "nickname", value, limit, page);
    }

    public Seq<PlayerData> findLeaders(String... fields) {
        return Seq.with(collection.find().sort(descending(fields)).limit(10));
    }
}
