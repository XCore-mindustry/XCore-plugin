package org.xcore.plugin.concurrent;

import arc.Core;

import java.util.Objects;

/** Keeps Mindustry thread-affinity handling in one place. */
@FunctionalInterface
public interface MainThreadDispatcher {
    void execute(Runnable task);

    static MainThreadDispatcher mindustry() {
        return task -> {
            Objects.requireNonNull(task, "task");
            if (Core.app == null) {
                // Allows storage utilities to be unit-tested without booting Arc.
                task.run();
            } else {
                Core.app.post(task);
            }
        };
    }
}
