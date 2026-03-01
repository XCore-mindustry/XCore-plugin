package org.xcore.plugin.service.network;

import arc.func.Cons;
import arc.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.xcore.plugin.event.SocketEvents.Request;
import org.xcore.plugin.event.SocketEvents.Response;
import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.SetArgs;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.SocketEvents;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private static final long MAXLEN_EVT = 50_000L;
    private static final long MAXLEN_CMD = 10_000L;
    private static final long MAXLEN_RPC_REQ = 5_000L;
    private static final long MAXLEN_RPC_RESP = 20_000L;
    private static final long MAXLEN_DLQ = 100_000L;


    private final Config config;
    private final Gson gson;
    private final RedisStreamRouter router;
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> commands;
    private boolean connectionWarningLogged;
    private boolean publishWarningLogged;
    private final List<Thread> subscriberThreads = new CopyOnWriteArrayList<>();
    private final Map<Request<?>, RpcInboundContext> inboundRpcContexts = Collections.synchronizedMap(new IdentityHashMap<>());
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
    public RedisNetworkBackend(Config config) {
        this.config = config;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>)
                        (src, typeOfSrc, context) -> src == null ? null : new com.google.gson.JsonPrimitive(src.toString()))
                .registerTypeAdapter(Instant.class, (com.google.gson.JsonDeserializer<Instant>)
                        (json, typeOfT, context) -> json == null || json.isJsonNull()
                                ? null
                                : Instant.parse(json.getAsString()))
                .create();
        this.router = new RedisStreamRouter();
    }

    public void connect() {
        if (commands != null) {
            return;
        }

        client = RedisClient.create(config.redisUrl);
        connection = client.connect();
        commands = connection.sync();
        connectionWarningLogged = false;
        Log.info("Redis backend connected, url=@", config.redisUrl);
    }

    public void disconnect() {
        for (Thread thread : new ArrayList<>(subscriberThreads)) {
            thread.interrupt();
        }
        subscriberThreads.clear();
        deliveryFailures.clear();

        commands = null;
        if (connection != null) {
            connection.close();
            connection = null;
        }
        if (client != null) {
            client.shutdown();
            client = null;
        }
    }

    public void send(Object event) {
        if (!ensureConnected()) {
            return;
        }

        try {
            var route = router.route(event, config.server);
            long now = System.currentTimeMillis();
            String eventId = UUID.randomUUID().toString();
            String payloadJson = gson.toJson(event);

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("schema_version", "1");
            fields.put("event_type", route.eventType());
            fields.put("event_id", eventId);
            fields.put(
                    "idempotency_key",
                    deterministicIdempotencyKey(
                            route.eventType(),
                            config.server,
                            payloadJson,
                            now,
                            Math.max(60_000L, route.ttlMillis())
                    )
            );
            fields.put("producer", "server:" + config.server);
            fields.put("created_at", String.valueOf(now));
            fields.put("expires_at", String.valueOf(now + route.ttlMillis()));
            fields.put("server", config.server);
            fields.put("payload_json", payloadJson);
            xaddWithTrim(commands, route.streamKey(), fields);
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

        List<Thread> localThreads = new CopyOnWriteArrayList<>();

        for (String stream : streams) {
            var thread = Thread.ofVirtual()
                    .name("redis-sub-" + type.getSimpleName() + "-" + stream)
                    .start(() -> consumeLoop(stream, type, listener));
            subscriberThreads.add(thread);
            localThreads.add(thread);

            if (config.redisReclaimEnabled) {
                String group = groupFor(type, stream);
                var reclaimThread = Thread.ofVirtual()
                        .name("redis-reclaim-" + type.getSimpleName() + "-" + stream)
                        .start(() -> reclaimLoop(stream, group, type, listener));
                subscriberThreads.add(reclaimThread);
                localThreads.add(reclaimThread);
            }
        }
        return new RedisSubscription<>(localThreads, subscriberThreads);
    }

    public <T extends Response> RequestSubscription<T> request(Request<T> request, Cons<T> listener, Runnable timeout) {
        if (!supportsRequestType(request.getClass())) {
            throw new UnsupportedOperationException("Redis request does not support type: " + request.getClass().getName());
        }
        if (!ensureConnected()) {
            throw new IllegalStateException("Redis backend is unavailable for request");
        }

        var route = router.route(request, config.server);
        String replyTo = "xcore:rpc:resp:" + config.server;
        String correlationId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long timeoutMs = 5000L;

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schema_version", "1");
        fields.put("rpc_type", route.eventType());
        fields.put("correlation_id", correlationId);
        fields.put("request_id", UUID.randomUUID().toString());
        fields.put(
                "idempotency_key",
                deterministicIdempotencyKey(
                        "rpc." + route.eventType(),
                        config.server,
                        gson.toJson(request),
                        now,
                        Math.max(60_000L, timeoutMs)
                )
        );
        fields.put("reply_to", replyTo);
        fields.put("requested_by", "server:" + config.server);
        fields.put("server", config.server);
        fields.put("timeout_ms", String.valueOf(timeoutMs));
        fields.put("created_at", String.valueOf(now));
        fields.put("expires_at", String.valueOf(now + timeoutMs));
        fields.put("payload_json", gson.toJson(request));

        xaddWithTrim(commands, route.streamKey(), fields);
        rpcRequests.incrementAndGet();

        Class<? extends Response> responseType = router.responseTypeForRequest(request.getClass());
        if (responseType == null) {
            timeout.run();
            return null;
        }

        Thread.ofVirtual().name("redis-rpc-await-" + request.getClass().getSimpleName()).start(() ->
                awaitRpcResponse(replyTo, correlationId, responseType, listener, timeout, timeoutMs)
        );

        return null;
    }

    public <T extends Response> void respond(Request<T> request, T response) {
        RpcInboundContext context;
        synchronized (inboundRpcContexts) {
            context = inboundRpcContexts.remove(request);
        }
        if (context == null) {
            Log.warn("Redis respond context is missing for request: @", request.getClass().getName());
            return;
        }
        if (!ensureConnected()) {
            return;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schema_version", "1");
        fields.put("rpc_type", context.rpcType());
        fields.put("correlation_id", context.correlationId());
        fields.put("server", config.server);
        fields.put("status", "ok");
        fields.put("error_code", "");
        fields.put("error_message", "");
        fields.put("responded_at", String.valueOf(System.currentTimeMillis()));
        fields.put("payload_json", gson.toJson(response));
        xaddWithTrim(commands, context.replyTo(), fields);
        rpcResponses.incrementAndGet();
    }

    public boolean isPrimaryControlNode() {
        return switch (config.nodeRole) {
            case PRIMARY -> true;
            case WORKER -> false;
            case AUTO -> false;
        };
    }

    public boolean supportsSubscribeType(Class<?> type) {
        if (router.isReadOnlyType(type)) {
            return true;
        }
        if (type == SocketEvents.KickBannedPlayer.class) {
            return config.redisConsumeEnabled;
        }
        if (config.redisRpcEnabled && router.isRpcRequestType(type)) {
            return true;
        }
        return config.redisMutatingConsumeEnabled && router.isMutatingType(type);
    }

    public boolean supportsRequestType(Class<?> type) {
        return config.redisRpcEnabled && router.isRpcRequestType(type) && ensureConnected();
    }

    public boolean supportsRespond(Request<?> request) {
        if (!config.redisRpcEnabled) {
            return false;
        }
        synchronized (inboundRpcContexts) {
            return inboundRpcContexts.containsKey(request);
        }
    }

    private boolean ensureConnected() {
        if (commands != null) {
            return true;
        }

        try {
            connect();
            return commands != null;
        } catch (Exception e) {
            if (!connectionWarningLogged) {
                connectionWarningLogged = true;
                Log.warn("Redis backend unavailable, continuing without publish: @", e.getMessage());
            }
            return false;
        }
    }

    private <T> void consumeLoop(String stream, Class<T> type, Cons<T> listener) {
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
                long ttlSeconds = resolveIdempotencyTtlSeconds(expiresAt);
                idempotencyRedisKey = "xcore:idmp:consume:" + idempotencyKey;
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
            if (event instanceof Request<?> request && config.redisRpcEnabled && router.isRpcRequestType(type)) {
                String correlationId = message.getBody().getOrDefault("correlation_id", "");
                String replyTo = message.getBody().getOrDefault("reply_to", "xcore:rpc:resp:" + config.server);
                String rpcType = message.getBody().getOrDefault("rpc_type", "rpc.unknown");
                synchronized (inboundRpcContexts) {
                    cleanupExpiredRpcContexts(System.currentTimeMillis());
                    inboundRpcContexts.put(request, new RpcInboundContext(correlationId, replyTo, rpcType, System.currentTimeMillis()));
                }
            }
            listener.get(event);
            consumedEvents.incrementAndGet();
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

    private long resolveIdempotencyTtlSeconds(long expiresAtMillis) {
        if (expiresAtMillis <= 0L) {
            return 600L;
        }
        long ttlMillis = expiresAtMillis - System.currentTimeMillis();
        long ttlSeconds = Math.max(1L, ttlMillis / 1000L);
        return Math.min(ttlSeconds, 86_400L);
    }

    private <T extends Response> void awaitRpcResponse(String replyTo,
                                                       String correlationId,
                                                       Class<? extends Response> responseType,
                                                       Cons<T> listener,
                                                       Runnable timeout,
                                                       long timeoutMs) {
        if (client == null) {
            timeout.run();
            return;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        try (StatefulRedisConnection<String, String> rpcConnection = client.connect()) {
            RedisCommands<String, String> rpcCommands = rpcConnection.sync();
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
        }

        rpcTimeouts.incrementAndGet();
        timeout.run();
    }

    private record RpcInboundContext(String correlationId, String replyTo, String rpcType, long createdAtMillis) {
    }

    private static final class RedisSubscription<T> extends Subscription<T> {
        private final List<Thread> localThreads;
        private final List<Thread> allThreads;

        private RedisSubscription(List<Thread> localThreads, List<Thread> allThreads) {
            this.localThreads = localThreads;
            this.allThreads = allThreads;
        }

        @Override
        public void call(Object object) {
        }

        @Override
        public boolean unsubscribe() {
            for (Thread thread : localThreads) {
                thread.interrupt();
                allThreads.remove(thread);
            }
            localThreads.clear();
            return true;
        }
    }

    private String groupFor(Class<?> type, String stream) {
        return config.redisGroupPrefix + ":" + type.getSimpleName().toLowerCase() + ":" + Math.abs(stream.hashCode());
    }

    private void ensureGroup(RedisCommands<String, String> subCommands, String stream, String group) {
        try {
            subCommands.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0-0"), group, new XGroupCreateArgs().mkstream(true));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || !msg.toUpperCase().contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    private void cleanupExpiredRpcContexts(long nowMillis) {
        List<Request<?>> toRemove = new ArrayList<>();
        for (Map.Entry<Request<?>, RpcInboundContext> entry : inboundRpcContexts.entrySet()) {
            if (nowMillis - entry.getValue().createdAtMillis() > 120000L) {
                toRemove.add(entry.getKey());
            }
        }
        for (Request<?> request : toRemove) {
            inboundRpcContexts.remove(request);
        }
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
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("source_stream", sourceStream);
        fields.put("source_group", sourceGroup);
        fields.put("source_id", message.getId());
        fields.put("failed_at", String.valueOf(System.currentTimeMillis()));
        fields.put("attempts", String.valueOf(attempts));
        fields.put("failure_reason", reason);
        fields.put("event_type", message.getBody().getOrDefault("event_type", ""));
        fields.put("rpc_type", message.getBody().getOrDefault("rpc_type", ""));
        fields.put("message_json", gson.toJson(message.getBody()));
        xaddWithTrim(commands, dlqStream, fields);
        dlqRouted.incrementAndGet();
    }

    private void xaddWithTrim(RedisCommands<String, String> commands,
                              String stream,
                              Map<String, String> fields) {
        long maxLen = streamMaxLen(stream);
        commands.xadd(
                stream,
                XAddArgs.Builder.maxlen(maxLen).approximateTrimming(true),
                fields
        );
    }

    private long streamMaxLen(String stream) {
        if (stream.startsWith("xcore:evt:")) {
            return MAXLEN_EVT;
        }
        if (stream.startsWith("xcore:cmd:")) {
            return MAXLEN_CMD;
        }
        if (stream.startsWith("xcore:rpc:req:")) {
            return MAXLEN_RPC_REQ;
        }
        if (stream.startsWith("xcore:rpc:resp:")) {
            return MAXLEN_RPC_RESP;
        }
        if (stream.startsWith(config.redisDlqPrefix + ":")) {
            return MAXLEN_DLQ;
        }
        return MAXLEN_EVT;
    }

    private String deterministicIdempotencyKey(String prefix,
                                               String server,
                                               String payloadJson,
                                               long nowMs,
                                               long ttlMs) {
        long windowMs = Math.max(60_000L, Math.min(ttlMs, 600_000L));
        long window = nowMs / windowMs;
        String material = prefix + "|" + server + "|" + payloadJson + "|" + window;
        return prefix + ":" + sha256Hex(material).substring(0, 24);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private String dlqStreamFor(String sourceStream) {
        if (sourceStream.startsWith("xcore:rpc:")) {
            return config.redisDlqPrefix + ":rpc";
        }
        if (sourceStream.startsWith("xcore:cmd:")) {
            return config.redisDlqPrefix + ":cmd";
        }
        return config.redisDlqPrefix + ":evt";
    }

    private String failureKey(String stream, String messageId) {
        return stream + "|" + messageId;
    }

    private boolean isNoGroupError(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.toUpperCase().contains("NOGROUP");
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
        metrics.put("pending_rpc_contexts", (long) inboundRpcContexts.size());
        metrics.put("tracked_failures", (long) deliveryFailures.size());
        return metrics;
    }
}
