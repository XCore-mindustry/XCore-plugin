package org.xcore.plugin.rating.match;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Sorts.orderBy;

/** Matches in the {@code rating_matches} collection, one document per (ladder, match). */
@Singleton
public class MongoMatchStore implements MatchStore {
    public static final String COLLECTION = "rating_matches";

    private final MongoCollection<Document> collection;
    private final TomlSecretsConfig config;

    @Inject
    public MongoMatchStore(MongoDatabase database, TomlSecretsConfig config) {
        this(database.getCollection(COLLECTION, Document.class), config);
        // Read-only stores must not perform index-management writes during creation.
        if (!config.database.readOnly) {
            collection.createIndex(new Document("ladder", 1).append("participants.uuid", 1)
                    .append("ended_at", -1).append("_id", -1));
            collection.createIndex(new Document("ladder", 1).append("ended_at", -1),
                    new IndexOptions().name("ladder_ended_at"));
        }
    }

    MongoMatchStore(MongoCollection<Document> collection, TomlSecretsConfig config) {
        this.collection = collection;
        this.config = config;
    }

    @Override
    public void record(MatchRecord match) {
        writable();
        collection.replaceOne(eq("_id", match.id()), document(match), new ReplaceOptions().upsert(true));
    }

    @Override
    public MatchPage page(String ladderId, String uuid, int limit, @Nullable String cursor) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be 1.." + MAX_PAGE_SIZE);
        }
        Bson player = playerFilter(ladderId, uuid);
        MatchCursor position = MatchCursor.decode(cursor);
        Bson filter = position == null ? player : and(player, or(
                lt("ended_at", new Date(position.endedAt())),
                and(eq("ended_at", new Date(position.endedAt())), lt("_id", position.id()))));

        List<MatchRecord> matches = new ArrayList<>(limit + 1);
        for (Document document : collection.find(filter)
                .projection(ownEntry(uuid))
                .sort(orderBy(descending("ended_at"), descending("_id")))
                .limit(limit + 1)) {
            matches.add(match(document));
        }
        if (matches.size() <= limit) {
            return new MatchPage(matches, null);
        }
        List<MatchRecord> page = matches.subList(0, limit);
        return new MatchPage(page, MatchCursor.after(page.getLast()).encode());
    }

    @Override
    public long count(String ladderId, String uuid) {
        return collection.countDocuments(playerFilter(ladderId, uuid));
    }

    @Override
    public Optional<MatchRecord> find(String ladderId, String matchId) {
        return Optional.ofNullable(collection.find(eq("_id", MatchRecord.id(ladderId, matchId))).first())
                .map(MongoMatchStore::match);
    }

    @Override
    public Optional<Instant> firstRecorded(String ladderId) {
        Document first = collection.find(eq("ladder", ladderId))
                .projection(new Document("ended_at", 1))
                .sort(ascending("ended_at"))
                .limit(1)
                .first();
        return first != null && first.getDate("ended_at") != null
                ? Optional.of(first.getDate("ended_at").toInstant())
                : Optional.empty();
    }

    @Override
    public long reassignPlayer(@Nullable ClientSession session, String sourceUuid, String targetUuid) {
        writable();
        if (sourceUuid == null || sourceUuid.isBlank() || targetUuid == null || targetUuid.isBlank()) {
            throw new IllegalArgumentException("UUID must not be blank");
        }
        Bson filter = eq("participants.uuid", sourceUuid);
        Bson update = Updates.set("participants.$[elem].uuid", targetUuid);
        var options = new UpdateOptions().arrayFilters(List.of(Filters.eq("elem.uuid", sourceUuid)));
        return session != null
                ? collection.updateMany(session, filter, update, options).getModifiedCount()
                : collection.updateMany(filter, update, options).getModifiedCount();
    }

    /**
     * Every field of a match but the other participants. An {@code $elemMatch} projection keeps
     * only what it names, so the rest is named too.
     */
    private static Document ownEntry(String uuid) {
        Document projection = new Document();
        for (String field : List.of("ladder", "season", "match_id", "started_at", "ended_at", "rated",
                "skip_reason", "finish", "map", "algorithm", "players", "team_sizes")) {
            projection.append(field, 1);
        }
        return projection.append("participants", new Document("$elemMatch", new Document("uuid", uuid)));
    }

    private static Bson playerFilter(String ladderId, String uuid) {
        if (ladderId == null || ladderId.isBlank()) throw new IllegalArgumentException("Ladder ID must not be blank");
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("UUID must not be blank");
        return and(eq("ladder", ladderId), eq("participants.uuid", uuid));
    }

    private void writable() {
        if (config.database.readOnly) {
            throw new IllegalStateException("Database is read-only");
        }
    }

    static Document document(MatchRecord match) {
        Document teams = new Document();
        match.teamSizes().forEach((team, size) -> teams.append(Integer.toString(team), size));
        List<Document> participants = new ArrayList<>(match.participants().size());
        for (MatchParticipant p : match.participants()) {
            Document extra = new Document();
            p.extra().forEach(extra::append);
            participants.add(new Document("uuid", p.uuid())
                    .append("name", p.name())
                    .append("team", p.team())
                    .append("placement", p.placement())
                    .append("win", p.win())
                    .append("rating_before", p.ratingBefore())
                    .append("delta", p.delta())
                    .append("rating_after", p.ratingAfter())
                    .append("counted", p.counted())
                    .append("reason", p.reason())
                    .append("participation", p.participation())
                    .append("extra", extra));
        }
        return new Document("_id", match.id())
                .append("ladder", match.ladder())
                .append("season", match.season())
                .append("match_id", match.matchId())
                .append("started_at", Date.from(match.startedAt()))
                .append("ended_at", Date.from(match.endedAt()))
                .append("rated", match.rated())
                .append("skip_reason", match.skipReason())
                .append("finish", match.finish())
                .append("map", match.map())
                .append("algorithm", match.algorithm())
                .append("players", match.players())
                .append("team_sizes", teams)
                .append("participants", participants);
    }

    static MatchRecord match(Document document) {
        Map<Integer, Integer> teams = new HashMap<>();
        Document rawTeams = document.get("team_sizes", Document.class);
        if (rawTeams != null) {
            for (var entry : rawTeams.entrySet()) {
                if (entry.getValue() instanceof Number size) {
                    try {
                        teams.put(Integer.parseInt(entry.getKey()), size.intValue());
                    } catch (NumberFormatException ignored) {
                        // A team that is not a number is not one this plugin wrote.
                    }
                }
            }
        }
        List<MatchParticipant> participants = new ArrayList<>();
        for (Document p : document.getList("participants", Document.class, List.of())) {
            participants.add(participant(p));
        }
        Date started = document.getDate("started_at");
        Date ended = document.getDate("ended_at");
        Instant endedAt = ended != null ? ended.toInstant() : Instant.EPOCH;
        return new MatchRecord(
                document.getString("ladder"),
                number(document, "season", 1),
                document.getString("match_id"),
                started != null ? started.toInstant() : endedAt,
                endedAt,
                document.getString("skip_reason"),
                document.getString("finish"),
                document.getString("map"),
                document.getString("algorithm"),
                number(document, "players", participants.size()),
                teams,
                participants);
    }

    private static MatchParticipant participant(Document p) {
        Map<String, Integer> extra = new HashMap<>();
        Document rawExtra = p.get("extra", Document.class);
        if (rawExtra != null) {
            for (var entry : rawExtra.entrySet()) {
                if (entry.getValue() instanceof Number value) {
                    extra.put(entry.getKey(), value.intValue());
                }
            }
        }
        int before = number(p, "rating_before", 0);
        return new MatchParticipant(
                p.getString("uuid"),
                p.getString("name"),
                p.get("team") instanceof Number team ? team.intValue() : null,
                Math.max(1, number(p, "placement", 1)),
                Boolean.TRUE.equals(p.getBoolean("win")),
                before,
                number(p, "delta", 0),
                number(p, "rating_after", before),
                Boolean.TRUE.equals(p.getBoolean("counted")),
                p.getString("reason") != null ? p.getString("reason") : "unknown",
                p.get("participation") instanceof Number share ? share.doubleValue() : null,
                extra);
    }

    private static int number(Document document, String field, int fallback) {
        return document.get(field) instanceof Number value ? value.intValue() : fallback;
    }
}
