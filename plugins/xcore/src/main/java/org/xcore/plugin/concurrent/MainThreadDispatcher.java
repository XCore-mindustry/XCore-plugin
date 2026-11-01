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
                // Guard the task itself, not just the call site. Arc's TaskQueue runs posted
                // work without a try/catch, so an unchecked exception from a marshalled
                // Redis listener or a UDP handler would propagate out of the main loop and
                // take the whole server down. Marshalled work is exactly the untrusted
                // surface where a surprise is most likely, so the loop itself is the
                // containment boundary.
                Core.app.post(() -> {
                    try {
                        task.run();
                    } catch (Throwable error) {
                        arc.util.Log.err("Uncaught exception in main-thread task", error);
                    }
                });
            }
        };
    }
}
