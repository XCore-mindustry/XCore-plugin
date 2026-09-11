package org.xcore.plugin.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncTest {
    private StorageExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void thenMainUsesTheConfiguredDispatcher() throws Exception {
        executor = new StorageExecutor(1);
        CountDownLatch dispatched = new CountDownLatch(1);
        AtomicBoolean callbackCalled = new AtomicBoolean();
        AtomicReference<String> value = new AtomicReference<>();
        MainThreadDispatcher dispatcher = task -> {
            dispatched.countDown();
            task.run();
        };
        Async async = new Async(executor, dispatcher);

        async.supply(() -> "loaded")
                .thenMain(result -> {
                    value.set(result);
                    callbackCalled.set(true);
                })
                .get(1, TimeUnit.SECONDS);

        assertThat(dispatched.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(callbackCalled).isTrue();
        assertThat(value).hasValue("loaded");
    }

    @Test
    void thenMainBiResultReceivesStorageFailure() throws Exception {
        executor = new StorageExecutor(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Async async = new Async(executor, Runnable::run);

        async.supply(() -> {
                    throw new IllegalStateException("storage failed");
                })
                .thenMain((value, error) -> failure.set(error))
                .get(1, TimeUnit.SECONDS);

        assertThat(failure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("storage failed");
    }

    @Test
    void observeExecutesDirectlyWithoutMainThread() {
        executor = new StorageExecutor(1);
        Async async = new Async(executor, task -> {
            throw new AssertionError("Should not be dispatched to main thread");
        });

        AtomicReference<String> result = new AtomicReference<>();
        CompletableFuture<String> future = CompletableFuture.completedFuture("direct");
        async.<String>observe(future, (val, err) -> result.set(val));

        assertThat(result.get()).isEqualTo("direct");
    }

    @Test
    void onMainDispatchesToMainThread() throws Exception {
        executor = new StorageExecutor(1);
        CountDownLatch dispatched = new CountDownLatch(1);
        Async async = new Async(executor, task -> {
            dispatched.countDown();
            task.run();
        });

        AtomicReference<String> result = new AtomicReference<>();
        CompletableFuture<String> future = CompletableFuture.completedFuture("dispatched");
        async.<String>onMain(future, (val, err) -> result.set(val));

        assertThat(dispatched.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(result.get()).isEqualTo("dispatched");
    }
}
