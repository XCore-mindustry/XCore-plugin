package org.xcore.plugin.rating.prize;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.rating.season.PrizeKind;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.orderBy;

/** Grants in the {@code rating_prize_grants} collection. */
@Singleton
public class MongoPrizeGrantRepository implements PrizeGrantRepository {
    public static final String COLLECTION = "rating_prize_grants";
    private static final int DUPLICATE_KEY = 11000;

    private final MongoCollection<Document> collection;
    private final TomlSecretsConfig config;

    @Inject
    public MongoPrizeGrantRepository(MongoDatabase database, TomlSecretsConfig config) {
        this.collection = database.getCollection(COLLECTION, Document.class);
        this.config = config;
        if (!config.database.readOnly) {
            collection.createIndex(new Document("season_id", 1).append("place", 1));
            collection.createIndex(new Document("player_uuid", 1));
        }
    }

    @Override
    public boolean createIfAbsent(PrizeGrant grant) {
        writable();
        try {
            collection.insertOne(document(grant));
            return true;
        } catch (MongoWriteException e) {
            if (e.getCode() == DUPLICATE_KEY) return false;
            throw e;
        }
    }

    @Override
    public List<PrizeGrant> findBySeason(String seasonId) {
        return collection.find(eq("season_id", seasonId))
                .sort(orderBy(ascending("place"), ascending("prize_index"), ascending("player_uuid")))
                .map(MongoPrizeGrantRepository::grant)
                .into(new java.util.ArrayList<>());
    }

    @Override
    public List<PrizeGrant> findByPlayer(String playerUuid) {
        return collection.find(eq("player_uuid", playerUuid))
                .map(MongoPrizeGrantRepository::grant)
                .into(new java.util.ArrayList<>());
    }

    @Override
    public boolean transition(String id, PrizeStatus expected, PrizeStatus status, String by, String note,
                              Instant now) {
        writable();
        return collection.updateOne(
                and(eq("_id", id), eq("status", expected.name())), update(status, by, note, now))
                .getModifiedCount() > 0;
    }

    @Override
    public int markDelivered(String seasonId, int place, @Nullable String playerUuid, String by, String note,
                             Instant now) {
        writable();
        Bson unsettled = in("status", PrizeStatus.PENDING.name(), PrizeStatus.FAILED.name());
        Bson target = and(eq("season_id", seasonId), eq("place", place), unsettled);
        if (playerUuid != null) {
            target = and(target, eq("player_uuid", playerUuid));
        }
        return (int) collection.updateMany(
                target,
                update(PrizeStatus.DELIVERED, by, note, now)).getModifiedCount();
    }

    private static Bson update(PrizeStatus status, String by, String note, Instant now) {
        return Updates.combine(
                Updates.set("status", status.name()),
                Updates.set("granted_by", by),
                Updates.set("note", note == null ? "" : note),
                Updates.set("updated_at", Date.from(now)));
    }

    private void writable() {
        if (config.database.readOnly) {
            throw new IllegalStateException("The database is read-only; prize grants cannot be written");
        }
    }

    static Document document(PrizeGrant grant) {
        return new Document("_id", grant.id())
                .append("season_id", grant.seasonId())
                .append("place", grant.place())
                .append("player_uuid", grant.playerUuid())
                .append("prize_index", grant.prizeIndex())
                .append("prize", new Document("kind", grant.kind().name())
                        .append("value", grant.value())
                        .append("description", grant.description()))
                .append("status", grant.status().name())
                .append("granted_by", grant.grantedBy())
                .append("updated_at", Date.from(grant.updatedAt()))
                .append("note", grant.note());
    }

    static PrizeGrant grant(Document document) {
        Document prize = document.get("prize", Document.class);
        return new PrizeGrant(
                document.getString("_id"),
                document.getString("season_id"),
                document.getInteger("place", 0),
                document.getString("player_uuid"),
                document.getInteger("prize_index", 0),
                PrizeKind.parse(prize.getString("kind")),
                prize.getString("value"),
                prize.getString("description"),
                PrizeStatus.valueOf(document.getString("status")),
                document.getString("granted_by"),
                document.getDate("updated_at").toInstant(),
                document.getString("note"));
    }
}
