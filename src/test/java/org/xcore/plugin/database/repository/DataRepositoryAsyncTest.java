package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.PlayerData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRepositoryAsyncTest {

    @Test
    @DisplayName("saveAsync returns false when data is null")
    void saveAsync_nullDataReturnsFalse() throws Exception {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection collection = mock(MongoCollection.class);
        when(database.getCollection(anyString(), any())).thenReturn(collection);

        PlayerDataRepository repository = new PlayerDataRepository(database, new TomlSecretsConfig());

        CompletionStage<Boolean> result = repository.saveAsync(null);
        assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    @DisplayName("saveAsync returns false when database is read only")
    void saveAsync_readOnlyReturnsFalse() throws Exception {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection collection = mock(MongoCollection.class);
        when(database.getCollection(anyString(), any())).thenReturn(collection);

        TomlSecretsConfig config = new TomlSecretsConfig();
        config.database.readOnly = true;

        PlayerDataRepository repository = new PlayerDataRepository(database, config);

        PlayerData data = new PlayerData("uuid-1", true);
        CompletionStage<Boolean> result = repository.saveAsync(data);
        assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    @DisplayName("findByIdAsync returns null when id is null")
    void findByIdAsync_nullIdReturnsNull() throws Exception {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection collection = mock(MongoCollection.class);
        when(database.getCollection(anyString(), any())).thenReturn(collection);

        PlayerDataRepository repository = new PlayerDataRepository(database, new TomlSecretsConfig());

        CompletionStage<PlayerData> result = repository.findByIdAsync(null);
        assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("updateByUuidAsync ignores null or blank uuid")
    void updateByUuidAsync_ignoresNullOrBlank() throws Exception {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection collection = mock(MongoCollection.class);
        when(database.getCollection(anyString(), any())).thenReturn(collection);

        PlayerDataRepository repository = new PlayerDataRepository(database, new TomlSecretsConfig());

        assertThat(repository.updatePvpRatingAsync(null, 100).toCompletableFuture().get(1, TimeUnit.SECONDS)).isFalse();
        assertThat(repository.updatePvpRatingAsync("", 100).toCompletableFuture().get(1, TimeUnit.SECONDS)).isFalse();
        assertThat(repository.updatePvpRatingAsync("   ", 100).toCompletableFuture().get(1, TimeUnit.SECONDS)).isFalse();
    }
}
