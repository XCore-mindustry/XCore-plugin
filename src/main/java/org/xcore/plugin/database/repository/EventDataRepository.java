package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.EventData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;

@Singleton
public class EventDataRepository extends DataRepository<EventData> {

    @Inject
    public EventDataRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        super(database, "events", EventData.class, secretsConfig);

        collection.createIndex(new Document("name", 1).append("map", 1).append("author", 1));
        collection.createIndex(new Document("is_active", -1));
        collection.createIndex(new Document("is_temporary", -1));
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

    public Bson getQuery(Map<String, StatusEnum> filters) {
        List<Bson> pipeline = new ArrayList<>();
        if (filters != null) {
            StatusEnum finished = filters.get("finished");
            if (finished == StatusEnum.Active) pipeline.add(Filters.eq("is_finished", true));
            if (finished == StatusEnum.Inactive) pipeline.add(Filters.eq("is_finished", false));

            StatusEnum major = filters.get("major");
            if (major == StatusEnum.Active) pipeline.add(Filters.eq("is_major", true));
            if (major == StatusEnum.Inactive) pipeline.add(Filters.eq("is_major", false));

            StatusEnum active = filters.get("active");
            if (active == StatusEnum.Active) pipeline.add(Filters.eq("is_active", true));
            if (active == StatusEnum.Inactive) pipeline.add(Filters.eq("is_active", false));
        }
        return pipeline.isEmpty() ? new Document() : Filters.and(pipeline);
    }

    public List<EventData> findPage(int skip, int limit, Map<String, StatusEnum> filters) {
        return collection.find(getQuery(filters))
                .sort(new Document("created_at", -1).append("_id", 1))
                .skip(skip)
                .limit(limit)
                .into(new ArrayList<>());
    }

    public long count(Map<String, StatusEnum> filters) {
        return collection.countDocuments(getQuery(filters));
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
