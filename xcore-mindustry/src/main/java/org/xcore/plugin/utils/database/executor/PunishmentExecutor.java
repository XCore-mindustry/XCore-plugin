package org.xcore.plugin.utils.database.executor;

import arc.math.Mathf;
import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.xcore.plugin.utils.database.Database;
import org.xcore.plugin.utils.database.PagedDataResult;
import org.xcore.plugin.utils.models.Punishment;

import static com.mongodb.client.model.Filters.eq;


public class PunishmentExecutor<T extends Punishment> extends Executor<T> {
    public PunishmentExecutor(MongoCollection<T> collection) {
        super(collection);
    }

    public T get(String uuid) {
        return collection.find(getPunishmentFilter(uuid)).first();
    }

    @Override
    public void save(T data) {
        collection.replaceOne(getPunishmentFilter(data.uuid), data, new ReplaceOptions().upsert(true));
    }

    public void delete(String uuid) {
        collection.deleteOne(getPunishmentFilter(uuid));
    }

    public Seq<T> getPunished() {
        return Seq.with(collection.find());
    }

    public Bson getPunishmentFilter(String uuid) {
        return eq("uuid", uuid);
    }

    public PagedDataResult<T> search(String value, int limit, int page) {
        return Database.search(collection, "name", value, limit, page);
    }

    public PagedDataResult<T> pagedData(int limit, int page) {
        int count = (int) collection.countDocuments();
        if (count == 0) return null;

        return new PagedDataResult<>(
                count,
                Mathf.ceil((float) count / limit),
                Database.pagedData(new BsonDocument(), collection, limit, page));
    }
}
