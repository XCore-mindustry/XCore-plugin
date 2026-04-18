package org.xcore.plugin.service.network;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class RedisSubscriber<T> extends RedisNetworkBackend.Subscription<T> {
    private final List<Thread> lifecycleThreads = new CopyOnWriteArrayList<>();
    private Consumer<List<Thread>> onStop;
    private final AtomicBoolean unsubscribed = new AtomicBoolean();

    RedisSubscriber(Consumer<List<Thread>> onStop) {
        this.onStop = onStop;
    }

    void onStop(Consumer<List<Thread>> onStop) {
        this.onStop = onStop;
    }

    void attach(Thread thread) {
        if (unsubscribed.get()) {
            thread.interrupt();
            return;
        }
        lifecycleThreads.add(thread);
    }

    @Override
    public void call(T object) {
    }

    @Override
    public boolean unsubscribe() {
        if (!unsubscribed.compareAndSet(false, true)) {
            return false;
        }

        for (Thread thread : lifecycleThreads) {
            thread.interrupt();
        }
        List<Thread> stoppedThreads = List.copyOf(lifecycleThreads);
        lifecycleThreads.clear();
        if (onStop != null) {
            onStop.accept(stoppedThreads);
        }
        return true;
    }
}
