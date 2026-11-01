package org.xcore.plugin.database.repository;

import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.xcore.plugin.database.MongoAsync;
import org.xcore.plugin.database.ReactiveMongoStore;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.MongoUtils;
import org.xcore.plugin.database.PagedDataResult;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.LeaderboardSlice;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.model.enums.TopCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Sorts.orderBy;

@Singleton
public class PlayerDataRepository extends DataRepository<PlayerData> {
    private final MongoCollection<Document> counters;
    private final com.mongodb.reactivestreams.client.MongoCollection<Document> reactiveCounters;

    @Inject
    public PlayerDataRepository(
            MongoDatabase database,
            ReactiveMongoStore reactiveMongoStore,
            TomlSecretsConfig secretsConfig) {

        super(database, reactiveMongoStore, "players", PlayerData.class, secretsConfig);
        this.counters = database.getCollection("counters");
        this.reactiveCounters = reactiveMongoStore != null ? reactiveMongoStore.collection("counters", Document.class) : null;

        collection.createIndex(new Document("uuid", 1), new IndexOptions().unique(true));
        collection.createIndex(new Document("pid", 1));
        collection.createIndex(new Document("username", 1), new IndexOptions().unique(true)
                        .partialFilterExpression(Filters.gt("username", ""))
                        .collation(com.mongodb.client.model.Collation.builder()
                                .locale("en")
                                .collationStrength(com.mongodb.client.model.CollationStrength.SECONDARY).build())
        );
        collection.createIndex(new Document("nickname", 1));
        collection.createIndex(new Document("discord_id", 1));
        collection.createIndex(new Document("total_play_time", -1).append("pid", 1));
        collection.createIndex(new Document("hexed_rank", -1).append("hexed_points", -1).append("pid", 1));
    }

