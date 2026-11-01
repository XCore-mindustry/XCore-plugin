package org.xcore.plugin.database.repository;

import arc.util.Log;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.MongoAsync;
import org.xcore.plugin.database.MongoUtils;
import org.xcore.plugin.database.PagedDataResult;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.PlayerPids;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Updates.set;

@Singleton
public class BanDataRepository extends DataRepository<BanData> {

    private final com.mongodb.reactivestreams.client.MongoCollection<Document> reactiveCounters;
    private final com.mongodb.reactivestreams.client.MongoCollection<Document> reactivePlayers;

    @Inject
    public BanDataRepository(MongoDatabase database, ReactiveMongoStore reactiveMongoStore, TomlSecretsConfig secretsConfig) {
        super(database, reactiveMongoStore, "bans", BanData.class, secretsConfig);
        reactiveCounters = reactiveMongoStore != null ? reactiveMongoStore.collection("counters", Document.class) : null;
        reactivePlayers = reactiveMongoStore != null ? reactiveMongoStore.collection("players", Document.class) : null;
    }

    public BanDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        this(database, null, secretsConfig);
    }

    public int nextBanId() {
        return nextBanId(null);
    }

    private int nextBanId(com.mongodb.client.ClientSession session) {
        if (isReadOnly()) {
            throw new IllegalStateException("Cannot allocate ban IDs in Read-Only mode");
        }
        var counters = database.getCollection("counters");
        var filter = new Document("_id", "ban_id");
        var update = new Document("$inc", new Document("seq", 1));
        var options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);
        return counterValue(session == null
                ? counters.findOneAndUpdate(filter, update, options)
                : counters.findOneAndUpdate(session, filter, update, options));
    }

    private static int counterValue(Document counter) {
        int value = Math.toIntExact(((Number) counter.get("seq")).longValue());
        if (value < 1) {
            throw new IllegalStateException("Ban ID counter must be positive");
        }
        return value;
    }

    public BanData findByBanId(int banId) {
        return collection.find(eq("ban_id", banId)).first();
    }

    public Integer findPlayerPid(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        var player = database.getCollection("players").find(eq("uuid", uuid))
                .projection(new Document("pid", 1)).first();
        return player == null ? null : PlayerPids.orNull(player.getInteger("pid"));
    }

    public void assignBanIdIfMissing(BanData ban) {
        if (ban == null || ban.id == null || isReadOnly()) return;
        if (ban.banId == null) {
            int candidate = nextBanId();
            var assigned = collection.findOneAndUpdate(
                    and(eq("_id", ban.id), exists("ban_id", false)), set("ban_id", candidate),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            // A competing reader may have won. Use its ID, never our unused candidate.
            var current = assigned != null ? assigned : findById(ban.id);
            if (current != null) {
                ban.banId = current.banId;
                ban.pid = current.pid;
            }
        }
        if (!PlayerPids.isAssigned(ban.pid)) {
            Integer pid = findPlayerPid(ban.uuid);
            if (pid != null) {
                collection.updateOne(and(eq("_id", ban.id), eq("pid", null)), set("pid", pid));
                var current = findById(ban.id);
                if (current != null) ban.pid = current.pid;
            }
        }
    }

    public BanData find(String uuid, String ip) {
        return find(null, uuid, ip);
    }

    public BanData find(com.mongodb.client.ClientSession session, String uuid, String ip) {
        var filter = identifierFilter(uuid, ip);
        if (filter == null) {
            return null;
        }
        if (session != null) {
            return collection.find(session, filter).first();
        }
        return collection.find(filter).first();
    }

    public CompletionStage<BanData> findAsync(String uuid, String ip) {
        var filter = identifierFilter(uuid, ip);
        if (filter == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for findAsync"));
        }
        return MongoAsync.first(reactiveCollection.find(filter));
    }

    @Override
    public boolean save(BanData data) {
        return save(null, data);
    }

    public boolean save(com.mongodb.client.ClientSession session, BanData data) {
        if (data == null) {
            return false;
        }
        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return false;
        }

        var filter = identifierFilter(data.uuid, data.ip);
        if (filter == null) {
            return false;
        }

        if (data.banId == null) data.banId = nextBanId(session);
        if (!PlayerPids.isAssigned(data.pid)) data.pid = findPlayerPid(data.uuid);

        if (session != null) {
            collection.replaceOne(session, filter, data, new ReplaceOptions().upsert(true));
        } else {
            collection.replaceOne(filter, data, new ReplaceOptions().upsert(true));
        }
        return true;
    }

    @Override
    public CompletionStage<Boolean> saveAsync(BanData data) {
        if (data == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return CompletableFuture.completedFuture(false);
        }

        var filter = identifierFilter(data.uuid, data.ip);
        if (filter == null) {
            return CompletableFuture.completedFuture(false);
        }

        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for saveAsync"));
        }

        CompletionStage<Integer> idStage = data.banId != null
                ? CompletableFuture.completedFuture(data.banId)
                : MongoAsync.first(reactiveCounters.findOneAndUpdate(
                        new Document("_id", "ban_id"), new Document("$inc", new Document("seq", 1)),
                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)))
                        .thenApply(BanDataRepository::counterValue);
        return idStage.thenCompose(banId -> {
            data.banId = banId;
            CompletionStage<Document> playerStage = !PlayerPids.isAssigned(data.pid) && data.uuid != null && !data.uuid.isBlank()
                    ? MongoAsync.first(reactivePlayers.find(eq("uuid", data.uuid)).projection(new Document("pid", 1)))
                    : CompletableFuture.completedFuture(null);
            return playerStage.thenCompose(player -> {
                if (player != null) data.pid = PlayerPids.orNull(player.getInteger("pid"));
                return MongoAsync.first(reactiveCollection.replaceOne(filter, data, new ReplaceOptions().upsert(true)))
                        .thenApply(res -> true);
            });
        });
    }

    public boolean delete(String uuid, String ip) {
        var filter = identifierFilter(uuid, ip);
        if (filter == null) {
            return false;
        }
        return collection.deleteMany(filter).getDeletedCount() > 0;
    }

    public CompletionStage<Boolean> deleteAsync(String uuid, String ip) {
        var filter = identifierFilter(uuid, ip);
        if (filter == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for deleteAsync"));
        }
        return MongoAsync.first(reactiveCollection.deleteMany(filter))
                .thenApply(res -> res != null && res.getDeletedCount() > 0);
    }

    public PagedDataResult<BanData> search(String value, int limit, int page) {
        return MongoUtils.search(collection, "name", value, limit, page);
    }

    public PagedDataResult<BanData> findAllPaged(int limit, int page) {
        long count = collection.countDocuments();
        if (count == 0) return null;

        int totalPages = (int) Math.ceil((double) count / limit);

        var data = MongoUtils.pagedData(new org.bson.BsonDocument(), collection, limit, page);

        return new PagedDataResult<>((int) count, totalPages, data);
    }

    public Iterable<BanData> findAll() {
        return collection.find();
    }

    private static Bson identifierFilter(String uuid, String ip) {
        if (uuid != null && ip != null) {
            return or(eq("uuid", uuid), eq("ip", ip));
        }
        if (uuid != null) {
            return eq("uuid", uuid);
        }
        if (ip != null) {
            return eq("ip", ip);
        }
        return null;
    }
}
