package org.xcore.plugin.cases;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

/** Blocking repository: call only from a worker. No Mongo operations in construction/startup. */
@Singleton
public class CaseNoticeRepository {
    public static final String COLLECTION = "case_notices";
    public static final long CLAIM_SECONDS = 120;
    private final MongoCollection<Document> collection;
    private final TomlSecretsConfig config;
    private boolean indexed;

    public CaseNoticeRepository(MongoDatabase database, TomlSecretsConfig config) {
        this.collection = database.getCollection(COLLECTION);
        this.config = config;
    }

    public List<CaseNotice> findPending(String uuid, int limit) {
        indexes();
        return collection.find(and(eq("player_uuid", uuid), pending(Instant.now())))
                .sort(new Document("created_at", 1).append("_id", 1)).limit(Math.clamp(limit, 1, 100))
                .map(CaseNoticeRepository::notice).into(new ArrayList<>());
    }

    public CaseNotice claim(String id, String uuid, String server) {
        if (config.database.readOnly) return null;
        indexes();
        Instant now = Instant.now();
        Document claimed = collection.findOneAndUpdate(and(eq("_id", id), eq("player_uuid", uuid), pending(now)),
                combine(set("claim_token", UUID.randomUUID().toString()), set("claim_server", server),
                        set("claim_until", Date.from(now.plusSeconds(CLAIM_SECONDS)))),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        return claimed == null ? null : notice(claimed);
    }

    /** Only the current owner may acknowledge. Expired owners cannot mark a successor's display. */
    public boolean acknowledge(CaseNotice notice, String server) {
        if (config.database.readOnly) return false;
        Instant now = Instant.now();
        return collection.updateOne(and(owner(notice), gt("claim_until", Date.from(now))),
                combine(set("delivered_at", Date.from(now)), set("delivered_server", server),
                        unset("claim_token"), unset("claim_server"), unset("claim_until"))).getModifiedCount() == 1;
    }

    /** Release is token guarded, including when a lease has expired or a server has restarted. */
    public void release(CaseNotice notice) {
        if (config.database.readOnly) return;
        collection.updateOne(owner(notice), combine(unset("claim_token"), unset("claim_server"), unset("claim_until")));
    }

    private static Bson owner(CaseNotice notice) {
        if (notice.claimToken() == null) throw new IllegalArgumentException("Unclaimed notice");
        return and(eq("_id", notice.id()), eq("player_uuid", notice.playerUuid()),
                eq("delivered_at", null), eq("claim_token", notice.claimToken()));
    }

    private static Bson pending(Instant now) {
        return and(eq("delivered_at", null), gt("expires_at", Date.from(now)),
                or(eq("claim_until", null), lte("claim_until", Date.from(now))));
    }

    private synchronized void indexes() {
        if (indexed || config.database.readOnly) return;
        collection.createIndex(new Document("player_uuid", 1).append("delivered_at", 1));
        collection.createIndex(new Document("expires_at", 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
        indexed = true;
    }

    private static CaseNotice notice(Document doc) {
        var params = new HashMap<String, String>();
        var values = doc.get("params", Document.class);
        if (values != null) values.forEach((key, value) -> {
            if (value instanceof String text) params.put(key, text);
        });
        Date until = doc.getDate("claim_until");
        return new CaseNotice(doc.getString("_id"), doc.getString("player_uuid"), doc.getString("kind"), params,
                doc.getString("claim_token"), until == null ? null : until.toInstant());
    }
}
