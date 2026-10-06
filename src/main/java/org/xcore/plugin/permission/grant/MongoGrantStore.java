package org.xcore.plugin.permission.grant;

import com.mongodb.MongoWriteException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lte;

/** Grants in the {@code permission_grants} collection, one document per player keyed by uuid. */
@Singleton
public class MongoGrantStore implements GrantStore {

    public static final String COLLECTION = "permission_grants";

    private static final int DUPLICATE_KEY = 11000;
    private static final String REVISION = "revision";
    private static final String GRANTS = "grants";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String CREDENTIALS_EPOCH = "credentialsEpoch";

    private final MongoCollection<Document> collection;

    @Inject
    public MongoGrantStore(MongoDatabase database, TomlSecretsConfig config) {
        this(database.getCollection(COLLECTION, Document.class));
        // Read-only stores must not perform index-management writes during creation.
        if (!config.database.readOnly) {
            collection.createIndex(new Document(GRANTS + "." + EXPIRES_AT, 1));
        }
    }

    MongoGrantStore(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public GrantDocument find(String uuid) {
        Document raw = collection.find(eq("_id", uuid)).first();
        return raw == null ? GrantDocument.empty(uuid) : document(raw);
    }

    @Override
    public Map<String, GrantDocument> findAll(Collection<String> uuids) {
        Map<String, GrantDocument> found = new HashMap<>();
        if (uuids.isEmpty()) {
            return found;
        }
        for (Document raw : collection.find(in("_id", uuids))) {
            GrantDocument document = document(raw);
            found.put(document.uuid(), document);
        }
        return found;
    }

    @Override
    public Optional<GrantDocument> replace(@Nullable ClientSession session, String uuid, long expectedRevision, List<Grant> grants) {
        if (grants.size() > GrantDocument.MAX_GRANTS) {
            throw new IllegalArgumentException("A player can hold at most " + GrantDocument.MAX_GRANTS + " grants");
        }
        List<Document> rawGrants = grants.stream().map(MongoGrantStore::raw).toList();
        if (expectedRevision == 0) {
            return insert(session, new GrantDocument(uuid, 1, grants));
        }
        // Only what is named is set, so the credentials epoch stays as it is.
        return update(session, uuid, expectedRevision,
                new Document("$set", new Document(REVISION, expectedRevision + 1).append(GRANTS, rawGrants)));
    }

    @Override
    public Optional<GrantDocument> bumpCredentialsEpoch(@Nullable ClientSession session, GrantDocument current) {
        if (current.revision() == 0) {
            return insert(session, new GrantDocument(current.uuid(), 1, List.of(), 1));
        }
        return update(session, current.uuid(), current.revision(),
                new Document("$set", new Document(REVISION, current.revision() + 1))
                        .append("$inc", new Document(CREDENTIALS_EPOCH, 1L)));
    }

    /** The first write for a player; loses to another first write that got there earlier. */
    private Optional<GrantDocument> insert(@Nullable ClientSession session, GrantDocument document) {
        Document fresh = new Document("_id", document.uuid())
                .append(REVISION, document.revision())
                .append(GRANTS, document.grants().stream().map(MongoGrantStore::raw).toList())
                .append(CREDENTIALS_EPOCH, document.credentialsEpoch());
        try {
            if (session != null) {
                collection.insertOne(session, fresh);
            } else {
                collection.insertOne(fresh);
            }
            return Optional.of(document);
        } catch (MongoWriteException e) {
            if (e.getError().getCode() == DUPLICATE_KEY) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private Optional<GrantDocument> update(@Nullable ClientSession session, String uuid, long expectedRevision, Bson update) {
        Bson unchanged = and(eq("_id", uuid), eq(REVISION, expectedRevision));
        FindOneAndUpdateOptions after = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
        Document raw = session != null
                ? collection.findOneAndUpdate(session, unchanged, update, after)
                : collection.findOneAndUpdate(unchanged, update, after);
        return raw == null ? Optional.empty() : Optional.of(document(raw));
    }

    @Override
    public List<String> findUuidsWithExpiredGrants(Instant now) {
        List<String> uuids = new ArrayList<>();
        for (Document raw : collection.find(lte(GRANTS + "." + EXPIRES_AT, Date.from(now))).projection(new Document("_id", 1))) {
            uuids.add(raw.getString("_id"));
        }
        return uuids;
    }

    private static GrantDocument document(Document raw) {
        List<Grant> grants = new ArrayList<>();
        for (Document grant : raw.getList(GRANTS, Document.class, List.of())) {
            grants.add(grant(grant));
        }
        Number revision = raw.get(REVISION, Number.class);
        Number epoch = raw.get(CREDENTIALS_EPOCH, Number.class);
        return new GrantDocument(raw.getString("_id"), revision == null ? 0 : revision.longValue(), grants,
                epoch == null ? 0 : epoch.longValue());
    }

    private static Grant grant(Document raw) {
        return new Grant(
                raw.getString("id"),
                raw.getString("role"),
                raw.getString("node"),
                raw.getBoolean("allow", true),
                raw.getString("server"),
                instant(raw.getDate(EXPIRES_AT)),
                raw.getString("source"),
                raw.getString("by"),
                instant(raw.getDate("at")),
                raw.getString("reason")
        );
    }

    private static Document raw(Grant grant) {
        Document raw = new Document("id", grant.id());
        if (grant.isRole()) {
            raw.append("role", grant.role());
        } else {
            raw.append("node", grant.node()).append("allow", grant.allow());
        }
        return raw.append("server", grant.server())
                .append(EXPIRES_AT, date(grant.expiresAt()))
                .append("source", grant.source())
                .append("by", grant.by())
                .append("at", date(grant.at()))
                .append("reason", grant.reason());
    }

    private static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private static Date date(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }
}
