package org.xcore.testkit.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Single-threaded queue driven explicitly by a test.
 *
 * <p>Run semantics follow Arc's snapshot-drain model: each turn executes the
 * tasks that were pending when it started; tasks posted by those tasks remain
 * queued for the next turn. Unlike Arc's TaskQueue, tasks of an interrupted
 * turn's snapshot are not restored after a callback exception — the scenario
 * is expected to fail loudly.
 *
 * <p>It is an {@link Executor}, so it can stand in for a main-thread dispatcher
 * ({@code Core.app::post}, an {@code Executor} parameter) without a hand-written
 * {@code ArrayDeque<Runnable>}.
 */
public final class DeterministicQueue implements Executor {
    /** Turns {@link #runUntilIdle()} allows before it decides the tasks re-post themselves forever. */
    public static final int DEFAULT_MAX_TURNS = 1_000;

    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

    public void post(Runnable task) {
        tasks.addLast(Objects.requireNonNull(task));
    }

    /** Same as {@link #post}: the task waits for a turn, it never runs inline. */
    @Override
    public void execute(Runnable task) {
        post(task);
    }

    public boolean runNext() {
        Runnable task = tasks.pollFirst();
        if (task == null) return false;
        task.run();
        return true;
    }

    public void runTurn() {
        List<Runnable> snapshot = new ArrayList<>(tasks);
        tasks.clear();
        for (Runnable task : snapshot) task.run();
    }

    /**
     * Runs turns until no task is pending.
     *
     * @return the number of turns that ran
     * @throws IllegalStateException when tasks are still pending after {@link #DEFAULT_MAX_TURNS} turns
     */
    public int runUntilIdle() {
        return runUntilIdle(DEFAULT_MAX_TURNS);
    }

    /**
     * Runs turns until no task is pending.
     *
     * @return the number of turns that ran
     * @throws IllegalStateException when tasks are still pending after {@code maxTurns} turns
     */
    public int runUntilIdle(int maxTurns) {
        for (int turn = 0; turn < maxTurns; turn++) {
            if (tasks.isEmpty()) return turn;
            runTurn();
        }
        if (tasks.isEmpty()) return maxTurns;
        throw new IllegalStateException(tasks.size() + " tasks still pending after " + maxTurns + " turns");
    }

    public int size() {
        return tasks.size();
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }
}
