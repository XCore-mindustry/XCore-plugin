package org.xcore.plugin.integration.playerstorage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginPlayerStoreFactoryTest {
    private static final PlayerStoreSchema SCHEMA = PlayerStoreSchema.builder()
            .field("score", FieldType.INT, true)
            .field("name", FieldType.STRING, false)
            .build();

    @Test
    void deleteReflectsMongoDeletedCount() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        var store = store(collection);
        DeleteResult result = mock(DeleteResult.class);
        when(collection.deleteOne(any(Bson.class))).thenReturn(result);

        when(result.getDeletedCount()).thenReturn(0L);
        assertThat(store.delete("missing")).isFalse();

        when(result.getDeletedCount()).thenReturn(1L);
        assertThat(store.delete("present")).isTrue();
    }

    @Test
    void writesCapturePlayerIdentityRevisionAndRequestedPaths() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        var store = store(collection);
        UpdateResult result = mock(UpdateResult.class);
        when(result.wasAcknowledged()).thenReturn(true);
        when(result.getMatchedCount()).thenReturn(1L);
        when(collection.updateOne(any(Bson.class), any(List.class), any(com.mongodb.client.model.UpdateOptions.class)))
                .thenReturn(result);

        assertThat(store.set("player-1", "name", "Alice")).isTrue();
        Document setFields = setFields(capturePlan(collection));
        assertThat(setFields.get("player_uuid", Document.class))
                .containsEntry("$ifNull", List.of("$player_uuid", new Document("$literal", "player-1")));
        assertThat(setFields).containsKey("revision");
        assertThat(setFields.get("data.name", Document.class))
                .containsEntry("$literal", "Alice");

        assertThat(store.remove("player-1", "name")).isTrue();
        List<Document> removePlan = capturePlan(collection);
        assertThat(removePlan).hasSize(2);
        assertThat(removePlan.get(1)).containsEntry("$unset", "data.name");
        assertThat(setFields(removePlan)).containsKey("revision");

        assertThat(store.increment("player-1", "score", 2)).isTrue();
        Document increment = setFields(capturePlan(collection));
        assertThat(increment.get("data.score", Document.class))
                .containsKey("$add");
        assertThat(increment).containsKeys("player_uuid", "revision");
    }

    private static PluginPlayerStoreFactory.MongoPlayerStore store(MongoCollection<Document> collection) {
        return new PluginPlayerStoreFactory.MongoPlayerStore(collection, SCHEMA, new TomlSecretsConfig());
    }

    @SuppressWarnings("unchecked")
    private static List<Document> capturePlan(MongoCollection<Document> collection) {
        ArgumentCaptor<List> plan = ArgumentCaptor.forClass(List.class);
        verify(collection, atLeastOnce()).updateOne(any(Bson.class), plan.capture(), any(com.mongodb.client.model.UpdateOptions.class));
        return (List<Document>) plan.getValue();
    }

    private static Document setFields(List<Document> plan) {
        return plan.get(0).get("$set", Document.class);
    }
}
