package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.types.ObjectId;
import org.xcore.plugin.model.ModelData;

import static com.mongodb.client.model.Filters.eq;

public abstract class DataRepository<T extends ModelData> {
    protected final MongoDatabase database;
    protected final MongoCollection<T> collection;

    protected DataRepository(MongoDatabase database, String collectionName, Class<T> clazz) {
        this.database = database;
        this.collection = database.getCollection(collectionName, clazz);
    }

    public void save(T data) {
        if (data == null) return;

        if (data.createdModelTime == 0) {
            data.createdModelTime = System.currentTimeMillis();
        }

        data.editModelTime = System.currentTimeMillis();

        if (data.id == null) {
            collection.insertOne(data);
        } else {
            collection.replaceOne(eq("_id", data.id), data, new ReplaceOptions().upsert(true));
        }
    }

    public T findById(ObjectId id) {
        return collection.find(eq("_id", id)).first();
    }
}
