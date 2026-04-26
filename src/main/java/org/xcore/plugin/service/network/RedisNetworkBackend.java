package org.xcore.plugin.service.network;

import arc.func.Cons;
import arc.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.lettuce.core.Consumer;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.SetArgs;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.event.TransportEvents.Request;
import org.xcore.plugin.event.TransportEvents.Response;
import org.xcore.plugin.model.BanData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;


@Singleton
public final class RedisNetworkBackend {
    public abstract static class Subscription<T> {
        public abstract void call(T object);
        public abstract boolean unsubscribe();
    }

    public abstract static class RequestSubscription<T> {
        public abstract void cancel();
    }
    private final Config config;
    private final Gson gson;
    private final RedisStreamRouter router;
    private final RedisEnvelopeFactory envelopeFactory;
    private final RedisStreamSupport streamSupport;
    private final RedisRpcTracker rpcTracker;
    private final RedisConnectionManager connectionManager;
    private final RedisTransportHealth transportHealth;
    private boolean publishWarningLogged;
    private final List<Thread> subscriberThreads = new CopyOnWriteArrayList<>();
    private final List<RedisRequestHandle<?>> requestHandles = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> deliveryFailures = new ConcurrentHashMap<>();
    private final AtomicLong publishedEvents = new AtomicLong();
    private final AtomicLong publishFailures = new AtomicLong();
    private final AtomicLong consumedEvents = new AtomicLong();
    private final AtomicLong consumeFailures = new AtomicLong();
    private final AtomicLong rpcRequests = new AtomicLong();
    private final AtomicLong rpcResponses = new AtomicLong();
    private final AtomicLong rpcTimeouts = new AtomicLong();
    private final AtomicLong dlqRouted = new AtomicLong();
    private final AtomicLong reclaimedMessages = new AtomicLong();

    @Inject
    public RedisNetworkBackend(Config config,
                               @Named("redis") Gson gson,
                               RedisStreamRouter router,
                               RedisStreamSupport streamSupport,
                               RedisRpcTracker rpcTracker,
                               RedisConnectionManager connectionManager,
                               RedisTransportHealth transportHealth) {
        this.config = config;
        this.gson = gson;
        this.router = router;
        this.streamSupport = streamSupport;
        this.rpcTracker = rpcTracker;
        this.connectionManager = connectionManager;
        this.transportHealth = transportHealth;
        this.envelopeFactory = new RedisEnvelopeFactory(config, gson);
    }

    public RedisNetworkBackend(Config config) {
        this(config, createRedisGson(), new RedisStreamRouter(), createStandaloneDependencies(config));
    }

    private RedisNetworkBackend(Config config,
                                Gson gson,
                                RedisStreamRouter router,
                                StandaloneDependencies dependencies) {
        this(config,
                gson,
                router,
                new RedisStreamSupport(config),
                new RedisRpcTracker(gson),
                dependencies.connectionManager(),
                dependencies.transportHealth());
    }

    private static StandaloneDependencies createStandaloneDependencies(Config config) {
        RedisTransportHealth transportHealth = new RedisTransportHealth();
        return new StandaloneDependencies(new RedisConnectionManager(config, transportHealth), transportHealth);
    }

    private record StandaloneDependencies(
            RedisConnectionManager connectionManager,
            RedisTransportHealth transportHealth
    ) {
    }

