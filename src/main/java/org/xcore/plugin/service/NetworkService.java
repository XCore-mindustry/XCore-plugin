package org.xcore.plugin.service;

import arc.func.Cons;
import arc.util.Log;
import org.xcore.plugin.service.network.RedisNetworkBackend.Subscription;
import org.xcore.plugin.service.network.RedisNetworkBackend.RequestSubscription;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import org.xcore.plugin.service.network.RedisNetworkBackend;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class NetworkService {
    private final RedisNetworkBackend backend;
    private final List<Runnable> reconnectHooks = new CopyOnWriteArrayList<>();

    public NetworkService(RedisNetworkBackend backend) {
        this.backend = backend;
    }

    @PostConstruct
    public void init() {
        safeConnect();
    }

    public void safeConnect() {
        try {
            backend.connect();
        } catch (Exception e) {
            Log.err("Exception occurred while connecting transport backend", e);
        }
    }

    public synchronized boolean reloadBackend() {
        backend.disconnect();
        try {
            backend.connect();
            replayReconnectHooks();
            return true;
        } catch (Exception e) {
            Log.err("Failed to reload Redis transport backend", e);
            backend.disconnect();
            return false;
        }
    }

    public void registerReconnectHook(Runnable hook) {
        if (!reconnectHooks.contains(hook)) {
            reconnectHooks.add(hook);
        }
    }

    @PreDestroy
    public void disconnect() {
        backend.disconnect();
    }

    public void post(Object event) {
        backend.send(event);
    }

    public <T> Subscription<T> subscribe(Class<T> type, Cons<T> listener) {
        return backend.subscribe(type, listener);
    }

    public <REQ, RES> RequestSubscription<RES> request(REQ request, Cons<RES> listener, Runnable timeout) {
        return backend.request(request, listener, timeout);
    }

    public void respond(Object request, Object response) {
        backend.respond(request, response);
    }

    public String backendName() {
        return backend.getClass().getSimpleName();
    }

    private void replayReconnectHooks() {
        for (Runnable hook : reconnectHooks) {
            hook.run();
        }
    }
}
