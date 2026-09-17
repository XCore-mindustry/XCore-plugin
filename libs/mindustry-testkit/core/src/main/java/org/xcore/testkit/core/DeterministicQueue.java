package org.xcore.testkit.core;

/** Single-threaded queue driven explicitly by a test. */
public final class DeterministicQueue {
    private final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();

    public void post(Runnable task) {
        tasks.addLast(java.util.Objects.requireNonNull(task));
    }

    /** Executes a snapshot; tasks posted by this turn remain pending. */
    public void runTurn() {
        var snapshot = new java.util.ArrayList<>(tasks);
        tasks.clear();
        for (Runnable task : snapshot) task.run();
    }

    public boolean runNext() {
        Runnable task = tasks.pollFirst();
        if (task == null) return false;
        task.run();
        return true;
    }
}
