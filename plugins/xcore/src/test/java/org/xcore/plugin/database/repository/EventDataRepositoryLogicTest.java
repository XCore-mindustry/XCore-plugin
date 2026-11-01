package org.xcore.plugin.database.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.EventDataRepository.Listing;
import org.xcore.plugin.model.EventData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventDataRepositoryLogicTest {

    private EventDataRepository repository;

    @BeforeEach
    void setUp() {
        MongoDatabase database = mock(MongoDatabase.class);
        @SuppressWarnings("unchecked")
        MongoCollection<EventData> collection = mock(MongoCollection.class);
        when(database.getCollection("events", EventData.class)).thenReturn(collection);

        repository = new EventDataRepository(database, new TomlSecretsConfig());
    }

    @Test
    @DisplayName("upcoming lists what is neither running nor over")
    void getQuery_upcoming_excludesRunningAndFinished() {
        var json = toJson(repository.getQuery(Listing.UPCOMING, null));

        assertThat(json).contains("\"is_finished\": false").contains("\"is_active\": false");
    }

    @Test
    @DisplayName("major lists the major events that are not over")
    void getQuery_major_keepsUnfinishedMajorEvents() {
        var json = toJson(repository.getQuery(Listing.MAJOR, null));

        assertThat(json).contains("\"is_major\": true").contains("\"is_finished\": false");
    }

    @Test
    @DisplayName("past lists the finished events")
    void getQuery_past_keepsFinishedEvents() {
        assertThat(toJson(repository.getQuery(Listing.PAST, null))).contains("\"is_finished\": true");
    }

    @Test
    @DisplayName("mine lists the events of one author, whatever their state")
    void getQuery_mine_filtersByAuthorOnly() {
        ObjectId author = new ObjectId();

        var json = toJson(repository.getQuery(Listing.MINE, author));

        assertThat(json).contains(author.toHexString()).doesNotContain("is_finished");
    }

    @Test
    @DisplayName("upcoming puts the events with a start time first, by that time")
    void getPipeline_upcoming_sortsScheduledFirst() {
        List<String> stages = repository.getPipeline(Listing.UPCOMING, null, 20, 10).stream()
                .map(EventDataRepositoryLogicTest::toJson).toList();

        assertThat(stages).hasSize(6);
        assertThat(stages.get(1)).contains("$addFields").contains("$lte").contains("$planned_start_at");
        assertThat(stages.get(2)).containsSubsequence("unscheduled", "planned_start_at", "created_at");
        assertThat(stages.get(3)).contains("\"$skip\": 20");
        assertThat(stages.get(4)).contains("\"$limit\": 10");
        // The field the sort needs is not a field of an event.
        assertThat(stages.get(5)).contains("$project").contains("\"unscheduled\": 0");
    }

    @Test
    @DisplayName("past puts the latest to end first")
    void getPipeline_past_sortsByEndDescending() {
        List<String> stages = repository.getPipeline(Listing.PAST, null, 0, 10).stream()
                .map(EventDataRepositoryLogicTest::toJson).toList();

        assertThat(stages).hasSize(4);
        assertThat(stages.get(1)).contains("\"ended_at\": -1");
    }

    private static String toJson(Bson query) {
        return query.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson();
    }
}
