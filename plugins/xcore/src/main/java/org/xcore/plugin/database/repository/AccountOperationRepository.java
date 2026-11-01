package org.xcore.plugin.database.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.set;

/** A receipt is claimed before touching a code or binding. An unfinished claim is never re-executed. */
@Singleton
public class AccountOperationRepository {
    private final MongoCollection<Document> collection;

    public AccountOperationRepository(MongoDatabase database, TomlSecretsConfig secrets) {
        collection = database.getCollection("account_operation_receipts");
        if (!secrets.database.readOnly) collection.createIndex(new Document("createdAt", 1), new IndexOptions().expireAfter(1L, TimeUnit.HOURS));
    }

    public Claim claim(String operationId, String fingerprint) {
        try {
            collection.insertOne(new Document("_id", operationId).append("fingerprint", fingerprint)
                    .append("createdAt", new Date()));
            return new Claim(true, false, null);
        } catch (MongoWriteException exception) {
            if (exception.getError().getCode() != 11000) throw exception;
            Claim existing = find(operationId, fingerprint);
            return existing == null ? new Claim(false, false, null) : existing;
        }
    }

    public Claim find(String operationId, String fingerprint) {
        Document receipt = collection.find(eq("_id", operationId)).first();
        return receipt == null ? null : new Claim(false, !fingerprint.equals(receipt.getString("fingerprint")), receipt.getString("response"));
    }

    public boolean complete(String operationId, String fingerprint, String response) {
        return collection.updateOne(and(eq("_id", operationId), eq("fingerprint", fingerprint), exists("response", false)),
                set("response", response)).getMatchedCount() == 1;
    }

    public record Claim(boolean acquired, boolean conflict, String response) {}
}
