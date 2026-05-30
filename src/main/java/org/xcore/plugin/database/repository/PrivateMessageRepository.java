package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Sorts;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.PrivateMessage;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

@Singleton
public class PrivateMessageRepository extends DataRepository<PrivateMessage> {

    @Inject
    public PrivateMessageRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        super(database, "private_messages", PrivateMessage.class, secretsConfig);

        collection.createIndex(
                new Document("to_uuid", 1)
                        .append("recipient_deleted", 1)
                        .append("created_at", -1)
        );
        collection.createIndex(
                new Document("to_uuid", 1)
                        .append("recipient_deleted", 1)
                        .append("read_at", 1)
                        .append("created_at", -1)
        );
        collection.createIndex(new Document("from_uuid", 1).append("created_at", -1));
        collection.createIndex(new Document("to_uuid", 1).append("from_uuid", 1).append("created_at", -1), new IndexOptions());
    }

    public List<PrivateMessage> findInbox(String uuid, int skip, int limit) {
        return collection.find(inboxFilter(uuid))
                .sort(Sorts.descending("created_at", "_id"))
                .skip(skip)
                .limit(limit)
                .into(new ArrayList<>());
    }

    public long countInbox(String uuid) {
        return collection.countDocuments(inboxFilter(uuid));
    }

    public long countUnread(String uuid) {
        return collection.countDocuments(Filters.and(
                eq("to_uuid", uuid),
                eq("recipient_deleted", false),
                eq("read_at", 0L)
        ));
    }

    @Override
    public PrivateMessage findById(ObjectId id) {
        return super.findById(id);
    }

    public PrivateMessage findLatestConversationMessage(String uuid) {
        return collection.find(Filters.or(
                        Filters.and(eq("to_uuid", uuid), eq("recipient_deleted", false)),
                        eq("from_uuid", uuid)
                ))
                .sort(Sorts.descending("created_at", "_id"))
                .first();
    }

    private org.bson.conversions.Bson inboxFilter(String uuid) {
        return Filters.and(
                eq("to_uuid", uuid),
                eq("recipient_deleted", false)
        );
    }
}
