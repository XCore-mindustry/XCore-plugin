package org.xcore.testkit.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Single-threaded queue driven explicitly by a test.
 *
 * <p>Run semantics follow Arc's snapshot-drain model: each turn executes the
 * tasks that were pending when it started; tasks posted by those tasks remain
 * queued for the next turn. Unlike Arc's TaskQueue, tasks of an interrupted
 * turn's snapshot are not restored after a callback exception — the scenario
 * is expected to fail loudly.
 */
public final class DeterministicQueue {
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

    public void post(Runnable task) {
        tasks.addLast(Objects.requireNonNull(task));
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
}
