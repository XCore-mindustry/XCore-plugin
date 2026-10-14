package org.xcore.plugin.cases;

import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.testkit.core.DeterministicQueue;

import java.util.ArrayDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Deterministic scheduling with real worker affinity, no sleeps. */
public final class CasesTestLoop {
    public final DeterministicQueue main = new DeterministicQueue();
    private final ArrayDeque<Runnable> workers = new ArrayDeque<>();
    public final StorageExecutor storage = mock(StorageExecutor.class);
    public final Async async;

    public CasesTestLoop() {
        when(storage.supply(any())).thenAnswer(invocation -> {
            Callable<Object> task = invocation.getArgument(0);
            var future = new CompletableFuture<Object>();
            workers.add(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable error) {
                    future.completeExceptionally(error);
                }
            });
            return future;
        });
        doAnswer(invocation -> {
            workers.add(invocation.getArgument(0));
            return null;
        }).when(storage).execute(any());
        async = new Async(storage, main::post);
    }

    public void worker() throws InterruptedException {
        Runnable work = workers.removeFirst();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread thread = new Thread(() -> {
            try {
                work.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "cases-test-worker");
        thread.start();
        thread.join(5000);
        if (thread.isAlive()) throw new AssertionError("Worker did not finish");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    public void drain() throws InterruptedException {
        for (int turn = 0; turn < 20; turn++) {
            while (!workers.isEmpty()) worker();
            main.runTurn();
            if (workers.isEmpty()) return;
        }
        throw new AssertionError("Cases loop did not settle");
    }

    public int workers() {
        return workers.size();
    }
}
