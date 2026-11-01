package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import com.mongodb.reactivestreams.client.FindPublisher;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.MapData;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MapDataRepositoryReadForwardTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void missingMapDoesNotCreatePersistentIdentityAndDuplicatesAreRejected() {
        var database = mock(MongoDatabase.class);
        var sync = mock(com.mongodb.client.MongoCollection.class);
        when(database.getCollection("maps", MapData.class)).thenReturn(sync);
        var store = mock(ReactiveMongoStore.class);
        var collection = mock(com.mongodb.reactivestreams.client.MongoCollection.class);
        when(store.collection("maps", MapData.class)).thenReturn(collection);
        FindPublisher<MapData> query = mock(FindPublisher.class);
        when(collection.find(any(org.bson.conversions.Bson.class))).thenReturn(query);
        when(query.limit(anyInt())).thenReturn(query);
        var rows = new java.util.concurrent.atomic.AtomicReference<>(List.<MapData>of());
        doAnswer(call -> {
            Subscriber<? super MapData> subscriber = call.getArgument(0);
            subscriber.onSubscribe(new Subscription() {
                boolean sent;
                public void request(long n) {
                    if (sent) return;
                    sent = true;
                    rows.get().forEach(subscriber::onNext);
                    subscriber.onComplete();
                }
                public void cancel() {}
            });
            return null;
        }).when(query).subscribe(any());
        var repository = new MapDataRepository(database, store, new TomlSecretsConfig());
        assertThat(repository.findExistingAsync("A", "a.msav", "B", "survival").toCompletableFuture().join()).isNull();
        verify(collection, never()).insertOne(any());
        var first = new MapData("A", "a.msav", "B", "survival");
        first.playedTimes = 10;
        var second = new MapData("A", "a.msav", "B", "survival");
        second.playedTimes = 5;
        rows.set(List.of(first, second));
        // Resilient: picks the highest-played duplicate rather than failing the future
        assertThat(repository.findExistingAsync("A", "a.msav", "B", "survival").toCompletableFuture().join()).isSameAs(first);
        verify(sync, never()).find(any(org.bson.conversions.Bson.class));

        // Parameter null safety
        assertThat(repository.findExistingAsync(null, null, null, null).toCompletableFuture().join()).isNull();
        assertThat(repository.findExistingAsync("A", "", "B", "survival").toCompletableFuture().join()).isNull();
        rows.set(List.of(first));
        assertThat(repository.findExistingAsync(null, "a.msav", null, "survival").toCompletableFuture().join()).isNotNull();
    }
}
