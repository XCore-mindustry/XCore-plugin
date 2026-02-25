package org.xcore.plugin.database.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.EventData;

import java.util.Map;

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

        repository = new EventDataRepository(database, new GlobalConfig());
    }

    @Test
    @DisplayName("getQuery returns empty document when no filters")
    void getQuery_returnsEmptyDocument_whenNoFilters() {
        var query = repository.getQuery(null);

        assertThat(toJson(query)).isEqualTo("{}");
    }

    @Test
    @DisplayName("getQuery includes finished filter when active")
    void getQuery_includesFinishedFilter_whenActive() {
        var query = repository.getQuery(Map.of("finished", StatusEnum.Active));

        assertThat(toJson(query)).contains("\"is_finished\": true");
    }

    @Test
    @DisplayName("getQuery includes major filter when inactive")
    void getQuery_includesMajorFilter_whenInactive() {
        var query = repository.getQuery(Map.of("major", StatusEnum.Inactive));

        assertThat(toJson(query)).contains("\"is_major\": false");
    }

    @Test
    @DisplayName("getQuery includes active filter when active")
    void getQuery_includesActiveFilter_whenActive() {
        var query = repository.getQuery(Map.of("active", StatusEnum.Active));

        assertThat(toJson(query)).contains("\"is_active\": true");
    }

    @Test
    @DisplayName("getQuery combines multiple filters with and")
    void getQuery_combinesMultipleFilters_withAnd() {
        var query = repository.getQuery(Map.of(
                "finished", StatusEnum.Active,
                "major", StatusEnum.Inactive,
                "active", StatusEnum.Active
        ));
        var json = toJson(query);

        assertThat(json).contains("\"$and\"");
        assertThat(json).contains("\"is_finished\": true");
        assertThat(json).contains("\"is_major\": false");
        assertThat(json).contains("\"is_active\": true");
    }

    private static String toJson(Bson query) {
        return query.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson();
    }
}
