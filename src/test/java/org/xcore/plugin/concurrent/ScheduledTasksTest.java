package org.xcore.plugin.concurrent;

import arc.util.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

class ScheduledTasksTest {
    private MockedStatic<Timer> timer;
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<Timer.Task> arcTasks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        timer = mockStatic(Timer.class);
        timer.when(() -> Timer.schedule(any(Runnable.class), anyFloat())).thenAnswer(invocation -> capture(invocation.getArgument(0)));
        timer.when(() -> Timer.schedule(any(Runnable.class), anyFloat(), anyFloat())).thenAnswer(invocation -> capture(invocation.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        timer.close();
    }

    private Timer.Task capture(Runnable runnable) {
        var task = mock(Timer.Task.class);
        scheduled.add(runnable);
        arcTasks.add(task);
        return task;
    }

    @Test
    void failingRepeatingTaskDoesNotEscapeIntoTheMainLoopAndKeepsRunning() {
        var runs = new AtomicInteger();
        var tasks = new ScheduledTasks("test");
        var handle = tasks.every(0f, 1f, () -> {
            if (runs.incrementAndGet() == 1) throw new IllegalStateException("boom");
        });

        assertThatCode(() -> scheduled.getFirst().run()).doesNotThrowAnyException();
        scheduled.getFirst().run();

        assertThat(runs).hasValue(2);
        assertThat(handle.isActive()).isTrue();
        verify(arcTasks.getFirst(), never()).cancel();
    }

    @Test
    void closingCancelsEveryTaskAndDisablesAnAlreadyQueuedRun() {
        var runs = new AtomicInteger();
        var tasks = new ScheduledTasks("test");
        tasks.every(0f, 1f, runs::incrementAndGet);
        tasks.after(5f, runs::incrementAndGet);

        tasks.close();
        scheduled.forEach(Runnable::run);

        assertThat(runs).hasValue(0);
        arcTasks.forEach(task -> verify(task).cancel());
        assertThat(tasks.pending()).isZero();
    }

    @Test
    void oneShotTaskLeavesTheGroupAfterItRuns() {
        var runs = new AtomicInteger();
        var tasks = new ScheduledTasks("test");
        var handle = tasks.after(5f, runs::incrementAndGet);

        scheduled.getFirst().run();

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
        scheduled.forEach(Runnable::run);

        assertThat(first).hasValue(0);
        assertThat(second).hasValue(1);
        verify(arcTasks.get(0)).cancel();
        verify(arcTasks.get(1), never()).cancel();
    }

    @Test
    void schedulingOnAClosedGroupDoesNothing() {
        var tasks = new ScheduledTasks("test");
        tasks.close();

        var handle = tasks.after(1f, () -> { });

        assertThat(handle.isActive()).isFalse();
        assertThat(scheduled).isEmpty();
    }

    @Test
    void remainingTimeIsMeasuredOnArcsMonotonicClock() {
        var tasks = new ScheduledTasks("test");
        var handle = tasks.after(5f, () -> { });
        when(arcTasks.getFirst().getExecuteTimeMillis()).thenReturn(System.nanoTime() / 1_000_000L + 5_000L);

        assertThat(handle.remainingMillis()).isBetween(4_000L, 5_000L);

        handle.cancel();
        assertThat(handle.remainingMillis()).isZero();
    }

    @Test
    void schedulingFailureDoesNotLeaveAPendingHandle() {
        timer.when(() -> Timer.schedule(any(Runnable.class), anyFloat()))
                .thenThrow(new IllegalStateException("timer unavailable"));
        var tasks = new ScheduledTasks("test");

        assertThatThrownBy(() -> tasks.after(1f, () -> { }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(tasks.pending()).isZero();
    }
}
