package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.MongoAsync;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.EventData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;

@Singleton
public class EventDataRepository extends DataRepository<EventData> {

    /** A field the upcoming listings sort by and no event is stored with. */
    private static final String UNSCHEDULED = "unscheduled";

    @Inject
    public EventDataRepository(MongoDatabase database, ReactiveMongoStore reactiveMongoStore, TomlSecretsConfig secretsConfig) {
        super(database, reactiveMongoStore, "events", EventData.class, secretsConfig);

        collection.createIndex(new Document("name", 1).append("map", 1).append("author", 1));
        collection.createIndex(new Document("is_active", -1));
        collection.createIndex(new Document("is_temporary", -1));
    }

    public EventDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        this(database, null, secretsConfig);
    }

    public Optional<EventData> find(String name, ObjectId author, ObjectId map) {
        return Optional.ofNullable(
            collection.find(and(
                eq("name", name),
                eq("map", map),
                eq("author", author)
            )).first()
        );
    }

    public EventData findOrCreate(String name, ObjectId author, ObjectId map) {
        return find(name, author, map).orElseGet(() -> {
            EventData event = new EventData(name, author, map);
            save(event);
            return event;
        });
    }

    /** What a page of the events menu lists. */
    public enum Listing {
        /** Neither running nor over: the ones with a start time by that time, then the rest, newest first. */
        UPCOMING,
        /** The major ones that are not over, in the order of {@link #UPCOMING}. */
        MAJOR,
        /** Over, the latest to end first. */
        PAST,
        /** Everything one author made, newest first. */
        MINE
    }

    /** @param author whose events {@link Listing#MINE} lists; ignored by the other listings */
    public Bson getQuery(Listing listing, ObjectId author) {
        return switch (listing) {
            case UPCOMING -> and(eq("is_finished", false), eq("is_active", false));
            case MAJOR -> and(eq("is_major", true), eq("is_finished", false));
            case PAST -> eq("is_finished", true);
            case MINE -> eq("author", author);
        };
    }

    /** The stages that put the events of {@code listing} in its order and cut out one page. */
    List<Bson> getPipeline(Listing listing, ObjectId author, int skip, int limit) {
        List<Bson> stages = new ArrayList<>();
        stages.add(Aggregates.match(getQuery(listing, author)));
        switch (listing) {
            case UPCOMING, MAJOR -> {
                // A start time of 0 means none was set, and a plain sort would put those first.
                stages.add(Aggregates.addFields(new Field<>(UNSCHEDULED,
                        new Document("$lte", List.of("$planned_start_at", 0)))));
                stages.add(Aggregates.sort(new Document(UNSCHEDULED, 1).append("planned_start_at", 1)
                        .append("created_at", -1).append("_id", 1)));
            }
            case PAST -> stages.add(Aggregates.sort(new Document("ended_at", -1).append("_id", -1)));
            case MINE -> stages.add(Aggregates.sort(new Document("created_at", -1).append("_id", 1)));
        }
        stages.add(Aggregates.skip(skip));
        stages.add(Aggregates.limit(limit));
        if (listing == Listing.UPCOMING || listing == Listing.MAJOR) {
            stages.add(Aggregates.project(Projections.exclude(UNSCHEDULED)));
        }
        return stages;
    }

    public List<EventData> findPage(Listing listing, ObjectId author, int skip, int limit) {
        return collection.aggregate(getPipeline(listing, author, skip, limit)).into(new ArrayList<>());
    }

    public long count(Listing listing, ObjectId author) {
        return collection.countDocuments(getQuery(listing, author));
    }

    /**
     * Adds to the votes of an event in the database itself, so two players voting at once both
     * count: saving the event as a viewer read it would overwrite the other's vote.
     */
    public void applyVote(ObjectId id, int likeDelta, int dislikeDelta) {
        if (id == null || isReadOnly()) return;
        collection.updateOne(eq("_id", id), Updates.combine(
                Updates.inc("like", likeDelta),
                Updates.inc("dislike", dislikeDelta),
                Updates.set("updated_at", System.currentTimeMillis())));
    }

    public Optional<EventData> findNextScheduled() {
        long now = System.currentTimeMillis();
        return Optional.ofNullable(
            collection.find(and(
                eq("is_active", false),
                eq("is_finished", false),
                gt("planned_start_at", 0),
                lte("planned_start_at", now)
            ))
            .sort(new Document("planned_start_at", 1))
            .first()
        );
    }

    public Optional<EventData> findActive() {
        return Optional.ofNullable(collection.find(eq("is_active", true)).first());
    }

    public void activateEvent(EventData event) {
        finishActiveEvent();

        collection.updateMany(new Document("is_active", true), new Document("$set", new Document("is_active", false)));

        event.isActive = true;
        event.startTime = System.currentTimeMillis();
        save(event);
    }

    public void finishActiveEvent() {
        findActive().ifPresent(event -> {
            event.isActive = false;
            event.isFinished = true;
            event.endTime = System.currentTimeMillis();
            save(event);
        });
    }

    public void resetActivity() {
        collection.updateMany(new Document("is_active", true), new Document("$set", new Document("is_active", false)));
    }
}
