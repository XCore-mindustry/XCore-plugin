package org.xcore.plugin.database.repository;

import arc.util.Log;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.MongoAsync;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.MuteData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.mongodb.client.model.Filters.eq;

@Singleton
public class MuteDataRepository extends DataRepository<MuteData> {

    @Inject
    public MuteDataRepository(MongoDatabase database, ReactiveMongoStore reactiveMongoStore, TomlSecretsConfig secretsConfig) {
        super(database, reactiveMongoStore, "mutes", MuteData.class, secretsConfig);
    }

    public MuteDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        this(database, null, secretsConfig);
    }

    public MuteData findByUuid(String uuid) {
        return collection.find(eq("uuid", uuid)).first();
    }

    public CompletionStage<MuteData> findByUuidAsync(String uuid) {
        if (uuid == null) return CompletableFuture.completedFuture(null);
        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for findByUuidAsync"));
        }
        return MongoAsync.first(reactiveCollection.find(eq("uuid", uuid)));
    }

    @Override
    public boolean save(MuteData data) {
        if (data == null) {
            return false;
        }
        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return false;
        }
        collection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true));
        return true;
    }

    @Override
    public CompletionStage<Boolean> saveAsync(MuteData data) {
        if (data == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return CompletableFuture.completedFuture(false);
        }
        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for saveAsync"));
        }
        return MongoAsync.first(reactiveCollection.replaceOne(eq("uuid", data.uuid), data, new ReplaceOptions().upsert(true)))
                .thenApply(res -> true);
    }

    public boolean delete(String uuid) {
        if (uuid == null) {
            return false;
        }
        return collection.deleteOne(eq("uuid", uuid)).getDeletedCount() > 0;
    }

    public CompletionStage<Boolean> deleteAsync(String uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for deleteAsync"));
        }
        return MongoAsync.first(reactiveCollection.deleteOne(eq("uuid", uuid)))
                .thenApply(res -> res != null && res.getDeletedCount() > 0);
    }
}
