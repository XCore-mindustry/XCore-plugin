package org.xcore.plugin.service.map;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.map.domain.MapContentHash;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class MapIdentityCatalogTest {
    @Test
    void obsoleteReloadCannotReplaceCurrentSnapshot() {
        var hashing = mock(MapContentHashService.class);
        var oldHash = new CompletableFuture<MapContentHash>();
        var newHash = new CompletableFuture<MapContentHash>();
        when(hashing.hashAsync(any())).thenReturn(oldHash, newHash);
        var main = new ArrayDeque<Runnable>();
        var catalog = new MapIdentityCatalog(hashing, main::add);
        var old = catalog.rebuild(List.of(source("old.msav")));
        var current = catalog.rebuild(List.of(source("new.msav")));
        newHash.complete(MapContentHash.fromHex("a".repeat(64)));
        assertThat(catalog.snapshot().entries()).isEmpty();
        main.remove().run();
        assertThat(current.toCompletableFuture().join()).isTrue();
        oldHash.complete(MapContentHash.fromHex("b".repeat(64)));
        main.remove().run();
        assertThat(old.toCompletableFuture().join()).isFalse();
        assertThat(catalog.snapshot().entries()).extracting(MapIdentityCatalog.Entry::fileName).containsExactly("new.msav");
        assertThatThrownBy(() -> catalog.snapshot().entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void readFailureIsExplicitAndDoesNotInventIdentity() {
        var hashing = mock(MapContentHashService.class);
        when(hashing.hashAsync(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unreadable")));
        var catalog = new MapIdentityCatalog(hashing, Runnable::run);
        assertThat(catalog.rebuild(List.of(source("bad.msav"))).toCompletableFuture().join()).isTrue();
        assertThat(catalog.snapshot().entries()).isEmpty();
        assertThat(catalog.snapshot().failures()).containsKey("bad.msav");
    }

    private MapIdentityCatalog.Source source(String filename) {
        return new MapIdentityCatalog.Source(filename, "Arena", "Author", 10, 20,
                () -> new ByteArrayInputStream(new byte[]{1}));
    }

    private static class CountingDispatcher implements java.util.function.Consumer<Runnable> {
        final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();
        int queued;
        public void accept(Runnable task) {
            tasks.add(task);
            queued++;
        }
    }

    @Test
    void rebuildSchedulesSingleMainPublicationPerGeneration() {
        var hashing = mock(MapContentHashService.class);
        when(hashing.hashAsync(any())).thenReturn(CompletableFuture.completedFuture(MapContentHash.fromHex("a".repeat(64))));
        var main = new CountingDispatcher();
        var catalog = new MapIdentityCatalog(hashing, main::accept);
        catalog.rebuild(List.of(source("one.msav"), source("two.msav")));
        while (!main.tasks.isEmpty()) main.tasks.remove().run();
        assertThat(main.queued).isEqualTo(1);
        assertThat(catalog.snapshot().entries()).hasSize(2);
    }
}
