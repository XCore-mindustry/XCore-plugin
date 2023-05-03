package org.xcore.plugin.utils.database;

import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.xcore.plugin.utils.models.PlayerData;

import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
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

    public Seq<PlayerData> getPlayersData(Seq<Administration.PlayerInfo> players) {
        return players.map(p -> getPlayerData(p.id));
    }

    public void setPlayerData(PlayerData data) {
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public Seq<PlayerData> getLeaders(String... fields) {
        return Seq.with(collection.find().sort(descending(fields)).limit(10));
    }
}
