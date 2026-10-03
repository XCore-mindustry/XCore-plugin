package org.xcore.plugin.rating.ladder;

import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Sorts.orderBy;

/** Standings in the {@code rating_standings} collection, one document per (ladder, season, player). */
@Singleton
public class MongoLadderStore implements LadderStore {
    public static final String COLLECTION = "rating_standings";

    /** Retries only ever cover the few operations in flight, so the trail stays short. */
    static final int APPLIED_OPERATIONS_LIMIT = 64;
    private static final int DUPLICATE_KEY = 11000;
    private static final int APPLY_ATTEMPTS = 3;
    private static final int RANK_BATCH_SIZE = 500;

    private final MongoCollection<Document> collection;
    private final TomlSecretsConfig config;

    @Inject
    public MongoLadderStore(MongoDatabase database, TomlSecretsConfig config) {
        this(database.getCollection(COLLECTION, Document.class), config);
        // Read-only stores must not perform index-management writes during creation.
        if (!config.database.readOnly) {
            collection.createIndex(new Document("ladder", 1).append("season", 1).append("player_uuid", 1),
                    new IndexOptions().unique(true));
            collection.createIndex(new Document("ladder", 1).append("season", 1)
                    .append("rating", -1).append("player_uuid", 1));
            collection.createIndex(new Document("player_uuid", 1));
        }
    }

    MongoLadderStore(MongoCollection<Document> collection, TomlSecretsConfig config) {
        this.collection = collection;
        this.config = config;
    }

    @Override
    public Optional<LadderStanding> find(String ladderId, int season, String uuid) {
        return Optional.ofNullable(collection.find(key(ladderId, season, uuid)).first())
                .map(MongoLadderStore::standing);
    }

    @Override
    public Optional<LadderStanding> latestBefore(String ladderId, int season, String uuid) {
        requireUuid(uuid);
        return Optional.ofNullable(collection.find(and(eq("ladder", ladderId), eq("player_uuid", uuid),
                        lt("season", season)))
                .sort(descending("season"))
                .first()).map(MongoLadderStore::standing);
    }

