package org.xcore.plugin.concurrent;

import arc.util.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.ManualTimer;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mockStatic;

@WithHeadlessWorld
class ScheduledTasksTest {
    private HeadlessWorld world;
    private ManualTimer timer;

    @BeforeEach
    void setUp(HeadlessWorld world) {
        this.world = world;
        timer = world.timer();
    }

    @Test
    void failingRepeatingTaskDoesNotEscapeIntoTheMainLoopAndKeepsRunning() {
        var runs = new AtomicInteger();
        var tasks = new ScheduledTasks("test");
        var handle = tasks.every(0f, 1f, () -> {
            if (runs.incrementAndGet() == 1) throw new IllegalStateException("boom");
        });

        assertThatCode(timer::runDue).doesNotThrowAnyException();
        timer.advance(1f);

        assertThat(runs).hasValue(2);
        assertThat(handle.isActive()).isTrue();
        assertThat(timer.pending()).isEqualTo(1);
    }

    @Test
    void closingCancelsEveryTaskAndDisablesAnAlreadyQueuedRun() {
        // Arc posts a due task to the main loop; hold the posts so one is in flight at close.
        var mainLoop = world.mainLoop();
        var runs = new AtomicInteger();
        var tasks = new ScheduledTasks("test");
        tasks.every(0f, 1f, runs::incrementAndGet);
        tasks.after(5f, runs::incrementAndGet);
        timer.runDue();
        assertThat(mainLoop.size()).isEqualTo(1);

        tasks.close();
        mainLoop.runUntilIdle();
        timer.advance(10f);
        mainLoop.runUntilIdle();

        assertThat(runs).hasValue(0);
        assertThat(timer.pending()).isZero();
        assertThat(tasks.pending()).isZero();
    }

    @Test
    void oneShotTaskLeavesTheGroupAfterItRuns() {
        var runs = new AtomicInteger();
        var tasks = new ScheduledTasks("test");
        var handle = tasks.after(5f, runs::incrementAndGet);

        timer.advance(5f);

        assertThat(runs).hasValue(1);
        assertThat(handle.isActive()).isFalse();
        assertThat(tasks.pending()).isZero();
    }

    @Test
    void cancellingOneTaskLeavesTheOthersScheduled() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var tasks = new ScheduledTasks("test");
        var cancelled = tasks.after(5f, first::incrementAndGet);
        tasks.after(5f, second::incrementAndGet);

        cancelled.cancel();
        assertThat(timer.pending()).isEqualTo(1);
        timer.advance(5f);

        assertThat(first).hasValue(0);
        assertThat(second).hasValue(1);
    }

    @Test
    void schedulingOnAClosedGroupDoesNothing() {
        var tasks = new ScheduledTasks("test");
        tasks.close();

        var handle = tasks.after(1f, () -> { });

        assertThat(handle.isActive()).isFalse();
        assertThat(timer.pending()).isZero();
    }

    @Test
    void remainingTimeIsMeasuredOnArcsMonotonicClock() {
        var tasks = new ScheduledTasks("test");
        var handle = tasks.after(5f, () -> { });

        assertThat(handle.remainingMillis()).isBetween(4_000L, 5_000L);
        timer.advance(2f);
        assertThat(handle.remainingMillis()).isBetween(2_000L, 3_000L);

        handle.cancel();
        assertThat(handle.remainingMillis()).isZero();
    }

    @Test
    void schedulingFailureDoesNotLeaveAPendingHandle() {
        try (var failing = mockStatic(Timer.class)) {
            failing.when(() -> Timer.schedule(any(Runnable.class), anyFloat()))
                    .thenThrow(new IllegalStateException("timer unavailable"));
            var tasks = new ScheduledTasks("test");

            assertThatThrownBy(() -> tasks.after(1f, () -> { }))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(tasks.pending()).isZero();
        }
    }
}
