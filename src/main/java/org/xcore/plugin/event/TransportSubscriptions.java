package org.xcore.plugin.event;

import arc.func.Cons;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.RedisNetworkBackend.Subscription;

import java.util.ArrayList;
import java.util.List;

/** A plugin-owned group of Redis subscriptions, including callbacks queued during shutdown. */
public final class TransportSubscriptions implements AutoCloseable {
    private final NetworkService network;
    private final List<Subscription<?>> subscriptions = new ArrayList<>();
    private volatile boolean closed;

    public TransportSubscriptions(NetworkService network) {
        this.network = network;
    }

    public synchronized <T> void subscribe(Class<T> type, Cons<T> listener) {
        if (closed || network == null) return;
        var subscription = network.subscribe(type, event -> {
            if (!closed) listener.get(event);
        });
        if (subscription != null) subscriptions.add(subscription);
    }

    /** Replace listeners after reconnect rather than accumulating registrations. */
    public synchronized void clear() {
        for (var subscription : subscriptions) {
            try {
                subscription.unsubscribe();
            } catch (RuntimeException error) {
                PLog.err("Error removing transport subscription", error);
            }
        }
        subscriptions.clear();
    }

    @Override
    public synchronized void close() {
        closed = true;
        clear();
    }
}
