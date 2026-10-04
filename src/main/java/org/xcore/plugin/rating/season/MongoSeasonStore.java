package org.xcore.plugin.rating.season;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Sorts.orderBy;

/** Seasons in the {@code rating_seasons} collection, one document per (ladder, number). */
@Singleton
public class MongoSeasonStore implements SeasonStore {
    public static final String COLLECTION = "rating_seasons";
    private static final int DUPLICATE_KEY = 11000;
    private static final Bson NEWEST_FIRST = orderBy(ascending("ladder"), descending("number"));

    private final MongoCollection<Document> collection;
    private final TomlSecretsConfig config;

    @Inject
    public MongoSeasonStore(MongoDatabase database, TomlSecretsConfig config) {
        this.collection = database.getCollection(COLLECTION, Document.class);
        this.config = config;
        // Read-only stores must not perform index-management writes during creation.
        if (!config.database.readOnly) {
            collection.createIndex(new Document("ladder", 1).append("number", -1), new IndexOptions().unique(true));
            collection.createIndex(new Document("status", 1));
        }
    }

    @Override
    public Optional<Season> find(String ladderId, int number) {
        return Optional.ofNullable(collection.find(eq("_id", Season.id(ladderId, number))).first())
                .map(MongoSeasonStore::season);
    }

    @Override
    public List<Season> list(String ladderId) {
        return seasons(eq("ladder", ladderId));
    }

    @Override
    public List<Season> all() {
        return seasons(new Document());
    }

    @Override
    public List<Season> open() {
        return seasons(ne("status", SeasonStatus.ARCHIVED.name()));
    }

    @Override
    public boolean create(Season season) {
        writable();
        Date now = new Date();
        try {
            Document document = new Document("_id", season.id())
                    .append("ladder", season.ladderId())
                    .append("number", season.number())
                    .append("starts_at", Date.from(season.startsAt()));
            document.putAll(mutableFields(season));
            collection.insertOne(document
                    .append("matches", season.matches())
                    .append("revision", season.revision())
                    .append("created_at", now)
                    .append("updated_at", now));
            return true;
        } catch (MongoWriteException e) {
            if (e.getCode() == DUPLICATE_KEY) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public Optional<Season> update(Season expected, Season updated) {
        writable();
        // Only the fields a transition owns are written, so the match counter keeps counting.
        Document changes = new Document("$set", mutableFields(updated).append("updated_at", new Date()))
                .append("$inc", new Document("revision", 1L));
        return Optional.ofNullable(collection.findOneAndUpdate(
                        and(eq("_id", expected.id()), eq("revision", expected.revision())),
                        changes,
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)))
                .map(MongoSeasonStore::season);
    }

    @Override
    public void countMatch(String ladderId, int number) {
        writable();
        collection.updateOne(eq("_id", Season.id(ladderId, number)), new Document("$inc", new Document("matches", 1)));
    }

    private List<Season> seasons(Bson filter) {
        List<Season> seasons = new ArrayList<>();
        for (Document document : collection.find(filter).sort(NEWEST_FIRST)) {
            seasons.add(season(document));
        }
        return seasons;
    }

    private void writable() {
        if (config.database.readOnly) {
            throw new IllegalStateException("Database is read-only");
        }
    }

    private static Document mutableFields(Season season) {
        Document fields = new Document("name", season.name())
                .append("ends_at", Date.from(season.endsAt()))
                .append("status", season.status().name())
                .append("sent_notices", List.copyOf(season.sentNotices()))
                .append("podium", season.podium().stream().map(MongoSeasonStore::document).toList())
                .append("rescheduled", season.rescheduled().stream().map(MongoSeasonStore::document).toList())
                .append("prizes", season.prizes().stream().map(MongoSeasonStore::document).toList());
        SeasonSummary summary = season.summary();
        fields.append("summary", summary == null ? null : new Document("participants", summary.participants())
                .append("matches", summary.matches()));
        return fields;
    }

    private static Document document(SeasonPodiumEntry entry) {
        return new Document("place", entry.place())
                .append("uuid", entry.uuid())
                .append("pid", entry.pid())
                .append("nickname", entry.nickname())
                .append("rating", entry.rating())
                .append("league", entry.league())
                .append("matches", entry.matches())
                .append("wins", entry.wins())
                .append("discord_id", entry.discordId())
                .append("discord_username", entry.discordUsername());
    }

    private static Document document(SeasonPrize prize) {
        return new Document("place_from", prize.placeFrom())
                .append("place_to", prize.placeTo())
                .append("kind", prize.kind().name())
                .append("value", prize.value())
                .append("description", prize.description());
    }

    private static Document document(SeasonReschedule change) {
        return new Document("from", Date.from(change.from()))
                .append("to", Date.from(change.to()))
                .append("actor", change.actor())
                .append("at", Date.from(change.at()))
                .append("reason", change.reason());
    }

    private static Season season(Document document) {
        Document summary = document.get("summary", Document.class);
        return new Season(
                document.getString("ladder"),
                number(document, "number", 1),
                document.getString("name"),
                instant(document, "starts_at"),
                instant(document, "ends_at"),
                SeasonStatus.valueOf(document.getString("status")),
                new LinkedHashSet<>(document.getList("sent_notices", String.class, List.of())),
                document.getList("podium", Document.class, List.of()).stream()
                        .map(MongoSeasonStore::podiumEntry).toList(),
                summary == null ? null : new SeasonSummary(
                        summary.get("participants") instanceof Number value ? value.longValue() : 0L,
                        number(summary, "matches", 0)),
                document.getList("rescheduled", Document.class, List.of()).stream()
                        .map(MongoSeasonStore::reschedule).toList(),
                document.getList("prizes", Document.class, List.of()).stream()
                        .map(MongoSeasonStore::prize).toList(),
                number(document, "matches", 0),
                document.get("revision") instanceof Number value ? value.longValue() : 0L);
    }

    private static SeasonPodiumEntry podiumEntry(Document document) {
        return new SeasonPodiumEntry(
                number(document, "place", 0),
                text(document, "uuid"),
                number(document, "pid", -1),
                text(document, "nickname"),
                number(document, "rating", 0),
                text(document, "league"),
                number(document, "matches", 0),
                number(document, "wins", 0),
                text(document, "discord_id"),
                text(document, "discord_username"));
    }

    private static SeasonPrize prize(Document document) {
        int from = number(document, "place_from", 1);
        return new SeasonPrize(from, Math.max(from, number(document, "place_to", from)),
                PrizeKind.parse(text(document, "kind")), text(document, "value"), text(document, "description"));
    }

    private static SeasonReschedule reschedule(Document document) {
        return new SeasonReschedule(instant(document, "from"), instant(document, "to"),
                text(document, "actor"), instant(document, "at"), text(document, "reason"));
    }

    private static Instant instant(Document document, String field) {
        Date value = document.getDate(field);
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private static String text(Document document, String field) {
        @Nullable String value = document.getString(field);
        return value == null ? "" : value;
    }

    private static int number(Document document, String field, int fallback) {
        return document.get(field) instanceof Number value ? value.intValue() : fallback;
    }
}
