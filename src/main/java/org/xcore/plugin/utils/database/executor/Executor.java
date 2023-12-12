package org.xcore.plugin.utils.database.executor;

import com.mongodb.client.MongoCollection;

public abstract class Executor<T> {
    protected final MongoCollection<T> collection;

    protected Executor(MongoCollection<T> collection) {
        this.collection = collection;
    }

    public abstract void save(T data);
}
