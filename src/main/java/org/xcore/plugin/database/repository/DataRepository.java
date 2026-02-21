package org.xcore.plugin.database.repository;

import arc.util.Log;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.ModelData;

import static com.mongodb.client.model.Filters.eq;

public abstract class DataRepository<T extends ModelData> {
    protected final MongoDatabase database;
    protected final MongoCollection<T> collection;

    protected final GlobalConfig globalConfig;

    protected DataRepository(MongoDatabase database, String collectionName, Class<T> clazz, GlobalConfig globalConfig) {
        this.database = database;
        this.collection = database.getCollection(collectionName, clazz);
        this.globalConfig = globalConfig;

        collection.createIndex(new Document("version", -1));
        collection.createIndex(new Document("is_visible", -1));
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

    public T findById(ObjectId id) {
        return collection.find(eq("_id", id)).first();
    }

    public boolean isReadOnly() {
        return globalConfig.isDataBaseReadOnly;
    }

    public long count() {
        return collection.countDocuments();
    }
}
