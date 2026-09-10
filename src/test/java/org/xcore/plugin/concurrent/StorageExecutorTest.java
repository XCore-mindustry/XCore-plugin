package org.xcore.plugin.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageExecutorTest {
    private StorageExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void supplyRunsOffCallerThreadAndReturnsValue() throws Exception {
        executor = new StorageExecutor(2);
        String caller = Thread.currentThread().getName();

        var result = executor.supply(() -> Thread.currentThread().getName());

        assertThat(result.get(1, TimeUnit.SECONDS)).isNotEqualTo(caller);
        assertThat(executor.activeTasks()).isZero();
    }

    @Test
    void capacityRejectsWithoutCreatingUnboundedQueue() throws Exception {
        executor = new StorageExecutor(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        executor.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> executor.execute(() -> {}))
                .isInstanceOf(RejectedExecutionException.class);
        release.countDown();
    }

    @Test
    void supplyPropagatesTaskFailure() {
        executor = new StorageExecutor(1);

        var result = executor.supply(() -> {
            throw new IllegalStateException("database unavailable");
        });

        assertThat(result).failsWithin(1, TimeUnit.SECONDS)
                .withThrowableThat()
                .withMessageContaining("database unavailable");
    }
}
