package org.xcore.plugin.utils.database.repository;

import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import mindustry.gen.Player;
import org.bson.Document;
import org.xcore.plugin.utils.database.MongoUtils;
import org.xcore.plugin.utils.database.PagedDataResult;
import org.xcore.plugin.utils.models.PlayerData;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;

public class PlayerDataRepository {
    private final MongoCollection<PlayerData> collection;
    private final MongoCollection<Document> counters;

    public PlayerDataRepository(MongoDatabase database) {
        this.collection = database.getCollection("players", PlayerData.class);
        this.counters = database.getCollection("counters");

        // Индексы
        collection.createIndex(new Document("uuid", 1), new IndexOptions().unique(true));
        collection.createIndex(new Document("pid", 1));
        collection.createIndex(new Document("nickname", 1));
    }

    public void save(PlayerData data) {
        if (data.pid == 0) {
            data.pid = generatePid();
        }
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
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

    public PlayerData findById(int id) {
        return collection.find(eq("pid", id)).first();
    }

    public long deleteBots() {
        return collection.deleteMany(lt("totalPlayTime", 2)).getDeletedCount();
    }

    public PagedDataResult<PlayerData> search(String value, int limit, int page) {
        return MongoUtils.search(collection, "nickname", value, limit, page);
    }

    public Seq<PlayerData> findLeaders(String... fields) {
        return Seq.with(collection.find().sort(descending(fields)).limit(10));
    }
}
