package org.xcore.plugin.service.network;

import org.xcore.plugin.service.network.RedisNetworkBackend.Subscription;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.BanData;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisNetworkBackendIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    private RedisNetworkBackend serverBackend;
    private RedisNetworkBackend requesterBackend;

    @AfterEach
    void tearDown() {
        if (serverBackend != null) {
            serverBackend.disconnect();
        }
        if (requesterBackend != null) {
            requesterBackend.disconnect();
        }
    }

    @Test
    @DisplayName("send publishes envelope to mapped stream")
    void sendPublishesEnvelopeToMappedStream() {
        Config config = baseConfig("alpha");
        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        requesterBackend.send(new SocketEvents.MessageEvent("tester", "hello", "alpha"));

        assertThat(requesterBackend.metricsSnapshot().getOrDefault("published_events", 0L)).isGreaterThanOrEqualTo(1L);

        try (RedisClient client = RedisClient.create(config.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            List<StreamMessage<String, String>> messages = connection.sync().xread(
                    XReadArgs.StreamOffset.from("xcore:evt:chat:message", "0-0")
            );

            assertThat(messages).isNotEmpty();
            var last = messages.get(messages.size() - 1).getBody();
            assertThat(last.get("event_type")).isEqualTo("chat.message");
            assertThat(last.get("payload_json")).contains("hello");
        }
    }

    @Test
    @DisplayName("send serializes BanData with Instant without reflection failure")
    void sendSerializesBanDataInstant() {
        Config config = baseConfig("alpha");
        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        requesterBackend.send(BanData.builder()
                .uuid("u-1")
                .ip("1.2.3.4")
                .name("player")
                .adminName("admin")
                .reason("rule")
                .expireDate(Instant.now().plusSeconds(3600))
                .build());

        assertThat(requesterBackend.metricsSnapshot().getOrDefault("publish_failures", 0L)).isEqualTo(0L);

        try (RedisClient client = RedisClient.create(config.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            List<StreamMessage<String, String>> messages = connection.sync().xread(
                    XReadArgs.StreamOffset.from("xcore:evt:moderation:ban", "0-0")
            );

            assertThat(messages).isNotEmpty();
            var last = messages.get(messages.size() - 1).getBody();
            assertThat(last.get("event_type")).isEqualTo("moderation.ban");
            assertThat(last.get("payload_json")).contains("expireDate");
        }
    }

    @Test
    @DisplayName("subscribe consumes read-only stream messages")
    void subscribeConsumesReadOnlyStreamMessages() throws InterruptedException {
        Config config = baseConfig("alpha");
        config.redisConsumeEnabled = true;

        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SocketEvents.MessageEvent> received = new AtomicReference<>();

        Subscription<SocketEvents.MessageEvent> subscription = requesterBackend.subscribe(SocketEvents.MessageEvent.class, event -> {
            received.set(event);
            latch.countDown();
        });

        requesterBackend.send(new SocketEvents.MessageEvent("tester", "bridge", "alpha"));

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().message()).isEqualTo("bridge");
        assertThat(requesterBackend.metricsSnapshot().getOrDefault("consumed_events", 0L)).isGreaterThanOrEqualTo(1L);

        subscription.unsubscribe();
    }

    @Test
    @DisplayName("kick-banned subscribe works when mutating consume is disabled")
    void kickBannedSubscribeWorksWithoutMutatingFlag() throws InterruptedException {
        Config config = baseConfig("alpha");
        config.redisConsumeEnabled = true;
        config.redisMutatingConsumeEnabled = false;

        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SocketEvents.KickBannedPlayer> received = new AtomicReference<>();

        Subscription<SocketEvents.KickBannedPlayer> subscription = requesterBackend.subscribe(
                SocketEvents.KickBannedPlayer.class,
                event -> {
                    received.set(event);
                    latch.countDown();
                }
        );

        requesterBackend.send(new SocketEvents.KickBannedPlayer("uuid-a", "1.2.3.4"));

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().uuid()).isEqualTo("uuid-a");

        subscription.unsubscribe();
    }

    @Test
    @DisplayName("rpc request/response roundtrip works for maps list")
    void rpcRequestResponseRoundtripWorks() throws InterruptedException {
        Config serverConfig = baseConfig("target");
        serverConfig.redisRpcEnabled = true;

        Config requesterConfig = baseConfig("discord");
        requesterConfig.redisRpcEnabled = true;

        serverBackend = new RedisNetworkBackend(serverConfig);
        requesterBackend = new RedisNetworkBackend(requesterConfig);
        serverBackend.connect();
        requesterBackend.connect();

        Subscription<SocketEvents.MapsListRequest> serverSubscription =
                serverBackend.subscribe(SocketEvents.MapsListRequest.class,
                        request -> serverBackend.respond(
                                request,
                                new SocketEvents.MapsListResponse(new SocketEvents.MapEntry[]{
                                        new SocketEvents.MapEntry("A", "a.msav", "author-a", 100, 120, 1024L),
                                        new SocketEvents.MapEntry("B", "b.msav", "author-b", 80, 80, 2048L)
                                })
                        ));

        CountDownLatch responseLatch = new CountDownLatch(1);
        CountDownLatch timeoutLatch = new CountDownLatch(1);
        AtomicReference<SocketEvents.MapsListResponse> responseRef = new AtomicReference<>();

        requesterBackend.request(new SocketEvents.MapsListRequest("target"), response -> {
            responseRef.set(response);
            responseLatch.countDown();
        }, timeoutLatch::countDown);

        assertThat(responseLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(timeoutLatch.getCount()).isEqualTo(1);
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().maps).extracting(entry -> entry.name).containsExactly("A", "B");
        assertThat(requesterBackend.metricsSnapshot().getOrDefault("rpc_requests", 0L)).isGreaterThanOrEqualTo(1L);
        assertThat(serverBackend.metricsSnapshot().getOrDefault("rpc_responses", 0L)).isGreaterThanOrEqualTo(1L);

        serverSubscription.unsubscribe();
    }

    @Test
    @DisplayName("rpc dispatch filters by rpc_type and does not cross-trigger handlers")
    void rpcDispatchDoesNotCrossTriggerHandlers() throws InterruptedException {
        Config serverConfig = baseConfig("target");
        serverConfig.redisRpcEnabled = true;

        Config requesterConfig = baseConfig("discord");
        requesterConfig.redisRpcEnabled = true;

        serverBackend = new RedisNetworkBackend(serverConfig);
        requesterBackend = new RedisNetworkBackend(requesterConfig);
        serverBackend.connect();
        requesterBackend.connect();

        CountDownLatch listLatch = new CountDownLatch(1);
        Subscription<SocketEvents.MapsListRequest> listSubscription =
                serverBackend.subscribe(SocketEvents.MapsListRequest.class, request -> {
                    listLatch.countDown();
                    serverBackend.respond(
                            request,
                            new SocketEvents.MapsListResponse(new SocketEvents.MapEntry[]{
                                    new SocketEvents.MapEntry("A", "a.msav", "author-a", 100, 120, 1024L),
                                    new SocketEvents.MapEntry("B", "b.msav", "author-b", 80, 80, 2048L)
                            })
                    );
                });

        Subscription<SocketEvents.MapRemoveRequest> removeSubscription =
                serverBackend.subscribe(SocketEvents.MapRemoveRequest.class,
                        request -> serverBackend.respond(request, new SocketEvents.MapRemoveResponse("Removed")));

        CountDownLatch responseLatch = new CountDownLatch(1);
        CountDownLatch timeoutLatch = new CountDownLatch(1);
        AtomicReference<SocketEvents.MapRemoveResponse> responseRef = new AtomicReference<>();

        requesterBackend.request(new SocketEvents.MapRemoveRequest("target", "MapX"), response -> {
            responseRef.set(response);
            responseLatch.countDown();
        }, timeoutLatch::countDown);

        assertThat(responseLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(timeoutLatch.getCount()).isEqualTo(1);
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().result).isEqualTo("Removed");
        assertThat(listLatch.getCount()).isEqualTo(1);

        listSubscription.unsubscribe();
        removeSubscription.unsubscribe();
    }

    @Test
    @DisplayName("expired rpc request is ACKed and dropped without handler execution")
    void expiredRpcRequestIsDropped() throws InterruptedException {
        Config serverConfig = baseConfig("target");
        serverConfig.redisRpcEnabled = true;

        serverBackend = new RedisNetworkBackend(serverConfig);
        serverBackend.connect();

        CountDownLatch handlerLatch = new CountDownLatch(1);
        Subscription<SocketEvents.MapsListRequest> subscription =
                serverBackend.subscribe(SocketEvents.MapsListRequest.class, request -> handlerLatch.countDown());

        try (RedisClient client = RedisClient.create(serverConfig.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            long now = System.currentTimeMillis();
            connection.sync().xadd("xcore:rpc:req:target", java.util.Map.ofEntries(
                    java.util.Map.entry("schema_version", "1"),
                    java.util.Map.entry("rpc_type", "maps.list"),
                    java.util.Map.entry("correlation_id", "c-expired"),
                    java.util.Map.entry("request_id", "r-expired"),
                    java.util.Map.entry("reply_to", "xcore:rpc:resp:discord"),
                    java.util.Map.entry("requested_by", "discord-bot"),
                    java.util.Map.entry("server", "target"),
                    java.util.Map.entry("timeout_ms", "5000"),
                    java.util.Map.entry("created_at", String.valueOf(now - 20_000)),
                    java.util.Map.entry("expires_at", String.valueOf(now - 10_000)),
                    java.util.Map.entry("payload_json", "{\"server\":\"target\"}")
            ));
        }

        Thread.sleep(1000);

        assertThat(handlerLatch.getCount()).isEqualTo(1);
        assertThat(serverBackend.metricsSnapshot().getOrDefault("consumed_events", 0L)).isEqualTo(0L);

        subscription.unsubscribe();
    }

    @Test
    @DisplayName("mutating event with same idempotency_key executes once")
    void mutatingDuplicateMessageExecutesOnce() throws InterruptedException {
        Config config = baseConfig("alpha");
        config.redisMutatingConsumeEnabled = true;

        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        AtomicInteger executions = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        Subscription<SocketEvents.LoadMapsV2> subscription = requesterBackend.subscribe(
                SocketEvents.LoadMapsV2.class,
                event -> {
                    executions.incrementAndGet();
                    latch.countDown();
                }
        );

        try (RedisClient client = RedisClient.create(config.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            long now = System.currentTimeMillis();
            long expires = now + 120_000;
            Map<String, String> fields = Map.ofEntries(
                    Map.entry("schema_version", "1"),
                    Map.entry("event_type", "maps.load"),
                    Map.entry("event_id", "evt-1"),
                    Map.entry("idempotency_key", "maps.load:test-key"),
                    Map.entry("producer", "discord-bot"),
                    Map.entry("created_at", String.valueOf(now)),
                    Map.entry("expires_at", String.valueOf(expires)),
                    Map.entry("server", "alpha"),
                    Map.entry("payload_json", "{\"urls\":[],\"server\":\"alpha\"}")
            );

            connection.sync().xadd("xcore:cmd:maps-load:alpha", fields);
            connection.sync().xadd("xcore:cmd:maps-load:alpha", fields);
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(500);
        assertThat(executions.get()).isEqualTo(1);

        subscription.unsubscribe();
    }

    @Test
    @DisplayName("failed consume routes message to DLQ after max attempts")
    void failedConsumeRoutesMessageToDlq() throws InterruptedException {
        Config config = baseConfig("alpha");
        config.redisDlqEnabled = true;
        config.redisMaxDeliveryAttempts = 1;

        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        CountDownLatch failureSeen = new CountDownLatch(1);
        Subscription<SocketEvents.MessageEvent> subscription = requesterBackend.subscribe(SocketEvents.MessageEvent.class, event -> {
            failureSeen.countDown();
            throw new IllegalStateException("intentional failure");
        });

        requesterBackend.send(new SocketEvents.MessageEvent("tester", "poison", "alpha"));
        assertThat(failureSeen.await(10, TimeUnit.SECONDS)).isTrue();

        try (RedisClient client = RedisClient.create(config.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            long deadline = System.currentTimeMillis() + 10000;
            boolean dlqFound = false;

            while (System.currentTimeMillis() < deadline && !dlqFound) {
                List<StreamMessage<String, String>> messages = connection.sync().xread(
                        XReadArgs.Builder.block(250).count(50),
                        XReadArgs.StreamOffset.from("xcore:dlq:evt", "0-0")
                );
                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (StreamMessage<String, String> message : messages) {
                    String sourceStream = message.getBody().get("source_stream");
                    if ("xcore:evt:chat:message".equals(sourceStream)) {
                        dlqFound = true;
                        break;
                    }
                }
            }

            assertThat(dlqFound).isTrue();
        }

        assertThat(requesterBackend.metricsSnapshot().getOrDefault("dlq_routed", 0L)).isGreaterThanOrEqualTo(1L);

        subscription.unsubscribe();
    }

    private Config baseConfig(String server) {
        Config config = new Config();
        config.server = server;
        config.redisUrl = "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
        config.redisShadowPublishEnabled = true;
        config.redisConsumeEnabled = true;
        config.redisReclaimEnabled = false;
        return config;
    }
}
