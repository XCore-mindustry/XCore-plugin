package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.xcore.plugin.model.EventData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;

@Singleton
public class EventDataRepository extends DataRepository<EventData> {

    @Inject
    public EventDataRepository(MongoDatabase database) {
        super(database, "events", EventData.class);

        collection.createIndex(new Document("name", 1).append("map", 1).append("author", 1));
        collection.createIndex(new Document("isActive", -1));
        collection.createIndex(new Document("isTemporary", -1));
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

    public List<EventData> findUpcoming(int skip, int limit) {
        return collection.find(and(eq("isConducted", false), eq("isActive", false)))
                .sort(new Document("scheduledTime", 1))
                .skip(skip).limit(limit).into(new ArrayList<>());
    }

    public List<EventData> findArchive(int skip, int limit) {
        return collection.find(eq("isConducted", true))
                .sort(new Document("endTime", -1))
                .skip(skip).limit(limit).into(new ArrayList<>());
    }

    public List<EventData> findPage(int skip, int limit) {
        return collection.find()
                .skip(skip)
                .limit(limit)
                .sort(new Document("createdTime", -1))
                .into(new ArrayList<>());
    }

    public Optional<EventData> findNextScheduled() {
        long now = System.currentTimeMillis();
        return Optional.ofNullable(
            collection.find(and(
                eq("isActive", false),
                eq("isConducted", false),
                gt("scheduledTime", 0),
                lte("scheduledTime", now)
            ))
            .sort(new Document("scheduledTime", 1))
            .first()
        );
    }

    public Optional<EventData> findActive() {
        return Optional.ofNullable(collection.find(eq("isActive", true)).first());
    }

    public void activateEvent(EventData event) {
        finishActiveEvent();

        collection.updateMany(new Document("isActive", true), new Document("$set", new Document("isActive", false)));

        event.isActive = true;
        event.startTime = System.currentTimeMillis();
        save(event);
    }

    public void finishActiveEvent() {
        findActive().ifPresent(event -> {
            event.isActive = false;
            event.isConducted = true;
            event.endTime = System.currentTimeMillis();
            save(event);
        });
    }

    public void resetActivity() {
        collection.updateMany(new Document("isActive", true), new Document("$set", new Document("isActive", false)));
    }

    public long count() {
        return collection.countDocuments();
    }
}
