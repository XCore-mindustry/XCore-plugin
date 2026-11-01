package org.xcore.plugin.service.map;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapSummaryCacheTest {
    @Test
    void expiredSnapshotSurvivesFailedRefreshAndCanRetry() {
        var repository = mock(MapDataRepository.class);
        var time = new AtomicLong();
        var row = new MapData("A", "a.msav", "B", "survival");
        row.like = 2;
        var failed = new CompletableFuture<List<MapData>>();
        when(repository.findAllAsync()).thenReturn(CompletableFuture.completedFuture(List.of(row)), failed,
                CompletableFuture.completedFuture(List.of()));
        var cache = new MapSummaryCache(repository, time::get);
        cache.refresh().toCompletableFuture().join();
        time.set(60_001);
        var refresh = cache.refresh();
        assertEquals(2, cache.snapshot().getFirst().likes());
        failed.completeExceptionally(new IllegalStateException("offline"));
        assertTrue(refresh.toCompletableFuture().isCompletedExceptionally());
        assertEquals(2, cache.snapshot().getFirst().likes());
        assertTrue(cache.refresh().toCompletableFuture().join().isEmpty());
    }

    @Test
    void concurrentReadersShareRefreshAndReceiveImmutableSnapshot() {
        var repository = mock(MapDataRepository.class);
        var pending = new CompletableFuture<List<MapData>>();
        when(repository.findAllAsync()).thenReturn(pending);
        var cache = new MapSummaryCache(repository, new AtomicLong()::get);

        var first = cache.refresh();
        var second = cache.refresh();
        assertSame(first, second);
        assertTrue(cache.snapshot().isEmpty());
        verify(repository, times(1)).findAllAsync();

        var data = new MapData("Arena", "arena.msav", "Author", "survival");
        data.like = 7;
        pending.complete(List.of(data));
        assertEquals(7, first.toCompletableFuture().join().getFirst().likes());
        data.like = 99;
        assertEquals(7, cache.snapshot().getFirst().likes());
        assertThrows(UnsupportedOperationException.class, () -> cache.snapshot().clear());
        cache.refresh();
        verify(repository, times(1)).findAllAsync();
    }

    @Test
    void optimisticVotePatchUpdatesCachedSnapshotImmediately() {
        var repository = mock(MapDataRepository.class);
        var row = new MapData("Arena", "arena.msav", "Author", "survival");
        row.like = 5;
        row.dislike = 1;
        when(repository.findAllAsync()).thenReturn(CompletableFuture.completedFuture(List.of(row)));
        var cache = new MapSummaryCache(repository);
        cache.refresh().toCompletableFuture().join();

        assertEquals(5, cache.snapshot().getFirst().likes());
        assertEquals(1, cache.snapshot().getFirst().dislikes());

        cache.patchVoteOptimistic("arena.msav", 1, 0);
        assertEquals(6, cache.snapshot().getFirst().likes());
        assertEquals(1, cache.snapshot().getFirst().dislikes());

        cache.patchVoteOptimistic("arena.msav", -1, 1);
        assertEquals(5, cache.snapshot().getFirst().likes());
        assertEquals(2, cache.snapshot().getFirst().dislikes());
    }

    @Test
    void optimisticVotePatchMatchesGamemodeWhenMultipleRowsExist() {
        var repository = mock(MapDataRepository.class);
        var survivalRow = new MapData("Arena", "arena.msav", "Author", "survival");
        survivalRow.like = 5;
        var pvpRow = new MapData("Arena", "arena.msav", "Author", "pvp");
        pvpRow.like = 10;
        when(repository.findAllAsync()).thenReturn(CompletableFuture.completedFuture(List.of(survivalRow, pvpRow)));
        var cache = new MapSummaryCache(repository);
        cache.refresh().toCompletableFuture().join();

        // Patch only survival
        cache.patchVoteOptimistic("arena.msav", "survival", 1, 0);

        var snap = cache.snapshot();
        assertEquals(6, snap.stream().filter(s -> "survival".equals(s.mode())).findFirst().get().likes());
        assertEquals(10, snap.stream().filter(s -> "pvp".equals(s.mode())).findFirst().get().likes()); // Untouched!
    }
}
