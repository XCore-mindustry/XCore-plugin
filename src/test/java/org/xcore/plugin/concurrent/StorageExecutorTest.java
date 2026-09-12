package org.xcore.plugin.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.metrics.DefaultMetricsService;
import org.xcore.plugin.metrics.LocalMetricRegistry;
import org.xcore.plugin.metrics.XcoreMetrics;
import org.xcore.protocol.generated.shared.MetricSampleV1;
import org.xcore.protocol.generated.shared.MetricSampleV1Type;

import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    @Test
    void storageMetricsTrackActiveRejectedAndDuration() throws Exception {
        LocalMetricRegistry registry = new LocalMetricRegistry();
        executor = new StorageExecutor(1, new DefaultMetricsService(registry, enabledConfig()));

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        });

        // The single permit is held; the active gauge must reflect that.
        assertThat(sampleByName(registry.snapshot(), XcoreMetrics.STORAGE_TASKS_ACTIVE.name()).value()).isEqualTo(1d);

        // A second task cannot start and must be rejected explicitly.
        assertThatThrownBy(() -> executor.execute(() -> {
        })).isInstanceOf(RejectedExecutionException.class);

        release.countDown();
        assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
        awaitGauge(registry, XcoreMetrics.STORAGE_TASKS_ACTIVE.name(), 0d);

        List<MetricSampleV1> samples = registry.snapshot();
        assertThat(sampleByName(samples, XcoreMetrics.STORAGE_TASKS_ACTIVE.name()).value()).isEqualTo(0d);
        MetricSampleV1 rejected = sampleByName(samples, XcoreMetrics.STORAGE_TASKS_REJECTED_TOTAL.name());
        assertThat(rejected.value()).isEqualTo(1d);

        MetricSampleV1 duration = sampleByName(samples, XcoreMetrics.STORAGE_TASK_DURATION_SECONDS.name());
        assertThat(duration.type()).isEqualTo(MetricSampleV1Type.HISTOGRAM);
        assertThat(duration.count()).isEqualTo(1L);
        assertThat(duration.sum()).isGreaterThanOrEqualTo(0d);
    }

    @Test
    void slowStorageTasksDoNotBlockCallerOrSaturateUnbounded() throws Exception {
        // Chaos guard: a storage task that stalls for seconds must never block the
        // calling (main) thread and must not grow an unbounded waiting queue.
        executor = new StorageExecutor(2);

        long callerStart = System.nanoTime();
        CompletableFuture<String> slow = executor.supply(() -> {
            Thread.sleep(1_500);
            return "stored";
        });
        long callerBlockedMillis = (System.nanoTime() - callerStart) / 1_000_000;

        assertThat(callerBlockedMillis).isLessThan(1_000);
        assertThat(slow.get(3, TimeUnit.SECONDS)).isEqualTo("stored");
        assertThat(executor.activeTasks()).isZero();
    }

    private static void awaitGauge(LocalMetricRegistry registry, String name, double expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            if (sampleByName(registry.snapshot(), name).value() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(sampleByName(registry.snapshot(), name).value()).isEqualTo(expected);
    }

    private static MetricSampleV1 sampleByName(List<MetricSampleV1> samples, String name) {
        return samples.stream()
                .filter(sample -> sample.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static TomlXcoreConfig enabledConfig() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.telemetry.enabled = true;
        return config;
    }
}
