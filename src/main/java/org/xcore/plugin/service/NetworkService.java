package org.xcore.plugin.service;

import arc.func.Cons;
import org.xcore.plugin.common.PLog;
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
    private volatile boolean deferredNoticeLogged;

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
            deferredNoticeLogged = false;
        } catch (Exception e) {
            PLog.err("Failed to connect transport backend", e);
        }
    }

    public synchronized boolean reloadBackend() {
        backend.disconnect();
        try {
            backend.connect();
            deferredNoticeLogged = false;
            replayReconnectHooks();
            return true;
        } catch (Exception e) {
            PLog.err("Failed to reload Redis transport backend", e);
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
        if (!backend.ensureConnected()) {
            logDeferred("subscription", type.getSimpleName());
            return new Subscription<>() {
                @Override
                public void call(T object) {
                    // no-op: backend unavailable, listener deferred until reconnect
                }

                @Override
                public boolean unsubscribe() {
                    return false;
                }
            };
        }
        return backend.subscribe(type, listener);
    }

    public <REQ, RES> RequestSubscription<RES> request(REQ request, Cons<RES> listener, Runnable timeout) {
        if (!backend.ensureConnected()) {
            logDeferred("request listener", request.getClass().getSimpleName());
            return new RequestSubscription<>() {
                @Override
                public void cancel() {
                    // no-op: backend unavailable, listener deferred until reconnect
                }
            };
        }
        return backend.request(request, listener, timeout);
    }

    /**
     * Logs the first degraded subscription loudly (operators must know which
     * systems stop functioning without Redis) and stays quiet afterwards —
     * every handler would otherwise repeat the same wall of warnings at boot.
     * The flag resets on any successful (re)connect.
     */
    private void logDeferred(String kind, String typeName) {
        if (deferredNoticeLogged) {
            PLog.debugTag("Transport", "Redis still unavailable: @ for '@' stays deferred", kind, typeName);
            return;
        }
        deferredNoticeLogged = true;
        PLog.errTag(
                "Transport",
                "Redis transport unavailable: @ for '@' is skipped. "
                        + "Cross-server chat, Discord moderation/badge sync and map sync are DISABLED "
                        + "until the backend is restored. Start Redis and run 'transport-reload'.",
                kind,
                typeName);
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
