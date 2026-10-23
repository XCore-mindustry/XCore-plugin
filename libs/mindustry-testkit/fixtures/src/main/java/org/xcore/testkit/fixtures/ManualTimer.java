package org.xcore.testkit.fixtures;

import arc.util.Timer;

import arc.Application;
import arc.struct.Seq;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Arc's {@link Timer} on virtual time: while installed, {@code Timer.schedule(...)} queues tasks
 * here, and they run only when a test calls {@link #advance}. No sleeps, no background thread.
 *
 * <pre>{@code
 * try (ManualTimer timer = ManualTimer.install()) {
 *     service.startCountdown();      // Timer.schedule(this::start, 10f)
 *     timer.advance(9.9f);           // nothing yet
 *     timer.advance(0.1f);           // the countdown task runs now
 * }
 * }</pre>
 *
 * <p>A task runs through the {@code Core.app} it captured when it was created, exactly as Arc runs
 * it: inline under a {@link HeadlessWorld}'s {@code MockApplication}, or queued when a test routes
 * {@code Core.app.post} into a deterministic queue. Repeating tasks run once per interval that
 * {@link #advance} crosses, each at its own virtual instant. Tasks due at the same instant run in
 * the order Arc's unordered task list gives, as on a live server: not necessarily the order they
 * were scheduled in.
 *
 * <p>{@link HeadlessWorld#timer()} installs one for the world's lifetime.
 */
public final class ManualTimer extends Timer implements AutoCloseable {
    /** Firings one {@link #advance} allows before it decides a task repeats with no interval. */
    public static final int MAX_FIRINGS_PER_ADVANCE = 100_000;

    private static final Field THREAD_LOCK = field(Timer.class, "threadLock");
    private static final Field THREAD = field(Timer.class, "thread");
    private static final Field THREAD_INSTANCE = field(nested("TimerThread"), "instance");
    private static final Field TASKS = field(Timer.class, "tasks");
    private static final Field EXECUTE_TIME = field(Task.class, "executeTimeMillis");
    private static final Field INTERVAL = field(Task.class, "intervalMillis");
    private static final Field REPEAT_COUNT = field(Task.class, "repeatCount");
    private static final Field TASK_TIMER = field(Task.class, "timer");
    private static final Field TASK_APP = field(Task.class, "app");

    private final Timer previous;
    private long nowMillis;
    private boolean closed;

    private ManualTimer(Timer previous) {
        // Timer() registers with Arc's background thread; take it off so only advance() runs tasks.
        stop();
        this.previous = previous;
    }

    /**
     * Makes {@code Timer.instance()} — and so every static {@code Timer.schedule} — this timer
     * until {@link #close()}, which puts back the timer that was there.
     */
    public static ManualTimer install() {
        Timer.instance(); // starts Arc's timer thread, which holds the instance
        try {
            synchronized (THREAD_LOCK.get(null)) {
                Object thread = THREAD.get(null);
                Timer previous = (Timer) THREAD_INSTANCE.get(thread);
                ManualTimer timer = new ManualTimer(previous);
                THREAD_INSTANCE.set(thread, timer);
                return timer;
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not replace Arc's Timer instance", e);
        }
    }

    @Override
    public Task scheduleTask(Task task, float delaySeconds, float intervalSeconds, int repeatCount) {
        checkNotClosed();
        synchronized (this) {
            super.scheduleTask(task, delaySeconds, intervalSeconds, repeatCount);
            // Arc stamps the task with System.nanoTime(); restamp it on the virtual clock.
            setExecuteTime(task, nowMillis + (long) (delaySeconds * 1000));
        }
        return task;
    }

    /** Moves virtual time forward, running every task that comes due on the way, in time order. */
    public void advance(float seconds) {
        advanceMillis((long) (seconds * 1000));
    }

    /** {@link #advance} in milliseconds. */
    public void advanceMillis(long millis) {
        checkNotClosed();
        if (millis < 0) throw new IllegalArgumentException("Time cannot go back: " + millis + " ms");
        long target = nowMillis + millis;
        for (int firings = 0; ; firings++) {
            long next = nextExecuteMillis();
            if (next < 0 || next > target) break;
            if (firings == MAX_FIRINGS_PER_ADVANCE) {
                throw new IllegalStateException("A task keeps coming due at " + next
                        + " ms; does it repeat with a zero interval?");
            }
            nowMillis = next; // tasks scheduled by a running task start from its instant
            fireDue();
        }
        nowMillis = target;
    }

    /**
     * Arc's {@code Timer.update} bookkeeping for one instant, then the posts. Arc posts while it
     * walks the task list, which is safe only because its posts run later on the main thread;
     * here a post may run inline and cancel or schedule tasks, so the walk finishes first.
     */
    private void fireDue() {
        List<Task> due = new ArrayList<>();
        try {
            synchronized (this) {
                Seq<Task> tasks = tasks();
                for (int i = 0; i < tasks.size; i++) {
                    Task task = tasks.get(i);
                    synchronized (task) {
                        if (EXECUTE_TIME.getLong(task) > nowMillis) continue;
                        int repeat = REPEAT_COUNT.getInt(task);
                        if (repeat == 0) {
                            TASK_TIMER.set(task, null);
                            tasks.remove(i--);
                        } else {
                            EXECUTE_TIME.setLong(task, nowMillis + INTERVAL.getLong(task));
                            if (repeat > 0) REPEAT_COUNT.setInt(task, repeat - 1);
                        }
                    }
                    due.add(task);
                }
            }
            for (Task task : due) ((Application) TASK_APP.get(task)).post(task);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Runs the tasks already due, such as {@code Timer.schedule(task, 0f)}, without moving time. */
    public void runDue() {
        advanceMillis(0);
    }

    /** Virtual time since {@link #install()}, in seconds. */
    public float now() {
        return nowMillis / 1000f;
    }

    /** Seconds until the next task comes due, or {@code -1} when nothing is scheduled. */
    public float nextIn() {
        long next = nextExecuteMillis();
        return next < 0 ? -1f : Math.max(0, next - nowMillis) / 1000f;
    }

    /** Tasks waiting to run, repeating ones included. */
    public synchronized int pending() {
        return tasks().size;
    }

    /** Cancels every pending task and gives {@code Timer.instance()} back. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        clear();
        try {
            synchronized (THREAD_LOCK.get(null)) {
                Object thread = THREAD.get(null);
                if (THREAD_INSTANCE.get(thread) == this) THREAD_INSTANCE.set(thread, previous);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not restore Arc's Timer instance", e);
        }
    }

    private synchronized long nextExecuteMillis() {
        Seq<Task> tasks = tasks();
        long next = -1;
        for (int i = 0; i < tasks.size; i++) {
            long at = executeTime(tasks.get(i));
            if (next < 0 || at < next) next = at;
        }
        return next;
    }

    @SuppressWarnings("unchecked")
    private Seq<Task> tasks() {
        try {
            return (Seq<Task>) TASKS.get(this);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long executeTime(Task task) {
        try {
            return EXECUTE_TIME.getLong(task);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setExecuteTime(Task task, long millis) {
        try {
            synchronized (task) {
                EXECUTE_TIME.setLong(task, millis);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void checkNotClosed() {
        if (closed) throw new IllegalStateException("ManualTimer has already been closed.");
    }

    private static Class<?> nested(String name) {
        for (Class<?> type : Timer.class.getDeclaredClasses()) {
            if (type.getSimpleName().equals(name)) return type;
        }
        throw new ExceptionInInitializerError("Arc Timer has no nested class " + name);
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(
                    new IllegalStateException(owner.getName() + " has no field '" + name + "'", e));
        }
    }
}
