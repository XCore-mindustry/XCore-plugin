package org.xcore.plugin.integration.idempotency;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Factory for plugin-scoped, persistent operation ledgers. */
@Singleton
public final class PluginIdempotencyLedgerFactory {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private static final long LEASE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private final MongoDatabase database;
    private final TomlSecretsConfig config;

    @Inject
    public PluginIdempotencyLedgerFactory(MongoDatabase database, TomlSecretsConfig config) {
        this.database = database;
        this.config = config;
    }

    public PluginIdempotencyLedger create(String pluginId) {
        if (pluginId == null || !ID.matcher(pluginId).matches()) {
            throw new IllegalArgumentException("Plugin ID must match [a-z0-9][a-z0-9_-]{0,31}");
        }
        MongoCollection<Document> collection = database.getCollection(collectionName(pluginId));
        if (!config.database.readOnly) {
            collection.createIndex(new Document("operation_id", 1),
                    new com.mongodb.client.model.IndexOptions().unique(true));
        }
        return new MongoLedger(collection, config);
    }

    public static String collectionName(String pluginId) {
        return "xcore_plugin_" + pluginId + "_operations";
    }

    static final class MongoLedger implements PluginIdempotencyLedger {
        private final MongoCollection<Document> collection;
        private final TomlSecretsConfig config;

        MongoLedger(MongoCollection<Document> collection, TomlSecretsConfig config) {
            this.collection = collection;
            this.config = config;
        }

        @Override
        public LedgerClaim claim(String operationId, String operationType, String resultHash) {
            validate(operationId, operationType, resultHash);
            writable();
            long now = System.currentTimeMillis();
            var initial = new Document("_id", operationId)
                    .append("operation_id", operationId)
                    .append("operation_type", operationType)
                    .append("status", "CLAIMED")
                    .append("result_hash", resultHash)
                    .append("created_at", now)
                    .append("updated_at", now);
            try {
                collection.insertOne(initial);
                return new LedgerClaim(true, entry(initial));
            } catch (MongoWriteException duplicate) {
                // Another server won the insert race; inspect and possibly take over a stale lease.
            }

            Document current = collection.find(Filters.eq("_id", operationId)).first();
            if (current == null) return claim(operationId, operationType, resultHash);
            LedgerEntry currentEntry = entry(current);
            if (!resultHash.equals(currentEntry.resultHash())) {
                throw new IllegalStateException("Operation already exists with a different result hash: " + operationId);
            }
            if ("COMPLETED".equals(currentEntry.status()) || "SKIPPED".equals(currentEntry.status())) {
                return new LedgerClaim(false, currentEntry);
            }
            if (!"CLAIMED".equals(currentEntry.status())
                    || now - currentEntry.updatedAt() < LEASE_MILLIS) {
                return new LedgerClaim(false, currentEntry);
            }

            Document taken = collection.findOneAndUpdate(
                    Filters.and(Filters.eq("_id", operationId), Filters.eq("status", "CLAIMED"),
                            Filters.lt("updated_at", now - LEASE_MILLIS)),
                    Updates.set("updated_at", now),
                    new com.mongodb.client.model.FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            return taken == null
                    ? new LedgerClaim(false, entry(collection.find(Filters.eq("_id", operationId)).first()))
                    : new LedgerClaim(true, entry(taken));
        }

        @Override
        public Optional<LedgerEntry> find(String operationId) {
            if (operationId == null || operationId.isBlank()) return Optional.empty();
            return Optional.ofNullable(collection.find(Filters.eq("_id", operationId)).first()).map(this::entry);
        }

        @Override
        public boolean markCompleted(String operationId, String resultHash) {
            validateIdAndHash(operationId, resultHash);
            writable();
            return collection.updateOne(Filters.and(Filters.eq("_id", operationId),
                            Filters.eq("status", "CLAIMED"), Filters.eq("result_hash", resultHash)),
                    Updates.combine(Updates.set("status", "COMPLETED"),
                            Updates.set("updated_at", System.currentTimeMillis())))
                    .getModifiedCount() > 0;
        }

        @Override
        public boolean markSkipped(String operationId, String reason) {
            if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operationId must not be blank");
            writable();
            return collection.updateOne(Filters.and(Filters.eq("_id", operationId), Filters.eq("status", "CLAIMED")),
                    Updates.combine(Updates.set("status", "SKIPPED"), Updates.set("reason", reason == null ? "" : reason),
                            Updates.set("updated_at", System.currentTimeMillis()))).getModifiedCount() > 0;
        }

        private void writable() {
            if (config.database.readOnly) throw new IllegalStateException("Database is read-only");
        }

        private static void validate(String operationId, String operationType, String resultHash) {
            validateIdAndHash(operationId, resultHash);
            if (operationType == null || operationType.isBlank()) throw new IllegalArgumentException("operationType must not be blank");
        }

        private static void validateIdAndHash(String operationId, String resultHash) {
            if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operationId must not be blank");
            if (resultHash == null || resultHash.isBlank()) throw new IllegalArgumentException("resultHash must not be blank");
        }

        private LedgerEntry entry(Document d) {
            return new LedgerEntry(d.getString("operation_id"), d.getString("operation_type"),
                    d.getString("status"), d.getString("result_hash"), number(d, "created_at"),
                    number(d, "updated_at"), d.getString("reason"));
        }

        private static long number(Document d, String key) {
            Number value = d.get(key, Number.class);
            return value == null ? 0L : value.longValue();
        }
    }
}
