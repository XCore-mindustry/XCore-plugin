package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.map.domain.MapEntity;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MapEntityRepositoryTest {

    @Test
    @DisplayName("Creates indexes and delegates async queries to reactive collection")
    @SuppressWarnings("unchecked")
    void createsIndexesAndQueriesByHashAndSlug() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<MapEntity> syncCol = mock(MongoCollection.class);
        when(database.getCollection(eq("map_entities"), eq(MapEntity.class))).thenReturn(syncCol);

        ReactiveMongoStore reactiveStore = mock(ReactiveMongoStore.class);
        com.mongodb.reactivestreams.client.MongoCollection<MapEntity> reactiveCol = mock(com.mongodb.reactivestreams.client.MongoCollection.class);
        when(reactiveStore.collection(eq("map_entities"), eq(MapEntity.class))).thenReturn(reactiveCol);

        TomlSecretsConfig secrets = new TomlSecretsConfig();
        MapEntityRepository repository = new MapEntityRepository(database, reactiveStore, secrets);

        // Verify index creation
        verify(syncCol).createIndex(eq(new Document("content_hash", 1)), any(com.mongodb.client.model.IndexOptions.class));
        verify(syncCol).createIndex(eq(new Document("slug", 1)));
        verify(syncCol).createIndex(eq(new Document("popularity", -1)));
        verify(syncCol).createIndex(eq(new Document("reputation", -1)));

        // Async query delegation returns stage without hitting sync collection
        when(reactiveCol.find(any(org.bson.conversions.Bson.class))).thenReturn(mock(com.mongodb.reactivestreams.client.FindPublisher.class));

        assertThat(repository.findByContentHashAsync(null)).isCompletedWithValue(null);
        assertThat(repository.findBySlugAsync(null)).isCompletedWithValue(null);
    }
}
