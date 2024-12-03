package org.xcore.plugin.utils.database.executor;

import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import mindustry.gen.Player;
import org.xcore.plugin.utils.database.Database;
import org.xcore.plugin.utils.database.PagedDataResult;
import org.xcore.plugin.utils.models.PlayerData;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Sorts.descending;
import static org.xcore.plugin.PluginVars.database;

public class PlayerDataExecutor extends Executor<PlayerData> {
    public PlayerDataExecutor(MongoCollection<PlayerData> collection) {
        super(collection);
    }

    public PlayerData get(Player player) {
        return get(player.uuid());
    }

    public PlayerData get(String uuid) {
        return collection.find(eq("uuid", uuid)).first();
    }

    public PlayerData getById(int id) {
        return collection.find(eq("pid", id)).first();
    }

    @Override
    public void save(PlayerData data) {
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public long clearBots() {
        var result = collection.deleteMany(lt("totalPlayTime", 2));

        int pids = 0;
        for (PlayerData data : collection.find()) {
            pids++;
            data.pid = pids;
            save(data);
        }

        database.setCounter("player_id", pids);
        return result.getDeletedCount();
    }

    public PagedDataResult<PlayerData> search(String value, int limit, int page) {
        return Database.search(collection, "nickname", value, limit, page);
    }

    public Seq<PlayerData> getLeaders(String... fields) {
        return Seq.with(collection.find().sort(descending(fields)).limit(10));
    }

    public Seq<PlayerData> getAdmins() {
        return Seq.with(collection.find(eq("adminConfirmed", true)));
    }
}
