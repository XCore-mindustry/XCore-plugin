package org.xcore.plugin.utils.database;

import arc.math.Mathf;
import arc.struct.Seq;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import mindustry.gen.Player;
import org.xcore.plugin.utils.models.PlayerData;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;
import static com.mongodb.client.model.Sorts.descending;

public class PlayerDataExecutor {
    private final MongoCollection<PlayerData> collection;

    public PlayerDataExecutor(MongoCollection<PlayerData> collection) {
        this.collection = collection;
    }

    public PlayerData getPlayerData(Player player) {
        return getPlayerData(player.uuid());
    }

    public PlayerData getPlayerData(String uuid) {
        return Optional.ofNullable(collection.find(eq("uuid", uuid)).first())
                .orElse(new PlayerData(uuid, false));
    }

    public PlayerData getPlayerDataById(int id) {
        return collection.find(eq("pid", id)).first();
    }

    public void setPlayerData(PlayerData data) {
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public SearchResult search(String field, String value, int limit, int page) {
        var filter = regex(field, value, "i");

        int skips = (page - 1) * limit;

        long matchedDocs = collection.countDocuments(filter);
        if (matchedDocs == 0) return null;

        return new SearchResult((int) matchedDocs, Mathf.ceil((float) matchedDocs / limit), collection.find(filter)
                .skip(skips)
                .limit(limit));
    }

    public Seq<PlayerData> getLeaders(String... fields) {
        return Seq.with(collection.find().sort(descending(fields)).limit(10));
    }

    public Seq<PlayerData> getAdmins() {
        return Seq.with(collection.find(eq("adminConfirmed", true)));
    }

    public record SearchResult(int total, int totalPages, FindIterable<PlayerData> found) {
    }
}
