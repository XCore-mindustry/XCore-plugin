package org.xcore.plugin.service.network;

import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public final class RedisTransportHealth {
    public enum LifecycleState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public record Snapshot(
            boolean available,
            LifecycleState lifecycleState,
            int activeSubscriberThreads,
            long lastConnectAttemptAt,
            long lastConnectedAt,
            long lastDisconnectedAt
    ) {
    }

    private final AtomicReference<LifecycleState> lifecycleState = new AtomicReference<>(LifecycleState.DISCONNECTED);
    private final AtomicInteger activeSubscriberThreads = new AtomicInteger();
    private final AtomicLong lastConnectAttemptAt = new AtomicLong();
    private final AtomicLong lastConnectedAt = new AtomicLong();
    private final AtomicLong lastDisconnectedAt = new AtomicLong();

    public void markConnecting() {
        lifecycleState.set(LifecycleState.CONNECTING);
        lastConnectAttemptAt.set(System.currentTimeMillis());
    }

    public void markConnected() {
        lifecycleState.set(LifecycleState.CONNECTED);
        lastConnectedAt.set(System.currentTimeMillis());
    }

    public void markUnavailable() {
        lifecycleState.set(LifecycleState.DISCONNECTED);
    }

    public void markDisconnected() {
        lifecycleState.set(LifecycleState.DISCONNECTED);
        lastDisconnectedAt.set(System.currentTimeMillis());
    }

    public void setActiveSubscriberThreads(int activeThreads) {
        activeSubscriberThreads.set(Math.max(activeThreads, 0));
    }

    public Snapshot snapshot() {
        LifecycleState currentState = lifecycleState.get();
        return new Snapshot(
                currentState == LifecycleState.CONNECTED,
                currentState,
                activeSubscriberThreads.get(),
                lastConnectAttemptAt.get(),
                lastConnectedAt.get(),
                lastDisconnectedAt.get()
        );
    }
}
