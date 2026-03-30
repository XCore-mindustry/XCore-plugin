package org.xcore.plugin.service.network;

import arc.func.Cons;
import arc.util.Log;
import com.google.gson.Gson;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.xcore.plugin.event.SocketEvents.Request;
import org.xcore.plugin.event.SocketEvents.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

final class RedisRpcTracker {
    private static final long DEFAULT_CONTEXT_TTL_MILLIS = 120_000L;

    private final Gson gson;
    private final Map<Request<?>, RpcInboundContext> inboundRpcContexts = Collections.synchronizedMap(new IdentityHashMap<>());

    RedisRpcTracker(Gson gson) {
        this.gson = gson;
    }

    void registerInbound(Request<?> request, String correlationId, String replyTo, String rpcType, long createdAtMillis) {
        synchronized (inboundRpcContexts) {
            cleanupExpired(createdAtMillis, DEFAULT_CONTEXT_TTL_MILLIS);
            inboundRpcContexts.put(request, new RpcInboundContext(correlationId, replyTo, rpcType, createdAtMillis));
        }
    }

    RpcInboundContext take(Request<?> request) {
        synchronized (inboundRpcContexts) {
            return inboundRpcContexts.remove(request);
        }
    }

    boolean contains(Request<?> request) {
        synchronized (inboundRpcContexts) {
            return inboundRpcContexts.containsKey(request);
        }
    }

    int size() {
        synchronized (inboundRpcContexts) {
            return inboundRpcContexts.size();
        }
    }

    void cleanupExpired(long nowMillis, long ttlMillis) {
        List<Request<?>> toRemove = new ArrayList<>();
        for (Map.Entry<Request<?>, RpcInboundContext> entry : inboundRpcContexts.entrySet()) {
            if (nowMillis - entry.getValue().createdAtMillis() > ttlMillis) {
                toRemove.add(entry.getKey());
            }
        }
        for (Request<?> request : toRemove) {
            inboundRpcContexts.remove(request);
        }
    }

    <T extends Response> void awaitResponse(RedisClient client,
                                            String replyTo,
                                            String correlationId,
                                            Class<? extends Response> responseType,
                                            Cons<T> listener,
                                            Runnable timeout,
                                            long timeoutMs,
                                            CountDownLatch listenerReady,
                                            AtomicLong rpcTimeouts) {
        if (client == null) {
            listenerReady.countDown();
            timeout.run();
            return;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        try (StatefulRedisConnection<String, String> rpcConnection = client.connect()) {
            RedisCommands<String, String> rpcCommands = rpcConnection.sync();
            listenerReady.countDown();
            String lastId = "$";

            while (!Thread.currentThread().isInterrupted() && System.currentTimeMillis() < deadline) {
                List<StreamMessage<String, String>> messages = rpcCommands.xread(
                        XReadArgs.Builder.block(250).count(50),
                        XReadArgs.StreamOffset.from(replyTo, lastId)
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (StreamMessage<String, String> message : messages) {
                    lastId = message.getId();
                    String foundCorrelationId = message.getBody().get("correlation_id");
                    if (!correlationId.equals(foundCorrelationId)) {
                        continue;
                    }

                    String payloadJson = message.getBody().get("payload_json");
                    if (payloadJson == null || payloadJson.isBlank()) {
                        rpcTimeouts.incrementAndGet();
                        timeout.run();
                        return;
                    }

                    Response response = gson.fromJson(payloadJson, responseType);
                    if (responseType.isInstance(response)) {
                        listener.get((T) responseType.cast(response));
                    }
                    return;
                }
            }
        } catch (Exception e) {
            Log.warn("Redis RPC response await failed: @", e.getMessage());
        } finally {
            listenerReady.countDown();
        }

        rpcTimeouts.incrementAndGet();
        timeout.run();
    }

    record RpcInboundContext(String correlationId, String replyTo, String rpcType, long createdAtMillis) {
    }
}