    public PlayerDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        this(database, null, secretsConfig);
    }

    @Override
    public boolean save(PlayerData data) {
        return save(null, data);
    }

    @Override
    public boolean save(com.mongodb.client.ClientSession session, PlayerData data) {
        if (data == null || isReadOnly()) return false;
        if (!PlayerPids.isAssigned(data.pid)) data.pid = generatePid(session);
        Bson update = playerSaveUpdate(data);
        boolean persisted = data.id != null;
        var options = new com.mongodb.client.model.UpdateOptions().upsert(!persisted);
        var result = session != null ? collection.updateOne(session, saveFilter(data), update, options)
                : collection.updateOne(saveFilter(data), update, options);
        if (persisted) return result.getMatchedCount() == 1;
        if (result.getUpsertedId() != null) data.id = result.getUpsertedId().asObjectId().getValue();
        else {
            PlayerData stored = findByUuid(session, data.uuid);
            if (stored != null) data.id = stored.id;
        }
        return true;
    }

    /** A persisted snapshot only updates its own document, so a stale copy of a merged account cannot re-insert its pid. */
    private static Bson saveFilter(PlayerData data) {
        return data.id == null ? eq("uuid", data.uuid) : Filters.and(eq("_id", data.id), eq("uuid", data.uuid));
    }

    @Override
    public java.util.concurrent.CompletionStage<Boolean> saveAsync(PlayerData data) {
        if (data == null || isReadOnly()) return java.util.concurrent.CompletableFuture.completedFuture(false);
        if (reactiveCollection == null) return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("Reactive MongoDB store is required for saveAsync"));
        var pid = !PlayerPids.isAssigned(data.pid) ? generatePidAsync()
                : java.util.concurrent.CompletableFuture.completedFuture(data.pid);
        return pid.thenCompose(value -> {
            data.pid = value;
            boolean persisted = data.id != null;
            Bson update = playerSaveUpdate(data);
            return MongoAsync.first(reactiveCollection.updateOne(saveFilter(data), update,
                    new com.mongodb.client.model.UpdateOptions().upsert(!persisted))).thenCompose(result -> {
                if (persisted) return java.util.concurrent.CompletableFuture.completedFuture(result.getMatchedCount() == 1);
                if (result.getUpsertedId() != null) {
                    data.id = result.getUpsertedId().asObjectId().getValue();
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                }
                return findByUuidAsync(data.uuid).thenApply(stored -> {
                    if (stored != null) data.id = stored.id;
                    return true;
                });
            });
        });
    }

    private Bson playerSaveUpdate(PlayerData data) {
        if (data.uuid == null || data.uuid.isBlank()) throw new IllegalArgumentException("Player UUID is required");
        if (data.createdModelTime == 0) data.createdModelTime = System.currentTimeMillis();
        data.editModelTime = System.currentTimeMillis();
        var document = new org.bson.BsonDocument();
        collection.getCodecRegistry().get(PlayerData.class).encode(new org.bson.BsonDocumentWriter(document), data,
                org.bson.codecs.EncoderContext.builder().build());
        document.remove("_id");
        // Bindings belong exclusively to account operations; never copy a cached binding into Mongo.
        document.keySet().removeIf(key -> key.startsWith("discord_"));
        return new org.bson.BsonDocument("$set", document);
    }

    /**
     * Administrative merge is the explicit exception to ordinary saves preserving account bindings.
     * {@code before} is the snapshot the merge was computed from; a binding changed since then rejects the write.
     */
    public boolean saveMergedPlayer(com.mongodb.client.ClientSession session, PlayerData before, PlayerData data) {
        if (data == null || before == null || before.uuid == null || before.uuid.isBlank() || isReadOnly()) return false;
        String originalUuid = before.uuid;
        var update = (org.bson.BsonDocument) playerSaveUpdate(data);
        var fields = update.getDocument("$set");
        fields.put("discord_id", new org.bson.BsonString(data.discordId == null ? "" : data.discordId));
        fields.put("discord_username", new org.bson.BsonString(data.discordUsername == null ? "" : data.discordUsername));
        fields.put("discord_linked_at", new org.bson.BsonInt64(data.discordLinkedAt));
        fields.put("password_hash", new org.bson.BsonString(data.password == null ? "" : data.password));
        update.put("$max", new org.bson.BsonDocument("discord_link_generation", new org.bson.BsonInt64(data.discordLinkedAt)));
        // Legacy documents may lack discord fields entirely; eq(null) matches missing and null.
        var filter = Filters.and(eq("uuid", originalUuid),
                before.discordId == null || before.discordId.isBlank()
                        ? or(eq("discord_id", ""), eq("discord_id", null)) : eq("discord_id", before.discordId),
                before.discordLinkedAt == 0L
                        ? or(eq("discord_linked_at", 0L), eq("discord_linked_at", null)) : eq("discord_linked_at", before.discordLinkedAt));
        var result = session == null ? collection.updateOne(filter, update)
                : collection.updateOne(session, filter, update);
        return result.getMatchedCount() == 1;
    }

    public java.util.concurrent.CompletionStage<Integer> generatePidAsync() {
        if (reactiveCounters == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for generatePidAsync"));
        }
        Document find = new Document("_id", "player_id");
        Document update = new Document("$inc", new Document("seq", 1));
        var options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        return MongoAsync.first(reactiveCounters.findOneAndUpdate(find, update, options))
                .thenApply(doc -> doc != null ? doc.getInteger("seq", 1) : 1);
    }

    private int generatePid() {
        return generatePid(null);
    }

    private int generatePid(com.mongodb.client.ClientSession session) {
        Document find = new Document("_id", "player_id");
        Document update = new Document("$inc", new Document("seq", 1));
        var options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        Document result = session != null
                ? counters.findOneAndUpdate(session, find, update, options)
                : counters.findOneAndUpdate(find, update, options);
        return result != null ? result.getInteger("seq") : 1;
    }

    public PlayerData findByPlayer(Player player) {
        return findByUuid(player.uuid());
    }

    public PlayerData findByUuid(String uuid) {
        return findByUuid(null, uuid);
    }

    public PlayerData findByUuid(com.mongodb.client.ClientSession session, String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        if (session != null) {
            return collection.find(session, eq("uuid", uuid)).first();
        }
        return collection.find(eq("uuid", uuid)).first();
    }

    public List<PlayerData> findByUuids(java.util.Collection<String> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return List.of();
        }
        return collection.find(Filters.in("uuid", uuids)).into(new ArrayList<>());
    }

    /**
     * The language a player selected in settings ({@code auto} when unset), or {@code null} for an
     * unknown player. Reads only that field.
     */
    public String findLanguage(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        PlayerData data = collection.find(eq("uuid", uuid))
                .projection(Projections.include("local_language"))
                .first();
        return data == null ? null : data.language;
    }

    public java.util.concurrent.CompletionStage<PlayerData> findByUuidAsync(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for findByUuidAsync"));
        }
        return MongoAsync.first(reactiveCollection.find(eq("uuid", uuid)));
    }

    public PlayerData findByPid(int id) {
        return collection.find(eq("pid", id)).first();
    }

    public java.util.concurrent.CompletionStage<PlayerData> findByPidAsync(int id) {
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for findByPidAsync"));
        }
        return MongoAsync.first(reactiveCollection.find(eq("pid", id)));
    }

    public PlayerData findByUsername(String username) {
        if (username == null || username.isBlank()) return null;
        return collection.find(Filters.eq("username", username))
                .collation(com.mongodb.client.model.Collation.builder()
                        .locale("en")
                        .collationStrength(com.mongodb.client.model.CollationStrength.SECONDARY)
                        .build())
                .first();
    }

    public java.util.concurrent.CompletionStage<PlayerData> findByUsernameAsync(String username) {
        if (username == null || username.isBlank()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for findByUsernameAsync"));
        }

        return MongoAsync.first(
                reactiveCollection.find(Filters.eq("username", username))
                        .collation(com.mongodb.client.model.Collation.builder()
                                .locale("en")
                                .collationStrength(com.mongodb.client.model.CollationStrength.SECONDARY)
                                .build())
        );
    }

    /**
     * Marks a player as online (or offline) so external services - such as the Discord
     * bot account merge - can refuse to touch an account that is currently in a game.
     */
    public java.util.concurrent.CompletionStage<Boolean> updateOnlineAsync(String uuid, boolean online, String server) {
        if (uuid == null || uuid.isBlank() || isReadOnly()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }

        return updateByUuidAsync(uuid, Updates.combine(
                Updates.set("online", online),
                Updates.set("online_since", online ? System.currentTimeMillis() : 0L),
                Updates.set("online_server", online && server != null ? server : "")
        ));
    }

    /**
     * Clears every {@code online} flag left behind by a previous server process.
     * Called once on plugin startup, before any session is registered.
     */
    public java.util.concurrent.CompletionStage<Long> clearOnlineFlagsAsync() {
        if (reactiveCollection == null || isReadOnly()) {
            return java.util.concurrent.CompletableFuture.completedFuture(0L);
        }

        return MongoAsync.first(reactiveCollection.updateMany(
                eq("online", true),
                Updates.combine(
                        Updates.set("online", false),
                        Updates.set("online_since", 0L),
                        Updates.set("online_server", "")
                )
        )).thenApply(result -> result == null ? 0L : result.getModifiedCount());
    }

    public List<PlayerData> findByDiscordId(String discordId) {
        if (discordId == null || discordId.isBlank()) {
            return List.of();
        }

        return collection.find(eq("discord_id", discordId)).into(new ArrayList<>());
    }

    public PlayerData findByDiscordLinkCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return collection.find(eq("discord_link_code", code)).first();
    }

    public boolean updateDiscordLink(String uuid, String discordId, String discordUsername, long linkedAt) {
        return writeDiscordLink(uuid, null, discordId, discordUsername, linkedAt) != null;
    }

    public PlayerData updateDiscordLink(String uuid, int expectedPid, String discordId, String discordUsername, long linkedAt) {
        return writeDiscordLink(uuid, expectedPid, discordId, discordUsername, linkedAt);
    }

    private PlayerData writeDiscordLink(String uuid, Integer expectedPid, String discordId, String discordUsername, long linkedAt) {
        if (uuid == null || uuid.isBlank() || discordId == null || discordId.isBlank() || isReadOnly()) return null;
        var filter = Filters.and(eq("uuid", uuid), or(eq("discord_id", ""), Filters.exists("discord_id", false)));
        if (expectedPid != null) filter = Filters.and(filter, eq("pid", expectedPid));
        // Preserve a monotonic timestamp across unlink/relink, including different servers in the same millisecond.
        var generation = new Document("$max", List.of(linkedAt,
                new Document("$add", List.of(new Document("$ifNull", List.of("$discord_link_generation", 0L)), 1L))));
        var values = new Document("discord_id", new Document("$literal", discordId))
                .append("discord_username", new Document("$literal", discordUsername == null ? "" : discordUsername))
                .append("discord_linked_at", generation).append("discord_link_generation", generation)
                .append("updated_at", System.currentTimeMillis());
        return collection.findOneAndUpdate(filter, List.of(new Document("$set", values)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
    }

    public boolean clearDiscordLink(String uuid, String expectedDiscordId, long expectedLinkedAt) {
        if (uuid == null || uuid.isBlank() || expectedDiscordId == null || expectedDiscordId.isBlank() || isReadOnly()) return false;
        var generation = new Document("$max", List.of(
                new Document("$ifNull", List.of("$discord_link_generation", 0L)),
                new Document("$ifNull", List.of("$discord_linked_at", 0L))));
        return collection.updateOne(Filters.and(eq("uuid", uuid), eq("discord_id", expectedDiscordId),
                        expectedLinkedAt == 0 ? or(eq("discord_linked_at", 0L), Filters.exists("discord_linked_at", false))
                                : eq("discord_linked_at", expectedLinkedAt)),
                List.of(new Document("$set", new Document("discord_id", "").append("discord_username", "")
                        .append("discord_linked_at", 0L).append("discord_link_generation", generation)
                        .append("updated_at", System.currentTimeMillis())))).getMatchedCount() > 0;
    }

    public boolean incrementPlayTime(String uuid, int delta) {
        return updateByUuid(uuid, Updates.inc("total_play_time", delta));
    }

    public java.util.concurrent.CompletionStage<Boolean> incrementPlayTimeAsync(String uuid, int delta) {
        return updateByUuidAsync(uuid, Updates.inc("total_play_time", delta));
    }

    public boolean updateIp(String uuid, String ip) {
        return updateByUuid(uuid, Updates.set("last_ip", ip));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateIpAsync(String uuid, String ip) {
        return updateByUuidAsync(uuid, Updates.set("last_ip", ip));
    }

    public boolean updateConnectionData(String uuid, String ip, String nickname) {
        return updateByUuid(uuid, Updates.combine(
                Updates.set("last_ip", ip),
                Updates.set("nickname", nickname)
        ));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateConnectionDataAsync(String uuid, String ip, String nickname) {
        return updateByUuidAsync(uuid, Updates.combine(
                Updates.set("last_ip", ip),
                Updates.set("nickname", nickname)
        ));
    }

    /** The players whose admin flag was set through their Discord role; for the move to roles. */
    public List<PlayerData> findDiscordRoleAdmins() {
        return collection.find(com.mongodb.client.model.Filters.and(eq("is_admin", true), eq("admin_source", "DISCORD_ROLE")))
                .into(new ArrayList<>());
    }

    /** Forgets the password and every remembered device, so that the next login sets a new password. */
    public boolean clearCredentials(String uuid) {
        return updateByUuid(uuid, Updates.combine(
                Updates.set("password_hash", ""),
                Updates.set("device_token_hashes", new ArrayList<String>()),
                Updates.set("device_tokens", new org.bson.Document())
        ));
    }

    public boolean updateLanguage(String uuid, String language) {
        return updateByUuid(uuid, Updates.set("local_language", language));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateLanguageAsync(String uuid, String language) {
        return updateByUuidAsync(uuid, Updates.set("local_language", language));
    }

    public boolean updateTranslatorLanguage(String uuid, String language) {
        return updateByUuid(uuid, Updates.set("translator_language", language));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateTranslatorLanguageAsync(String uuid, String language) {
        return updateByUuidAsync(uuid, Updates.set("translator_language", language));
    }

    public boolean updateGlobalChatVisible(String uuid, boolean visible) {
        return updateByUuid(uuid, Updates.set("show_global_chat", visible));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateGlobalChatVisibleAsync(String uuid, boolean visible) {
        return updateByUuidAsync(uuid, Updates.set("show_global_chat", visible));
    }

    public boolean updateDiscordRelayVisible(String uuid, boolean visible) {
        return updateByUuid(uuid, Updates.set("show_discord_relay", visible));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateDiscordRelayVisibleAsync(String uuid, boolean visible) {
        return updateByUuidAsync(uuid, Updates.set("show_discord_relay", visible));
    }

    public boolean updateLeaderboard(String uuid, boolean leaderboard) {
        return updateByUuid(uuid, Updates.set("leaderboard", leaderboard));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateLeaderboardAsync(String uuid, boolean leaderboard) {
        return updateByUuidAsync(uuid, Updates.set("leaderboard", leaderboard));
    }

    public boolean updateCustomNickname(String uuid, String customNickname) {
        return updateByUuid(uuid, Updates.set("custom_nickname", customNickname));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateCustomNicknameAsync(String uuid, String customNickname) {
        return updateByUuidAsync(uuid, Updates.set("custom_nickname", customNickname));
    }

    public boolean updateDescription(String uuid, String description) {
        return updateByUuid(uuid, Updates.set("description", description));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateDescriptionAsync(String uuid, String description) {
        return updateByUuidAsync(uuid, Updates.set("description", description));
    }

    public boolean setActiveBadge(String uuid, String badgeId) {
        return updateByUuid(uuid, Updates.set("active_badge", badgeId));
    }

    public java.util.concurrent.CompletionStage<Boolean> setActiveBadgeAsync(String uuid, String badgeId) {
        return updateByUuidAsync(uuid, Updates.set("active_badge", badgeId));
    }

    public boolean updateBadgeSymbolColorMode(String uuid, String mode) {
        return updateByUuid(uuid, Updates.set("badge_symbol_color_mode", mode));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateBadgeSymbolColorModeAsync(String uuid, String mode) {
        return updateByUuidAsync(uuid, Updates.set("badge_symbol_color_mode", mode));
    }

    public boolean addUnlockedBadge(String uuid, String badgeId) {
        return updateByUuid(uuid, Updates.addToSet("unlocked_badges", badgeId));
    }

    public java.util.concurrent.CompletionStage<Boolean> addUnlockedBadgeAsync(String uuid, String badgeId) {
        return updateByUuidAsync(uuid, Updates.addToSet("unlocked_badges", badgeId));
    }

    public boolean removeUnlockedBadge(String uuid, String badgeId) {
        return updateByUuid(uuid, Updates.pull("unlocked_badges", badgeId));
    }

    public java.util.concurrent.CompletionStage<Boolean> removeUnlockedBadgeAsync(String uuid, String badgeId) {
        return updateByUuidAsync(uuid, Updates.pull("unlocked_badges", badgeId));
    }

    public boolean addBlockedPrivateUuid(String uuid, String blockedUuid) {
        return updateByUuid(uuid, Updates.addToSet("blocked_private_uuids", blockedUuid));
    }

    public java.util.concurrent.CompletionStage<Boolean> addBlockedPrivateUuidAsync(String uuid, String blockedUuid) {
        return updateByUuidAsync(uuid, Updates.addToSet("blocked_private_uuids", blockedUuid));
    }

    public boolean removeBlockedPrivateUuid(String uuid, String blockedUuid) {
        return updateByUuid(uuid, Updates.pull("blocked_private_uuids", blockedUuid));
    }

    public java.util.concurrent.CompletionStage<Boolean> removeBlockedPrivateUuidAsync(String uuid, String blockedUuid) {
        return updateByUuidAsync(uuid, Updates.pull("blocked_private_uuids", blockedUuid));
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

    public java.util.concurrent.CompletionStage<Boolean> updateByUuidAsync(String uuid, Bson update) {
        if (uuid == null || uuid.isBlank() || update == null || isReadOnly()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }

        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for updateByUuidAsync"));
        }

        return MongoAsync.first(reactiveCollection.updateOne(
                eq("uuid", uuid),
                Updates.combine(
                        update,
                        Updates.set("updated_at", System.currentTimeMillis())
                )
        )).thenApply(result -> result != null && result.getMatchedCount() > 0);
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

    public java.util.concurrent.CompletionStage<Long> deleteBotsAsync() {
        if (isReadOnly()) {
            return java.util.concurrent.CompletableFuture.completedFuture(0L);
        }
        if (reactiveCollection == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for deleteBotsAsync"));
        }
        return MongoAsync.first(reactiveCollection.deleteMany(lt("total_play_time", 2)))
                .thenApply(result -> result == null ? 0L : result.getDeletedCount());
    }

    public PagedDataResult<PlayerData> search(String value, int limit, int page) {
        return MongoUtils.search(collection, "nickname", value, limit, page);
    }

    public Seq<PlayerData> findLeaders(String... fields) {
        return Seq.with(collection.find().sort(descending(fields)).limit(10));
    }

    public long countTopEntries() {
        return collection.countDocuments();
    }

    public LeaderboardSlice<PlayerData> findTopSlice(TopCategory category, LeaderboardCursor cursor, int limit) {
        int normalizedLimit = normalizeLeaderboardLimit(limit);
        Bson effectiveFilter = leaderboardCursorFilter(category, cursor);

        var iterable = effectiveFilter == null ? collection.find() : collection.find(effectiveFilter);
        var data = iterable
                .sort(leaderboardSort(category))
                .limit(normalizedLimit + 1)
                .into(new ArrayList<>());

        boolean hasNext = data.size() > normalizedLimit;
        if (hasNext) {
            data.removeLast();
        }

        LeaderboardCursor nextCursor = null;
        if (hasNext && !data.isEmpty()) {
            nextCursor = leaderboardCursor(category, data.getLast());
        }

        return new LeaderboardSlice<>(List.copyOf(data), hasNext, nextCursor);
    }

    public Integer findTopRank(TopCategory category, PlayerData playerData) {
        if (playerData == null || playerData.uuid == null || playerData.uuid.isBlank()) {
            return null;
        }

        if (collection.find(eq("uuid", playerData.uuid)).first() == null) {
            return null;
        }

        long aheadCount = collection.countDocuments(higherRankFilter(category, playerData));
        return Math.toIntExact(aheadCount + 1);
    }

    private Bson leaderboardSort(TopCategory category) {
        return switch (category) {
            case HEXED -> orderBy(descending("hexed_rank"), descending("hexed_points"), ascending("pid"));
            case PLAYTIME -> orderBy(descending("total_play_time"), ascending("pid"));
        };
    }

    private Bson higherRankFilter(TopCategory category, PlayerData playerData) {
        return switch (category) {
            case HEXED -> Filters.or(
                    gt("hexed_rank", playerData.hexedRank),
                    Filters.and(
                            eq("hexed_rank", playerData.hexedRank),
                            gt("hexed_points", playerData.hexedPoints)
                    ),
                    Filters.and(
                            eq("hexed_rank", playerData.hexedRank),
                            eq("hexed_points", playerData.hexedPoints),
                            lt("pid", playerData.pid)
                    )
            );
            case PLAYTIME -> Filters.or(
                    gt("total_play_time", playerData.totalPlayTime),
                    Filters.and(
                            eq("total_play_time", playerData.totalPlayTime),
                            lt("pid", playerData.pid)
                    )
            );
        };
    }

    private static int normalizeLeaderboardLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, 100);
    }

    private Bson leaderboardCursorFilter(TopCategory category, LeaderboardCursor cursor) {
        if (cursor == null || !cursor.isValid()) {
            return null;
        }

        return switch (category) {
            case PLAYTIME -> Filters.or(
                    lt("total_play_time", cursor.primaryValue()),
                    Filters.and(
                            eq("total_play_time", cursor.primaryValue()),
                            gt("pid", cursor.pid())
                    )
            );
            case HEXED -> Filters.or(
                    lt("hexed_rank", cursor.primaryValue()),
                    Filters.and(
                            eq("hexed_rank", cursor.primaryValue()),
                            lt("hexed_points", cursor.secondaryValue())
                    ),
                    Filters.and(
                            eq("hexed_rank", cursor.primaryValue()),
                            eq("hexed_points", cursor.secondaryValue()),
                            gt("pid", cursor.pid())
                    )
            );
        };
    }

    private LeaderboardCursor leaderboardCursor(TopCategory category, PlayerData playerData) {
        return switch (category) {
            case PLAYTIME -> new LeaderboardCursor(playerData.totalPlayTime, 0, playerData.pid);
            case HEXED -> new LeaderboardCursor(playerData.hexedRank, playerData.hexedPoints, playerData.pid);
        };
    }

    public List<PlayerData> findAllWithMapVotes() {
        return collection.find(Filters.exists("map_votes", true)).into(new ArrayList<>());
    }

    public boolean updateUsername(String uuid, String username, boolean canChangeUsername) {
        return updateByUuid(uuid, Updates.combine(
                Updates.set("username", username == null ? "" : username),
                Updates.set("can_change_username", canChangeUsername)
        ));
    }

    public java.util.concurrent.CompletionStage<Boolean> updateUsernameAsync(String uuid, String username, boolean canChangeUsername) {
        return updateByUuidAsync(uuid, Updates.combine(
                Updates.set("username", username == null ? "" : username),
                Updates.set("can_change_username", canChangeUsername)
        ));
    }

    public boolean updateIdentityDisplayMode(String uuid, org.xcore.plugin.model.enums.IdentityDisplayMode mode) {
        return updateByUuid(uuid, com.mongodb.client.model.Updates.set("identity_display_mode", mode != null ? mode.name() : "PID"));
    }
}
