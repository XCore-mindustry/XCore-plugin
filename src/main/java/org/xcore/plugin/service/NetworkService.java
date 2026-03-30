package org.xcore.plugin.service;

import arc.func.Cons;
import arc.util.Log;
import org.xcore.plugin.event.SocketEvents.Request;
import org.xcore.plugin.event.SocketEvents.Response;
import org.xcore.plugin.service.network.RedisNetworkBackend.Subscription;
import org.xcore.plugin.service.network.RedisNetworkBackend.RequestSubscription;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import org.xcore.plugin.service.network.RedisNetworkBackend;

@Singleton
public class NetworkService {
    private final RedisNetworkBackend backend;

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
        try {
            backend.disconnect();
            backend.connect();
            return true;
        } catch (Exception e) {
            Log.err("Failed to reload Redis transport backend", e);
            return false;
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

    public <T extends Response> RequestSubscription<T> request(Request<T> request, Cons<T> listener, Runnable timeout) {
        return backend.request(request, listener, timeout);
    }

    public <T extends Response> void respond(Request<T> request, T response) {
        backend.respond(request, response);
    }

    public String backendName() {
        return backend.getClass().getSimpleName();
    }
}
