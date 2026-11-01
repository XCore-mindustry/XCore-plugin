package org.xcore.plugin.event;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import org.xcore.plugin.concurrent.GameThread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** A bean-owned group of Arc listeners, disabled immediately when its scope closes. */
public final class EventSubscriptions implements AutoCloseable {
    private final List<Runnable> removals = new ArrayList<>();
    private volatile boolean closed;

    public synchronized <T> void on(Class<T> type, Cons<T> listener) {
        GameThread.require("EventSubscriptions.on");
        if (closed) throw new IllegalStateException("Event subscriptions are closed");
        Cons<T> guarded = event -> {
            if (!closed) listener.get(event);
        };
        Events.on(type, guarded);
        removals.add(() -> Events.remove(type, guarded));
    }

    /** Arc has no remove API for enum triggers; release the callback on scope close. */
    public synchronized void run(Object trigger, Runnable action) {
        GameThread.require("EventSubscriptions.run");
        if (closed) throw new IllegalStateException("Event subscriptions are closed");
        var callback = new AtomicReference<>(action);
        Events.run(trigger, () -> {
            var current = callback.get();
            if (!closed && current != null) current.run();
        });
        removals.add(() -> callback.set(null));
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        var cleanup = List.copyOf(removals);
        removals.clear();
        Runnable remove = () -> cleanup.forEach(Runnable::run);
        // JVM shutdown can close a scope off the game thread. Disable callbacks now,
        // but leave mutations of Arc's event bus to the game thread if it is still alive.
        if (GameThread.isGameThread()) remove.run();
        else Core.app.post(remove);
    }
}
