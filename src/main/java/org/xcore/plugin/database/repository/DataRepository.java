package org.xcore.plugin.database.repository;

import arc.util.Log;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.MongoAsync;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.ModelData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.mongodb.client.model.Filters.eq;

public abstract class DataRepository<T extends ModelData> {
    protected final MongoDatabase database;
    protected final MongoCollection<T> collection;
    protected final com.mongodb.reactivestreams.client.MongoCollection<T> reactiveCollection;
    protected final TomlSecretsConfig secretsConfig;

    protected DataRepository(MongoDatabase database,
                             ReactiveMongoStore reactiveMongoStore,
                             String collectionName,
                             Class<T> clazz,
                             TomlSecretsConfig secretsConfig) {
        this.database = database;
        this.collection = database.getCollection(collectionName, clazz);
        this.reactiveCollection = reactiveMongoStore != null ? reactiveMongoStore.collection(collectionName, clazz) : null;
        this.secretsConfig = secretsConfig;

        collection.createIndex(new Document("version", -1));
        collection.createIndex(new Document("is_visible", -1));
    }

    protected DataRepository(MongoDatabase database, String collectionName, Class<T> clazz, TomlSecretsConfig secretsConfig) {
        this(database, null, collectionName, clazz, secretsConfig);
    }

    public boolean save(T data) {
        if (data == null) return false;

        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return false;
        }

        if (data.createdModelTime == 0) {
            data.createdModelTime = System.currentTimeMillis();
        }

        data.editModelTime = System.currentTimeMillis();

        if (data.id == null) {
            collection.insertOne(data);
        } else {
            collection.replaceOne(eq("_id", data.id), data, new ReplaceOptions().upsert(true));
        }
        return true;
    }

    public CompletionStage<Boolean> saveAsync(T data) {
        if (data == null) return CompletableFuture.completedFuture(false);

        if (isReadOnly()) {
            Log.warn("[XCore-DB] Database is in Read-Only mode. Save ignored for @", data.getClass().getSimpleName());
            return CompletableFuture.completedFuture(false);
        }

        if (data.createdModelTime == 0) {
            data.createdModelTime = System.currentTimeMillis();
        }

        data.editModelTime = System.currentTimeMillis();

        if (reactiveCollection == null) {
            return CompletableFuture.completedFuture(save(data));
        }

        if (data.id == null) {
            return MongoAsync.first(reactiveCollection.insertOne(data))
                    .thenApply(result -> true);
        } else {
            return MongoAsync.first(reactiveCollection.replaceOne(eq("_id", data.id), data, new ReplaceOptions().upsert(true)))
                    .thenApply(result -> true);
        }
    }

    public T findById(ObjectId id) {
        return collection.find(eq("_id", id)).first();
    }

    public CompletionStage<T> findByIdAsync(ObjectId id) {
        if (id == null) return CompletableFuture.completedFuture(null);
        if (reactiveCollection == null) return CompletableFuture.completedFuture(findById(id));
        return MongoAsync.first(reactiveCollection.find(eq("_id", id)));
    }

    public boolean isReadOnly() {
        return secretsConfig != null && secretsConfig.database != null && secretsConfig.database.readOnly;
    }

    public long count() {
        return collection.countDocuments();
    }

    public CompletionStage<Long> countAsync() {
        if (reactiveCollection == null) return CompletableFuture.completedFuture(count());
        return MongoAsync.first(reactiveCollection.countDocuments());
    }

    public long count(Bson filter) {
        return collection.countDocuments(filter);
    }

    public CompletionStage<Long> countAsync(Bson filter) {
        if (reactiveCollection == null) return CompletableFuture.completedFuture(count(filter));
        return MongoAsync.first(reactiveCollection.countDocuments(filter));
    }
}
