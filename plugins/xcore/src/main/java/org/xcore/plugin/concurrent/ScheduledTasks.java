package org.xcore.plugin.concurrent;

import arc.util.Log;
import arc.util.Timer;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A bean-owned group of Arc timer tasks, cancelled together when its scope closes.
 *
 * <p>Arc posts a due task to the main loop and runs it there without a try/catch, so an
 * unchecked exception from a raw {@link Timer#schedule} callback takes the server down.
 * Tasks scheduled here are guarded instead: a failure is logged under the owner's name and
 * a repeating task keeps its schedule.
 */
public final class ScheduledTasks implements AutoCloseable {
    private final String owner;
    private final Set<Handle> handles = new HashSet<>();
    private volatile boolean closed;

    public ScheduledTasks(String owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public ScheduledTasks(Class<?> owner) {
        this(owner.getSimpleName());
    }

    /** Runs {@code action} once on the game thread after {@code delaySeconds}. */
    public Handle after(float delaySeconds, Runnable action) {
        return schedule(new Handle(action, false), delaySeconds, 0f);
    }

    /** Runs {@code action} on the game thread after {@code delaySeconds}, then every {@code intervalSeconds}. */
    public Handle every(float delaySeconds, float intervalSeconds, Runnable action) {
        return schedule(new Handle(action, true), delaySeconds, intervalSeconds);
    }

    /**
     * Scheduling on a closed group is a no-op that returns a finished handle: a late
     * request during shutdown is expected, and there is nothing useful to do with it.
     */
    private synchronized Handle schedule(Handle handle, float delaySeconds, float intervalSeconds) {
        if (closed) {
            handle.done = true;
            return handle;
        }
        handles.add(handle);
        Runnable guarded = () -> run(handle);
        try {
            handle.task = handle.repeating
                    ? Timer.schedule(guarded, delaySeconds, intervalSeconds)
                    : Timer.schedule(guarded, delaySeconds);
        } catch (RuntimeException | Error error) {
            forget(handle);
            throw error;
        }
        return handle;
    }

    private void run(Handle handle) {
        if (closed || handle.done) return;
        if (!handle.repeating) forget(handle);
        try {
            handle.action.run();
        } catch (Throwable error) {
            Log.err("[" + owner + "] Scheduled task failed", error);
        }
    }

    private synchronized void forget(Handle handle) {
        handle.done = true;
        handles.remove(handle);
    }

    /** How many tasks are still waiting to run. Visible for tests. */
    synchronized int pending() {
        return handles.size();
    }

    /** Safe off the game thread: {@link Timer.Task#cancel()} may be called at any time. */
    @Override
    public void close() {
        List<Handle> cancelled;
        synchronized (this) {
            if (closed) return;
            closed = true;
            cancelled = List.copyOf(handles);
            handles.clear();
        }
        cancelled.forEach(Handle::cancelTask);
    }

    /** One scheduled task of a {@link ScheduledTasks} group. */
    public final class Handle {
        private final Runnable action;
        private final boolean repeating;
        private volatile Timer.Task task;
        private volatile boolean done;

        private Handle(Runnable action, boolean repeating) {
            this.action = Objects.requireNonNull(action, "action");
            this.repeating = repeating;
        }

        /** Stops the task. A callback that is already posted to the main loop is skipped. */
        public void cancel() {
            forget(this);
            cancelTask();
        }

        /** Whether the task is still going to run. */
        public boolean isActive() {
            return !done && !closed;
        }

        /** Milliseconds until the next run, or 0 once the task is finished or cancelled. */
        public long remainingMillis() {
            var current = task;
            if (current == null || !isActive()) return 0L;
            // Arc stamps the execute time from System.nanoTime, not the wall clock.
            return Math.max(0L, current.getExecuteTimeMillis() - System.nanoTime() / 1_000_000L);
        }

        private void cancelTask() {
            done = true;
            var current = task;
            if (current != null) current.cancel();
        }
    }
}
