package org.xcore.plugin.service.map;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.StorageExecutor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapContentHashServiceTest {
    @Test
    void defersReadingUntilWorkerRunsAndClosesStream() {
        var tasks = new ArrayDeque<Runnable>();
        var executor = mock(StorageExecutor.class, CALLS_REAL_METHODS);
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(executor).execute(any());
        var opened = new AtomicBoolean();
        var closed = new AtomicBoolean();
        var service = new MapContentHashService(executor);
        var result = service.hashAsync(() -> {
            opened.set(true);
            return new ByteArrayInputStream(new byte[]{97, 98, 99}) {
                @Override public void close() { closed.set(true); }
            };
        }).toCompletableFuture();

        assertFalse(opened.get());
        assertFalse(result.isDone());
        tasks.remove().run();
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.join().asHex());
        assertTrue(closed.get());
    }

    @Test
    void reportsIoFailureThroughStage() {
        var executor = mock(StorageExecutor.class, CALLS_REAL_METHODS);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(executor).execute(any());
        var failure = new IOException("unreadable");
        var result = new MapContentHashService(executor).hashAsync(() -> { throw failure; });
        assertSame(failure, assertThrows(CompletionException.class, () -> result.toCompletableFuture().join()).getCause());
    }

    @Test
    void reportsCapacityRejectionWithoutReading() {
        var executor = mock(StorageExecutor.class, CALLS_REAL_METHODS);
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        var result = new MapContentHashService(executor).hashAsync(() -> { fail("must not read"); return null; });
        assertInstanceOf(RejectedExecutionException.class,
                assertThrows(CompletionException.class, () -> result.toCompletableFuture().join()).getCause());
    }
}