    @Override
    public ApplyResult applyOnce(String ladderId, int season, String operationId, StandingMutation mutation,
                                 StandingSeed seed, int minimumRating) {
        writable();
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("Operation ID must not be blank");
        }
        Document key = key(ladderId, season, mutation.uuid());
        Document filter = new Document(key).append("applied_operations", new Document("$ne", operationId));
        List<Bson> update = applyPipeline(operationId, mutation, seed, minimumRating);
        var options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER);

        for (int attempt = 1; ; attempt++) {
            try {
                return new ApplyResult(true, standing(collection.findOneAndUpdate(filter, update, options)));
            } catch (MongoException e) {
                if (e.getCode() != DUPLICATE_KEY) {
                    throw e;
                }
                // The standing exists but did not match the filter: either this operation is
                // already applied, or another operation created the standing a moment ago.
                Document current = collection.find(key).first();
                if (current != null && current.getList("applied_operations", String.class, List.of())
                        .contains(operationId)) {
                    return new ApplyResult(false, standing(current));
                }
                if (attempt == APPLY_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private static List<Bson> applyPipeline(String operationId, StandingMutation mutation,
                                            StandingSeed seed, int minimumRating) {
        Document fields = new Document()
                .append("rating", new Document("$max", List.of(minimumRating, new Document("$add",
                        List.of(new Document("$ifNull", List.of("$rating", seed.rating())), mutation.ratingDelta())))))
                .append("matches", increment("matches", 1))
                .append("wins", increment("wins", mutation.win() ? 1 : 0));
        for (var stat : mutation.stats().entrySet()) {
            fields.append("stats." + stat.getKey(), increment("stats." + stat.getKey(), stat.getValue()));
        }
        fields.append("applied_operations", new Document("$slice", List.of(
                        new Document("$concatArrays", List.of(
                                new Document("$ifNull", List.of("$applied_operations", List.of())),
                                List.of(new Document("$literal", operationId)))),
                        -APPLIED_OPERATIONS_LIMIT)))
                // Only a standing created by this very update records where its rating came from.
                .append("seeded_from", new Document("$cond", List.of(
                        new Document("$eq", List.of(new Document("$type", "$created_at"), "missing")),
                        seed.carried()
                                ? new Document("$literal", new Document("season", seed.fromSeason())
                                        .append("rating", seed.fromRating()))
                                : "$$REMOVE",
                        new Document("$ifNull", List.of("$seeded_from", "$$REMOVE")))))
                .append("created_at", new Document("$ifNull", List.of("$created_at", "$$NOW")))
                .append("updated_at", "$$NOW");
        // A second stage, so the peak is compared against the rating this update just produced.
        Document peak = new Document("peak_rating", new Document("$max",
                List.of(new Document("$ifNull", List.of("$peak_rating", 0)), "$rating")));
        return List.of(new Document("$set", fields), new Document("$set", peak));
    }

    private static Document increment(String field, int delta) {
        return new Document("$add", List.of(new Document("$ifNull", List.of("$" + field, 0)), delta));
    }

    @Override
    public StandingPage top(String ladderId, int season, int limit, @Nullable String cursor) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be 1.." + MAX_PAGE_SIZE);
        }
        StandingCursor position = StandingCursor.decode(cursor);
        Bson filter = position == null
                ? key(ladderId, season)
                : and(key(ladderId, season), or(
                        lt("rating", position.rating()),
                        and(eq("rating", position.rating()), gt("player_uuid", position.uuid()))));

        List<LadderStanding> standings = new ArrayList<>(limit + 1);
        for (Document document : collection.find(filter)
                .sort(orderBy(descending("rating"), ascending("player_uuid")))
                .limit(limit + 1)) {
            standings.add(standing(document));
        }
        if (standings.size() <= limit) {
            return new StandingPage(standings, null, false);
        }
        List<LadderStanding> page = standings.subList(0, limit);
        return new StandingPage(page, StandingCursor.after(page.getLast()).encode(), true);
    }

    @Override
    public OptionalLong rankOf(String ladderId, int season, String uuid) {
        Document own = collection.find(key(ladderId, season, uuid)).first();
        if (own == null) {
            return OptionalLong.empty();
        }
        int rating = number(own, "rating", 0);
        long ahead = collection.countDocuments(and(key(ladderId, season), or(
                gt("rating", rating),
                and(eq("rating", rating), lt("player_uuid", uuid)))));
        return OptionalLong.of(ahead + 1);
    }

    @Override
    public long count(String ladderId, int season) {
        return collection.countDocuments(key(ladderId, season));
    }

    @Override
    public List<LadderStanding> leaders(String ladderId, int season, int minMatches, int limit) {
        List<LadderStanding> leaders = new ArrayList<>();
        if (limit < 1) {
            return leaders;
        }
        for (Document document : collection.find(and(key(ladderId, season), gte("matches", minMatches)))
                .sort(orderBy(descending("rating"), ascending("player_uuid")))
                .limit(limit)) {
            leaders.add(standing(document));
        }
        return leaders;
    }

    @Override
    public int assignFinalRanks(String ladderId, int season) {
        writable();
        List<WriteModel<Document>> batch = new ArrayList<>(RANK_BATCH_SIZE);
        int rank = 0;
        for (Document document : collection.find(key(ladderId, season))
                .sort(orderBy(descending("rating"), ascending("player_uuid")))
                .projection(new Document("_id", 1))) {
            batch.add(new UpdateOneModel<>(eq("_id", document.get("_id")),
                    new Document("$set", new Document("final_rank", ++rank))));
            if (batch.size() == RANK_BATCH_SIZE) {
                collection.bulkWrite(batch);
                batch = new ArrayList<>(RANK_BATCH_SIZE);
            }
        }
        if (!batch.isEmpty()) {
            collection.bulkWrite(batch);
        }
        return rank;
    }

    @Override
    public int mergePlayer(@Nullable ClientSession session, String sourceUuid, String targetUuid) {
        writable();
        requireUuid(sourceUuid);
        requireUuid(targetUuid);
        if (sourceUuid.equals(targetUuid)) {
            throw new IllegalArgumentException("Cannot merge a player's standings into themselves");
        }

        List<Document> sources = find(session, eq("player_uuid", sourceUuid)).into(new ArrayList<>());
        for (Document source : sources) {
            Bson sourceId = eq("_id", source.get("_id"));
            Document target = find(session,
                    key(source.getString("ladder"), number(source, "season", 1), targetUuid)).first();
            if (target == null) {
                update(session, sourceId, new Document("$set", new Document("player_uuid", targetUuid))
                        .append("$currentDate", new Document("updated_at", true)));
                continue;
            }
            update(session, eq("_id", target.get("_id")), new Document("$set", merged(source, target))
                    .append("$currentDate", new Document("updated_at", true)));
            if (session != null) {
                collection.deleteOne(session, sourceId);
            } else {
                collection.deleteOne(sourceId);
            }
        }
        return sources.size();
    }

    private static Document merged(Document source, Document target) {
        int rating = Math.max(number(source, "rating", 0), number(target, "rating", 0));
        Map<String, Integer> summed = new HashMap<>(stats(target));
        stats(source).forEach((name, value) -> summed.merge(name, value, Integer::sum));
        Document stats = new Document();
        summed.forEach(stats::append);
        List<String> operations = new ArrayList<>(target.getList("applied_operations", String.class, List.of()));
        operations.addAll(source.getList("applied_operations", String.class, List.of()));
        if (operations.size() > APPLIED_OPERATIONS_LIMIT) {
            operations = operations.subList(operations.size() - APPLIED_OPERATIONS_LIMIT, operations.size());
        }
        return new Document("rating", rating)
                .append("peak_rating", Math.max(rating, Math.max(
                        number(source, "peak_rating", 0), number(target, "peak_rating", 0))))
                .append("matches", number(source, "matches", 0) + number(target, "matches", 0))
                .append("wins", number(source, "wins", 0) + number(target, "wins", 0))
                .append("stats", stats)
                .append("applied_operations", operations);
    }

    private FindIterable<Document> find(@Nullable ClientSession session, Bson filter) {
        return session != null ? collection.find(session, filter) : collection.find(filter);
    }

    private void update(@Nullable ClientSession session, Bson filter, Bson update) {
        if (session != null) {
            collection.updateOne(session, filter, update);
        } else {
            collection.updateOne(filter, update);
        }
    }

    private void writable() {
        if (config.database.readOnly) {
            throw new IllegalStateException("Database is read-only");
        }
    }

    private static Document key(String ladderId, int season) {
        if (ladderId == null || ladderId.isBlank()) {
            throw new IllegalArgumentException("Ladder ID must not be blank");
        }
        return new Document("ladder", ladderId).append("season", season);
    }

    private static Document key(String ladderId, int season, String uuid) {
        requireUuid(uuid);
        return key(ladderId, season).append("player_uuid", uuid);
    }

    private static void requireUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("UUID must not be blank");
        }
    }

    private static Map<String, Integer> stats(Document document) {
        Map<String, Integer> stats = new HashMap<>();
        Document raw = document.get("stats", Document.class);
        if (raw != null) {
            for (var entry : raw.entrySet()) {
                if (entry.getValue() instanceof Number value) {
                    stats.put(entry.getKey(), value.intValue());
                }
            }
        }
        return stats;
    }

    private static LadderStanding standing(Document document) {
        int rating = number(document, "rating", 0);
        return new LadderStanding(
                document.getString("ladder"),
                number(document, "season", 1),
                document.getString("player_uuid"),
                rating,
                Math.max(rating, number(document, "peak_rating", rating)),
                number(document, "matches", 0),
                number(document, "wins", 0),
                stats(document));
    }

    private static int number(Document document, String field, int fallback) {
        return document.get(field) instanceof Number value ? value.intValue() : fallback;
    }
}
