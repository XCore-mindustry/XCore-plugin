package org.xcore.plugin.service.network;

import io.lettuce.core.api.StatefulRedisConnection;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class RedisRequestHandle<T> extends RedisNetworkBackend.RequestSubscription<T> {
    private Runnable onFinish;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();
    private final AtomicReference<StatefulRedisConnection<String, String>> connection = new AtomicReference<>();

    RedisRequestHandle(Runnable onFinish) {
        this.onFinish = onFinish;
    }

    void onFinish(Runnable onFinish) {
        this.onFinish = onFinish;
    }

    void bindWorker(Thread thread) {
        if (!workerThread.compareAndSet(null, thread) && workerThread.get() != thread) {
            throw new IllegalStateException("Redis request handle worker already bound");
        }
        if (cancelled.get()) {
            thread.interrupt();
        }
    }

    void bindConnection(StatefulRedisConnection<String, String> redisConnection) {
        StatefulRedisConnection<String, String> previous = connection.getAndSet(redisConnection);
        if (previous != null && previous != redisConnection) {
            previous.close();
        }
        if (cancelled.get()) {
            closeConnection(redisConnection);
        }
    }

    void clearConnection(StatefulRedisConnection<String, String> redisConnection) {
        if (redisConnection == null) {
            connection.set(null);
            return;
        }
        connection.compareAndSet(redisConnection, null);
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    boolean markFinished() {
        if (!finished.compareAndSet(false, true)) {
            return false;
        }
        if (onFinish != null) {
            onFinish.run();
        }
        return true;
    }

    @Override
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }

        Thread thread = workerThread.get();
        if (thread != null) {
            thread.interrupt();
        }

        StatefulRedisConnection<String, String> redisConnection = connection.getAndSet(null);
        if (redisConnection != null) {
            closeConnection(redisConnection);
        }

        markFinished();
    }

    private void closeConnection(StatefulRedisConnection<String, String> redisConnection) {
        try {
            redisConnection.close();
        } catch (Exception ignored) {
        }
    }
}
