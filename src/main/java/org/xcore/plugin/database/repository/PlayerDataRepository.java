package org.xcore.plugin.database.repository;

import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
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
import java.util.Set;

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

    public boolean incrementPlayTime(String uuid, int delta) {
        return updateByUuid(uuid, Updates.inc("total_play_time", delta));
    }

    public boolean updateIp(String uuid, String ip) {
        return updateByUuid(uuid, Updates.set("last_ip", ip));
    }

    public boolean updateConnectionData(String uuid, String ip, String nickname) {
        return updateByUuid(uuid, Updates.combine(
                Updates.set("last_ip", ip),
                Updates.set("nickname", nickname)
        ));
    }

    public boolean updateAdminStatus(String uuid, boolean admin, boolean adminConfirmed) {
        return updateByUuid(uuid, Updates.combine(
                Updates.set("is_admin", admin),
                Updates.set("admin_confirmed", adminConfirmed)
        ));
    }

    public boolean updateLanguage(String uuid, String language) {
        return updateByUuid(uuid, Updates.set("local_language", language));
    }

    public boolean updateTranslatorLanguage(String uuid, String language) {
        return updateByUuid(uuid, Updates.set("translator_language", language));
    }

    public boolean updateLeaderboard(String uuid, boolean leaderboard) {
        return updateByUuid(uuid, Updates.set("leaderboard", leaderboard));
    }

    public boolean updateCustomNickname(String uuid, String customNickname) {
        return updateByUuid(uuid, Updates.set("custom_nickname", customNickname));
    }

    public boolean updateDescription(String uuid, String description) {
        return updateByUuid(uuid, Updates.set("description", description));
    }

    public boolean setActiveBadge(String uuid, String badgeId) {
        return updateByUuid(uuid, Updates.set("active_badge", badgeId));
    }

    public boolean addUnlockedBadge(String uuid, String badgeId) {
        return updateByUuid(uuid, Updates.addToSet("unlocked_badges", badgeId));
    }

    public boolean removeUnlockedBadge(String uuid, String badgeId) {
        return updateByUuid(uuid, Updates.pull("unlocked_badges", badgeId));
    }

    public boolean addBlockedPrivateUuid(String uuid, String blockedUuid) {
        return updateByUuid(uuid, Updates.addToSet("blocked_private_uuids", blockedUuid));
    }

    public boolean removeBlockedPrivateUuid(String uuid, String blockedUuid) {
        return updateByUuid(uuid, Updates.pull("blocked_private_uuids", blockedUuid));
    }

    public boolean updatePvpRating(String uuid, int rating) {
        return updateByUuid(uuid, Updates.set("pvp_rating", rating));
    }

    public boolean updateHexedProgress(String uuid, int rank, int points) {
        return updateByUuid(uuid, Updates.combine(
                Updates.set("hexed_rank", rank),
                Updates.set("hexed_points", points)
        ));
    }

    public boolean putMapVote(String uuid, String mapId, boolean like) {
        return updateByUuid(uuid, Updates.set("map_votes." + mapId, like));
    }

    public boolean putEventVote(String uuid, String eventId, boolean like) {
        return updateByUuid(uuid, Updates.set("event_votes." + eventId, like));
    }

    public boolean replaceUnlockedBadges(String uuid, Set<String> unlockedBadges) {
        return updateByUuid(uuid, Updates.set("unlocked_badges", unlockedBadges));
    }

    private boolean updateByUuid(String uuid, Bson update) {
        if (uuid == null || uuid.isBlank() || update == null || isReadOnly()) {
            return false;
        }

        return collection.updateOne(
                eq("uuid", uuid),
                Updates.combine(
                        update,
                        Updates.set("updated_at", System.currentTimeMillis())
                )
        ).getMatchedCount() > 0;
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