    public static Gson createRedisGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>)
                        (src, typeOfSrc, context) -> src == null ? null : new com.google.gson.JsonPrimitive(src.toString()))
                .registerTypeAdapter(Instant.class, (com.google.gson.JsonDeserializer<Instant>)
                        (json, typeOfT, context) -> json == null || json.isJsonNull()
                                ? null
                                : Instant.parse(json.getAsString()))
                .create();
    }

    public void connect() {
        connectionManager.connect();
    }

    public void disconnect() {
        for (Thread thread : new ArrayList<>(subscriberThreads)) {
            thread.interrupt();
        }
        subscriberThreads.clear();
        for (RedisRequestHandle<?> requestHandle : new ArrayList<>(requestHandles)) {
            requestHandle.cancel();
        }
        requestHandles.clear();
        transportHealth.setActiveSubscriberThreads(0);
        deliveryFailures.clear();
        connectionManager.disconnect();
    }

    public void send(Object event) {
        if (!ensureConnected()) {
            return;
        }

        try {
            var route = router.route(event, config.server);
            long now = System.currentTimeMillis();
            String payloadJson = payloadJson(event, now);
            RedisCommands<String, String> commands = connectionManager.commands();
            streamSupport.xaddWithTrim(commands, route.streamKey(), envelopeFactory.eventFields(route, payloadJson, now));
            publishedEvents.incrementAndGet();
            publishWarningLogged = false;
        } catch (Exception e) {
            publishFailures.incrementAndGet();
            if (!publishWarningLogged) {
                publishWarningLogged = true;
                Log.warn("Redis publish failed: @", e.getMessage());
            }
        }
    }

    public <T> Subscription<T> subscribe(Class<T> type, Cons<T> listener) {
        if (!supportsSubscribeType(type)) {
            throw new UnsupportedOperationException("Redis subscribe does not support type: " + type.getName());
        }
        if (!ensureConnected()) {
            throw new IllegalStateException("Redis backend is unavailable for subscribe");
        }

        List<String> streams = router.subscribeStreamsFor(type, config.server);
        if (streams.isEmpty()) {
            throw new UnsupportedOperationException("Redis subscribe has no stream mapping for type: " + type.getName());
        }

        RedisSubscriber<T> subscription = new RedisSubscriber<>(this::removeSubscriberThreads);

        for (String stream : streams) {
            Thread thread = Thread.ofVirtual()
                    .name("redis-sub-" + type.getSimpleName() + "-" + stream)
                    .start(() -> consumeLoop(stream, type, listener));
            registerSubscriberThread(subscription, thread);

            if (config.redisReclaimEnabled) {
                String group = groupFor(type, stream);
                Thread reclaimThread = Thread.ofVirtual()
                        .name("redis-reclaim-" + type.getSimpleName() + "-" + stream)
                        .start(() -> reclaimLoop(stream, group, type, listener));
                registerSubscriberThread(subscription, reclaimThread);
            }
        }
        return subscription;
    }

    public <T extends Response> RequestSubscription<T> request(Request<T> request, Cons<T> listener, Runnable timeout) {
        if (!supportsRequestType(request.getClass())) {
            throw new UnsupportedOperationException("Redis request does not support type: " + request.getClass().getName());
        }
        if (!ensureConnected()) {
            throw new IllegalStateException("Redis backend is unavailable for request");
        }

        Class<? extends Response> responseType = router.responseTypeForRequest(request.getClass());
        RedisRequestHandle<T> requestHandle = new RedisRequestHandle<>(null);
        requestHandles.add(requestHandle);
        requestHandle.onFinish(() -> requestHandles.remove(requestHandle));
        if (responseType == null) {
            timeout.run();
            requestHandle.markFinished();
            return requestHandle;
        }

        var route = router.route(request, config.server);
        String replyTo = "xcore:rpc:resp:" + config.server;
        String correlationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long timeoutMs = 5000L;

        CountDownLatch listenerReady = new CountDownLatch(1);
        Thread awaitThread = Thread.ofVirtual().name("redis-rpc-await-" + request.getClass().getSimpleName()).start(() ->
                awaitRpcResponse(replyTo, correlationId, responseType, listener, timeout, timeoutMs, listenerReady, requestHandle)
        );
        requestHandle.bindWorker(awaitThread);

        try {
            listenerReady.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            requestHandle.cancel();
            return requestHandle;
        }

        if (requestHandle.isCancelled()) {
            return requestHandle;
        }

        String requestJson = gson.toJson(request);
        RedisCommands<String, String> commands = connectionManager.commands();
        try {
            streamSupport.xaddWithTrim(commands, route.streamKey(),
                    envelopeFactory.rpcRequestFields(route, requestJson, replyTo, correlationId, now, timeoutMs));
            rpcRequests.incrementAndGet();
        } catch (RuntimeException e) {
            requestHandle.cancel();
            throw e;
        }

        return requestHandle;
    }

    public <T extends Response> void respond(Request<T> request, T response) {
        RedisRpcTracker.RpcInboundContext context = rpcTracker.take(request);
        if (context == null) {
            Log.warn("Redis respond context is missing for request: @", request.getClass().getName());
            return;
        }
        if (!ensureConnected()) {
            return;
        }

        rpcResponses.incrementAndGet();
        try {
            RedisCommands<String, String> commands = connectionManager.commands();
            streamSupport.xaddWithTrim(commands, context.replyTo(),
                    envelopeFactory.rpcResponseFields(context, gson.toJson(response), System.currentTimeMillis()));
        } catch (RuntimeException e) {
            rpcResponses.decrementAndGet();
            throw e;
        }
    }

    public boolean supportsSubscribeType(Class<?> type) {
        if (router.isReadOnlyType(type)) {
            return true;
        }
        if (type == TransportEvents.KickBannedPlayer.class) {
            return true;
        }
        if (router.isRpcRequestType(type)) {
            return true;
        }
        return router.isMutatingType(type);
    }

    public boolean supportsRequestType(Class<?> type) {
        return router.isRpcRequestType(type) && ensureConnected();
    }

    public <T> T withCommands(java.util.function.Function<RedisCommands<String, String>, T> operation, T fallback) {
        if (!ensureConnected()) {
            return fallback;
        }

        try {
            return operation.apply(connectionManager.commands());
        } catch (Exception e) {
            Log.warn("Redis direct command failed: @", e.getMessage());
            return fallback;
        }
    }

    public boolean supportsRespond(Request<?> request) {
        return rpcTracker.contains(request);
    }

    private String payloadJson(Object event, long now) {
        if (event instanceof BanData banData) {
            return gson.toJson(ModerationProtocolMapper.toBanCreated(
                    banData,
                    config.server,
                    Instant.ofEpochMilli(now)
            ));
        }
        return gson.toJson(event);
    }

    private boolean ensureConnected() {
        return connectionManager.ensureConnected();
    }

    private <T> void consumeLoop(String stream, Class<T> type, Cons<T> listener) {
        var client = connectionManager.client();
        if (client == null) {
            return;
        }

        try (StatefulRedisConnection<String, String> subConnection = client.connect()) {
            RedisCommands<String, String> subCommands = subConnection.sync();
            String group = groupFor(type, stream);
            ensureGroup(subCommands, stream, group);
            Consumer<String> consumer = Consumer.from(group, config.redisConsumerName);

            while (!Thread.currentThread().isInterrupted()) {
                List<StreamMessage<String, String>> messages;
                try {
                    messages = subCommands.xreadgroup(
                            consumer,
                            XReadArgs.Builder.block(1000).count(50),
                            XReadArgs.StreamOffset.lastConsumed(stream)
                    );
                } catch (Exception e) {
                    if (isNoGroupError(e)) {
                        ensureGroup(subCommands, stream, group);
                        continue;
                    }
                    if (!sleepWithBackoff("consume", stream, e)) {
                        return;
                    }
                    continue;
                }

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (StreamMessage<String, String> message : messages) {
                    try {
                        if (dispatchStreamMessage(subCommands, message, type, listener)) {
                            subCommands.xack(stream, group, message.getId());
                            clearFailureCounter(stream, message.getId());
                        } else {
                            handleFailedMessage(subCommands, stream, group, message, "dispatch_failed");
                        }
                    } catch (Exception e) {
                        if (!sleepWithBackoff("consume", stream, e)) {
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.warn("Redis consume loop stopped for stream @: @", stream, e.getMessage());
        }
    }

    private <T> void reclaimLoop(String stream, String group, Class<T> type, Cons<T> listener) {
        var client = connectionManager.client();
        if (client == null) {
            return;
        }

        try (StatefulRedisConnection<String, String> reclaimConnection = client.connect()) {
            RedisCommands<String, String> reclaimCommands = reclaimConnection.sync();
            ensureGroup(reclaimCommands, stream, group);
            String cursor = "0-0";
            Consumer<String> consumer = Consumer.from(group, config.redisConsumerName);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    var claimed = reclaimCommands.xautoclaim(stream, new XAutoClaimArgs<String>()
                            .consumer(consumer)
                            .minIdleTime(config.redisReclaimMinIdleMs)
                            .startId(cursor)
                            .count(config.redisReclaimBatch));

                    if (claimed == null) {
                        Thread.sleep(1000L);
                        continue;
                    }

                    cursor = claimed.getId();
                    List<StreamMessage<String, String>> messages = claimed.getMessages();
                    if (messages == null || messages.isEmpty()) {
                        Thread.sleep(1000L);
                        continue;
                    }

                    reclaimedMessages.addAndGet(messages.size());

                    for (StreamMessage<String, String> message : messages) {
                        try {
                            if (dispatchStreamMessage(reclaimCommands, message, type, listener)) {
                                reclaimCommands.xack(stream, group, message.getId());
                                clearFailureCounter(stream, message.getId());
                            } else {
                                handleFailedMessage(reclaimCommands, stream, group, message, "reclaim_dispatch_failed");
                            }
                        } catch (Exception e) {
                            if (!sleepWithBackoff("reclaim", stream, e)) {
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    if (isNoGroupError(e)) {
                        ensureGroup(reclaimCommands, stream, group);
                        continue;
                    }
                    if (!sleepWithBackoff("reclaim", stream, e)) {
                        return;
                    }
                    continue;
                }
            }
        } catch (Exception e) {
            Log.warn("Redis reclaim loop stopped for stream @: @", stream, e.getMessage());
        }
    }

    private boolean sleepWithBackoff(String loopType, String stream, Exception e) {
        Log.warn("Redis @ loop transient failure on stream @: @", loopType, stream, e.getMessage());
        try {
            Thread.sleep(2000L);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private <T> boolean dispatchStreamMessage(RedisCommands<String, String> consumerCommands,
                                              StreamMessage<String, String> message,
                                              Class<T> type,
                                              Cons<T> listener) {
        String expiresAtRaw = message.getBody().getOrDefault("expires_at", "0");
        long expiresAt = 0L;
        try {
            expiresAt = Long.parseLong(expiresAtRaw);
            if (expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
                return true;
            }
        } catch (NumberFormatException ignored) {
        }

        String idempotencyRedisKey = null;
        boolean idempotencyClaimed = false;
        if (router.isMutatingType(type)) {
            String idempotencyKey = message.getBody().getOrDefault("idempotency_key", "");
            if (!idempotencyKey.isBlank()) {
                long ttlSeconds = envelopeFactory.resolveIdempotencyTtlSeconds(expiresAt);
                idempotencyRedisKey = "xcore:idmp:consume:" + config.server + ":" + idempotencyKey;
                String claimed = consumerCommands.set(
                        idempotencyRedisKey,
                        "1",
                        SetArgs.Builder.ex(ttlSeconds).nx()
                );
                if (claimed == null) {
                    return true;
                }
                idempotencyClaimed = true;
            }
        }

        String payloadJson = message.getBody().get("payload_json");
        if (payloadJson == null || payloadJson.isBlank()) {
            return true;
        }

        if (router.isRpcRequestType(type)) {
            String expectedRpcType = router.rpcTypeForRequestClass(type);
            String foundRpcType = message.getBody().getOrDefault("rpc_type", "");
            if (expectedRpcType != null && !expectedRpcType.equals(foundRpcType)) {
                return true;
            }
        }

        try {
            T event = gson.fromJson(payloadJson, type);
            if (event instanceof Request<?> request && router.isRpcRequestType(type)) {
                String correlationId = message.getBody().getOrDefault("correlation_id", "");
                String replyTo = message.getBody().getOrDefault("reply_to", "xcore:rpc:resp:" + config.server);
                String rpcType = message.getBody().getOrDefault("rpc_type", "rpc.unknown");
                rpcTracker.registerInbound(request, correlationId, replyTo, rpcType, System.currentTimeMillis());
            }
            consumedEvents.incrementAndGet();
            listener.get(event);
            return true;
        } catch (Exception e) {
            if (idempotencyClaimed && idempotencyRedisKey != null) {
                consumerCommands.del(idempotencyRedisKey);
            }
            consumeFailures.incrementAndGet();
            Log.warn("Redis consume decode/dispatch failed for stream message @: @", message.getId(), e.getMessage());
            return false;
        }
    }

    private <T extends Response> void awaitRpcResponse(String replyTo,
                                                       String correlationId,
                                                       Class<? extends Response> responseType,
                                                       Cons<T> listener,
                                                       Runnable timeout,
                                                       long timeoutMs,
                                                       CountDownLatch listenerReady,
                                                       RedisRequestHandle<T> requestHandle) {
        rpcTracker.awaitResponse(connectionManager.client(), replyTo, correlationId, responseType, listener, timeout, timeoutMs, listenerReady, rpcTimeouts, requestHandle);
    }

    private <T> void registerSubscriberThread(RedisSubscriber<T> subscription, Thread thread) {
        subscriberThreads.add(thread);
        subscription.attach(thread);
        transportHealth.setActiveSubscriberThreads(subscriberThreads.size());
    }

    private void removeSubscriberThreads(List<Thread> threads) {
        subscriberThreads.removeAll(threads);
        transportHealth.setActiveSubscriberThreads(subscriberThreads.size());
    }

    private String groupFor(Class<?> type, String stream) {
        return streamSupport.groupFor(type, stream);
    }

    private void ensureGroup(RedisCommands<String, String> subCommands, String stream, String group) {
        streamSupport.ensureGroup(subCommands, stream, group);
    }

    private void clearFailureCounter(String stream, String messageId) {
        deliveryFailures.remove(failureKey(stream, messageId));
    }

    private void handleFailedMessage(RedisCommands<String, String> commands,
                                     String stream,
                                     String group,
                                     StreamMessage<String, String> message,
                                     String reason) {
        String key = failureKey(stream, message.getId());
        int attempt = deliveryFailures.merge(key, 1, Integer::sum);
        int maxAttempts = Math.max(1, config.redisMaxDeliveryAttempts);

        if (attempt >= maxAttempts) {
            if (config.redisDlqEnabled) {
                routeToDlq(commands, stream, group, message, attempt, reason);
            }
            commands.xack(stream, group, message.getId());
            deliveryFailures.remove(key);
        }
    }

    private void routeToDlq(RedisCommands<String, String> commands,
                            String sourceStream,
                            String sourceGroup,
                            StreamMessage<String, String> message,
                            int attempts,
                            String reason) {
        String dlqStream = dlqStreamFor(sourceStream);
        streamSupport.xaddWithTrim(commands, dlqStream,
                envelopeFactory.dlqFields(sourceStream, sourceGroup, message, attempts, reason, System.currentTimeMillis()));
        dlqRouted.incrementAndGet();
    }

    private String dlqStreamFor(String sourceStream) {
        return streamSupport.dlqStreamFor(sourceStream);
    }

    private String failureKey(String stream, String messageId) {
        return streamSupport.failureKey(stream, messageId);
    }

    private boolean isNoGroupError(Exception e) {
        return streamSupport.isNoGroupError(e);
    }

    public Map<String, Long> metricsSnapshot() {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("published_events", publishedEvents.get());
        metrics.put("publish_failures", publishFailures.get());
        metrics.put("consumed_events", consumedEvents.get());
        metrics.put("consume_failures", consumeFailures.get());
        metrics.put("rpc_requests", rpcRequests.get());
        metrics.put("rpc_responses", rpcResponses.get());
        metrics.put("rpc_timeouts", rpcTimeouts.get());
        metrics.put("dlq_routed", dlqRouted.get());
        metrics.put("reclaimed_messages", reclaimedMessages.get());
        metrics.put("active_subscriber_threads", (long) subscriberThreads.size());
        metrics.put("active_request_handles", (long) requestHandles.size());
        metrics.put("pending_rpc_contexts", (long) rpcTracker.size());
        metrics.put("tracked_failures", (long) deliveryFailures.size());
        RedisTransportHealth.Snapshot snapshot = transportHealth.snapshot();
        metrics.put("redis_available", snapshot.available() ? 1L : 0L);
        metrics.put("redis_lifecycle_connected", snapshot.lifecycleState() == RedisTransportHealth.LifecycleState.CONNECTED ? 1L : 0L);
        metrics.put("redis_last_connect_attempt_at", snapshot.lastConnectAttemptAt());
        metrics.put("redis_last_connected_at", snapshot.lastConnectedAt());
        metrics.put("redis_last_disconnected_at", snapshot.lastDisconnectedAt());
        return metrics;
    }
}
