package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.GameData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameDataRepositoryLogicTest {
    private MongoCollection<GameData> collection;
    private GameDataRepository repository;

    @BeforeEach
    void setUp() {
        MongoDatabase database = mock(MongoDatabase.class);
        collection = mock(MongoCollection.class);
        when(database.getCollection("games_v2", GameData.class)).thenReturn(collection);
        repository = new GameDataRepository(database, new TomlSecretsConfig());
    }

    @Test
    void saveOnceInsertsNewMatch() {
        var game = GameData.builder().matchId("match-1").build();

        assertThat(repository.saveOnce(game)).isTrue();
        verify(collection).insertOne(game);
    }

    @Test
    void saveOnceTreatsConcurrentDuplicateAsAlreadyStored() {
        var game = GameData.builder().matchId("match-1").build();
        var duplicate = mock(com.mongodb.MongoWriteException.class);
        var error = mock(com.mongodb.WriteError.class);
        when(error.getCode()).thenReturn(11000);
        when(duplicate.getError()).thenReturn(error);
        doThrow(duplicate).when(collection).insertOne(any(GameData.class));

        assertThat(repository.saveOnce(game)).isFalse();
    }

    @Test
    void saveOnceRejectsMissingMatchIdWithoutWriting() {
        var game = GameData.builder().matchId("").build();

        assertThat(repository.saveOnce(game)).isFalse();
        org.mockito.Mockito.verify(collection, org.mockito.Mockito.never()).insertOne(any(GameData.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveOnceAsyncWithStatisticsOffTreatsRetriedIdCollisionAsStored() throws Exception {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection("games_v2", GameData.class)).thenReturn(collection);
        when(collection.getCodecRegistry()).thenReturn(org.bson.codecs.configuration.CodecRegistries.fromRegistries(
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry(),
                org.bson.codecs.configuration.CodecRegistries.fromProviders(
                        org.bson.codecs.pojo.PojoCodecProvider.builder().automatic(true).build())));
        MongoCollection<Document> documents = mock(MongoCollection.class);
        when(database.getCollection("games_v2")).thenReturn(documents);
        var duplicate = mock(com.mongodb.MongoWriteException.class);
        var error = mock(com.mongodb.WriteError.class);
        when(duplicate.getCode()).thenReturn(11000);
        when(duplicate.getError()).thenReturn(error);
        when(error.getMessage()).thenReturn("E11000 duplicate key error collection: xcore.games_v2 index: _id_ dup key");
        doThrow(new com.mongodb.MongoSocketReadException("reset", new com.mongodb.ServerAddress()))
                .doThrow(duplicate).when(documents).insertOne(any(Document.class));
        var totals = mock(org.xcore.plugin.service.stats.PlayerTotalsStore.class);
        var storage = new org.xcore.plugin.concurrent.StorageExecutor(2);
        var retrying = new GameDataRepository(database, null, new TomlSecretsConfig(), totals, storage, null);
        retrying.retryDelaysMs = new long[]{1};
        try {
            // The first insert landed but its reply was lost; the retry hits the _id the driver already assigned.
            var game = GameData.builder().matchId("match-1").build();
            assertThat(retrying.saveOnceAsync(game).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
        } finally {
            storage.shutdown();
        }
    }

    @Test
    void saveOnceAsyncRetriesTransientFailuresThenGivesUp() throws Exception {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection("games_v2", GameData.class)).thenReturn(collection);
        when(collection.getCodecRegistry()).thenReturn(org.bson.codecs.configuration.CodecRegistries.fromRegistries(
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry(),
                org.bson.codecs.configuration.CodecRegistries.fromProviders(
                        org.bson.codecs.pojo.PojoCodecProvider.builder().automatic(true).build())));
        var totals = mock(org.xcore.plugin.service.stats.PlayerTotalsStore.class);
        when(totals.enabled()).thenReturn(true);
        var lost = new com.mongodb.MongoSocketReadException("reset", new com.mongodb.ServerAddress());
        when(totals.write(any())).thenThrow(lost).thenReturn(false);
        var storage = new org.xcore.plugin.concurrent.StorageExecutor(2);
        var retrying = new GameDataRepository(database, null, new TomlSecretsConfig(), totals, storage, null);
        retrying.retryDelaysMs = new long[]{1};
        try {
            // The first commit reply was lost; the replay finds the stored match and reports a duplicate.
            var game = GameData.builder().matchId("match-1").build();
            assertThat(retrying.saveOnceAsync(game).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS)).isFalse();
            verify(totals, org.mockito.Mockito.times(2)).write(any());

            doThrow(lost).when(totals).write(any());
            var failed = retrying.saveOnceAsync(GameData.builder().matchId("match-2").build()).toCompletableFuture();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> failed.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .hasCause(lost);

            var invalid = new IllegalArgumentException("Missing immutable payload hash");
            doThrow(invalid).when(totals).write(any());
            var rejected = retrying.saveOnceAsync(GameData.builder().matchId("match-3").build()).toCompletableFuture();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> rejected.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .hasCause(invalid);
            verify(totals, org.mockito.Mockito.times(5)).write(any());
        } finally {
            storage.shutdown();
        }
    }

    @Test
    void shutdownRunsAPendingRetryBeforeStorageCloses() throws Exception {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection("games_v2", GameData.class)).thenReturn(collection);
        when(collection.getCodecRegistry()).thenReturn(org.bson.codecs.configuration.CodecRegistries.fromRegistries(
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry(),
                org.bson.codecs.configuration.CodecRegistries.fromProviders(
                        org.bson.codecs.pojo.PojoCodecProvider.builder().automatic(true).build())));
        var totals = mock(org.xcore.plugin.service.stats.PlayerTotalsStore.class);
        when(totals.enabled()).thenReturn(true);
        var firstAttempt = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(totals.write(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() > 0) return true;
            firstAttempt.countDown();
            throw new com.mongodb.MongoSocketReadException("reset", new com.mongodb.ServerAddress());
        });
        var storage = new org.xcore.plugin.concurrent.StorageExecutor(2);
        var repository = new GameDataRepository(database, null, new TomlSecretsConfig(), totals, storage, null);
        // Far longer than the test: only the shutdown can bring the retry forward.
        repository.retryDelaysMs = new long[]{600_000};
        try {
            var write = repository.saveOnceAsync(GameData.builder().matchId("match-1").build()).toCompletableFuture();
            assertThat(firstAttempt.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            repository.drain();

            assertThat(write).isCompletedWithValue(true);
            verify(totals, org.mockito.Mockito.times(2)).write(any());
        } finally {
            storage.shutdown();
        }
    }
}
