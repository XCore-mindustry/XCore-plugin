package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import org.xcore.plugin.service.network.RedisNetworkBackend.RequestSubscription;
import org.xcore.plugin.service.network.RedisNetworkBackend.Subscription;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatGlobalV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatMessageV1;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveResponseV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationMuteCreatedV1;
import org.xcore.protocol.generated.shared.MapEntryV1;
import org.xcore.protocol.generated.shared.VoteKickParticipantV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.Punishment;

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
    @DisplayName("send rejects unsupported payloads without publishing any fallback stream")
    void sendRejectsUnsupportedPayloadsWithoutPublishingAnyFallbackStream() {
        Config config = baseConfig("alpha");
        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        requesterBackend.send(new Object());

        assertThat(requesterBackend.metricsSnapshot().getOrDefault("publish_failures", 0L)).isEqualTo(1L);
        assertThat(requesterBackend.metricsSnapshot().getOrDefault("published_events", 0L)).isZero();

        try (RedisClient client = RedisClient.create(config.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            List<StreamMessage<String, String>> messages = connection.sync().xread(
                    XReadArgs.StreamOffset.from("xcore:evt:raw", "0-0")
            );

            assertThat(messages).isEmpty();
        }
    }

    @Test
    @DisplayName("send publishes envelope to mapped stream")
    void sendPublishesEnvelopeToMappedStream() {
        Config config = baseConfig("alpha");
        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        requesterBackend.send(new ChatMessageV1("tester", "hello", "alpha"));

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
    @DisplayName("global chat is delivered to subscribers on different servers")
    void globalChatDeliveredAcrossServers() throws InterruptedException {
        Config alphaConfig = baseConfig("alpha");
        Config betaConfig = baseConfig("beta");

        serverBackend = new RedisNetworkBackend(alphaConfig);
        requesterBackend = new RedisNetworkBackend(betaConfig);
        serverBackend.connect();
        requesterBackend.connect();

        CountDownLatch alphaLatch = new CountDownLatch(1);
        CountDownLatch betaLatch = new CountDownLatch(1);
        AtomicReference<ChatGlobalV1> alphaReceived = new AtomicReference<>();
        AtomicReference<ChatGlobalV1> betaReceived = new AtomicReference<>();

        Subscription<ChatGlobalV1> alphaSubscription = serverBackend.subscribe(
                ChatGlobalV1.class,
                event -> {
                    alphaReceived.set(event);
                    alphaLatch.countDown();
                }
        );
        Subscription<ChatGlobalV1> betaSubscription = requesterBackend.subscribe(
                ChatGlobalV1.class,
                event -> {
                    betaReceived.set(event);
                    betaLatch.countDown();
                }
        );

        serverBackend.send(new ChatGlobalV1("player", "hello world", "alpha"));

        assertThat(alphaLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(betaLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(alphaReceived.get()).isNotNull();
        assertThat(betaReceived.get()).isNotNull();
        assertThat(alphaReceived.get().message()).isEqualTo("hello world");
        assertThat(betaReceived.get().message()).isEqualTo("hello world");

        alphaSubscription.unsubscribe();
        betaSubscription.unsubscribe();
    }

    @Test
    @DisplayName("execute command broadcast is delivered to all servers")
    void executeCommandBroadcastDeliveredAcrossServers() throws InterruptedException {
        Config alphaConfig = baseConfig("alpha");
        Config betaConfig = baseConfig("beta");

        serverBackend = new RedisNetworkBackend(alphaConfig);
        requesterBackend = new RedisNetworkBackend(betaConfig);
        serverBackend.connect();
        requesterBackend.connect();

        CountDownLatch alphaLatch = new CountDownLatch(1);
        CountDownLatch betaLatch = new CountDownLatch(1);
        AtomicReference<TransportEvents.ExecuteCommand> alphaReceived = new AtomicReference<>();
        AtomicReference<TransportEvents.ExecuteCommand> betaReceived = new AtomicReference<>();

        Subscription<TransportEvents.ExecuteCommand> alphaSubscription = serverBackend.subscribe(
                TransportEvents.ExecuteCommand.class,
                event -> {
                    alphaReceived.set(event);
                    alphaLatch.countDown();
                }
        );
        Subscription<TransportEvents.ExecuteCommand> betaSubscription = requesterBackend.subscribe(
                TransportEvents.ExecuteCommand.class,
                event -> {
                    betaReceived.set(event);
                    betaLatch.countDown();
                }
        );

        serverBackend.send(new TransportEvents.ExecuteCommand("status", new String[0], false));

        assertThat(alphaLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(betaLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(alphaReceived.get()).isNotNull();
        assertThat(betaReceived.get()).isNotNull();
        assertThat(alphaReceived.get().command()).isEqualTo("status");
        assertThat(betaReceived.get().command()).isEqualTo("status");

        alphaSubscription.unsubscribe();
        betaSubscription.unsubscribe();
    }

    @Test
    @DisplayName("send serializes canonical moderation ban created event on primary route")
    void sendSerializesBanDataInstant() {
        Config config = baseConfig("alpha");
        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        BanData banData = punishment(new BanData(), "u-1", "player");
        banData.ip = "1.2.3.4";
        requesterBackend.send(org.xcore.plugin.service.network.ModerationProtocolMapper.toBanCreated(
                banData,
                "alpha",
                Instant.parse("2026-04-26T00:00:00Z")
        ));

        assertThat(requesterBackend.metricsSnapshot().getOrDefault("publish_failures", 0L)).isEqualTo(0L);

        try (RedisClient client = RedisClient.create(config.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            List<StreamMessage<String, String>> messages = connection.sync().xread(
                    XReadArgs.StreamOffset.from("xcore:evt:moderation:ban", "0-0")
            );

            assertThat(messages).isNotEmpty();
            var last = messages.get(messages.size() - 1).getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new Gson().fromJson(last.get("payload_json"), Map.class);
            assertThat(last.get("event_type")).isEqualTo("moderation.ban.created");
            assertThat(payload)
                    .containsEntry("messageType", ModerationMessages.ModerationBanCreatedV1.MESSAGE_TYPE)
                    .containsEntry("messageVersion", 1.0)
                    .containsEntry("reason", "rule")
                    .containsEntry("server", "alpha")
                    .containsEntry("occurredAt", "2026-04-26T00:00:00Z")
                    .containsKeys("target", "actor", "expiration")
                    .doesNotContainKeys("uuid", "name", "adminName", "expireDate");
        }
    }

    @Test
    @DisplayName("send serializes canonical moderation mute created event")
    void sendSerializesCanonicalModerationMuteCreatedEvent() {
        Config config = baseConfig("alpha");
        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        MuteData muteData = punishment(new MuteData(), "u-1", "player");
        var canonicalEvent = org.xcore.plugin.service.network.ModerationProtocolMapper.toMuteCreated(
                muteData,
                "alpha",
                Instant.parse("2026-04-26T00:00:00Z")
        );
        requesterBackend.send(canonicalEvent);

        assertThat(requesterBackend.metricsSnapshot().getOrDefault("publish_failures", 0L)).isEqualTo(0L);

        try (RedisClient client = RedisClient.create(config.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            List<StreamMessage<String, String>> messages = connection.sync().xread(
                    XReadArgs.StreamOffset.from("xcore:evt:moderation:mute", "0-0")
            );

            assertThat(messages).isNotEmpty();
            var last = messages.get(messages.size() - 1).getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new Gson().fromJson(last.get("payload_json"), Map.class);
            assertThat(last.get("event_type")).isEqualTo("moderation.mute.created");
            assertThat(payload)
                    .containsEntry("messageType", ModerationMessages.ModerationMuteCreatedV1.MESSAGE_TYPE)
                    .containsEntry("messageVersion", 1.0)
                    .containsEntry("reason", "rule")
                    .containsEntry("server", "alpha")
                    .containsEntry("occurredAt", "2026-04-26T00:00:00Z")
                    .containsKeys("target", "actor", "expiration")
                    .doesNotContainKeys("uuid", "name", "adminName", "expireDate");
        }
    }

    @Test
    @DisplayName("subscribe consumes canonical moderation ban created event from primary route")
    void subscribeConsumesCanonicalModerationBanCreatedEvent() throws InterruptedException {
        Config config = baseConfig("alpha");
        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ModerationBanCreatedV1> received = new AtomicReference<>();

        Subscription<ModerationBanCreatedV1> subscription = requesterBackend.subscribe(
                ModerationBanCreatedV1.class,
                event -> {
                    received.set(event);
                    latch.countDown();
                }
        );
        
        BanData banData = punishment(new BanData(), "u-1", "player");
        banData.ip = "1.2.3.4";
        requesterBackend.send(org.xcore.plugin.service.network.ModerationProtocolMapper.toBanCreated(
                banData,
                "alpha",
                Instant.parse("2026-04-26T00:00:00Z")
        ));

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(ModerationBanCreatedV1.MESSAGE_TYPE).isEqualTo(ModerationMessages.ModerationBanCreatedV1.MESSAGE_TYPE);
        assertThat(ModerationMessages.ModerationBanCreatedV1.MESSAGE_VERSION).isEqualTo(1);
        assertThat(received.get().target().playerUuid()).isEqualTo("u-1");
        assertThat(received.get().server()).isEqualTo("alpha");

        subscription.unsubscribe();
    }

    @Test
    @DisplayName("send serializes canonical vote-kick event to moderation votekick stream")
    void sendSerializesVoteKickEvent() {
        Config config = baseConfig("alpha");
        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        requesterBackend.send(org.xcore.plugin.service.network.ModerationProtocolMapper.toVoteKickCreated(
                "uuid-target",
                42,
                "Target",
                "Starter",
                7,
                "123456",
                "griefing",
                List.of(org.xcore.plugin.service.network.ModerationProtocolMapper.toVoteKickParticipant("Starter", 7, "123456")),
                List.of(org.xcore.plugin.service.network.ModerationProtocolMapper.toVoteKickParticipant("Voter2", 8, "654321")),
                "alpha",
                Instant.parse("2026-04-26T00:00:00Z")
        ));

        assertThat(requesterBackend.metricsSnapshot().getOrDefault("publish_failures", 0L)).isEqualTo(0L);

        try (RedisClient client = RedisClient.create(config.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            List<StreamMessage<String, String>> messages = connection.sync().xread(
                    XReadArgs.StreamOffset.from("xcore:evt:moderation:votekick", "0-0")
            );

            assertThat(messages).isNotEmpty();
            var last = messages.get(messages.size() - 1).getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new Gson().fromJson(last.get("payload_json"), Map.class);
            assertThat(last.get("event_type")).isEqualTo("moderation.vote-kick.created");
            assertThat(payload)
                    .containsEntry("messageType", ModerationMessages.ModerationVoteKickCreatedV1.MESSAGE_TYPE)
                    .containsEntry("messageVersion", 1.0)
                    .containsEntry("reason", "griefing")
                    .containsEntry("server", "alpha")
                    .containsEntry("occurredAt", "2026-04-26T00:00:00Z")
                    .containsKeys("target", "starter", "votesFor", "votesAgainst");
        }
    }

    @Test
    @DisplayName("subscribe consumes read-only stream messages")
    void subscribeConsumesReadOnlyStreamMessages() throws InterruptedException {
        Config config = baseConfig("alpha");

        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatMessageV1> received = new AtomicReference<>();

        Subscription<ChatMessageV1> subscription = requesterBackend.subscribe(ChatMessageV1.class, event -> {
            received.set(event);
            latch.countDown();
        });

        requesterBackend.send(new ChatMessageV1("tester", "bridge", "alpha"));

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().message()).isEqualTo("bridge");
        assertThat(requesterBackend.metricsSnapshot().getOrDefault("consumed_events", 0L)).isGreaterThanOrEqualTo(1L);

        subscription.unsubscribe();
    }

    @Test
    @DisplayName("kick-banned canonical command subscribe works")
    void kickBannedSubscribeWorks() throws InterruptedException {
        Config config = baseConfig("alpha");

        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ModerationKickBannedCommandV1> received = new AtomicReference<>();

        Subscription<ModerationKickBannedCommandV1> subscription = requesterBackend.subscribe(
                ModerationKickBannedCommandV1.class,
                event -> {
                    received.set(event);
                    latch.countDown();
                }
        );

        requesterBackend.send(org.xcore.plugin.service.network.ModerationProtocolMapper.toKickBannedCommand(
                "uuid-a",
                null,
                "Unknown",
                "1.2.3.4",
                "alpha",
                Instant.parse("2026-04-26T00:00:00Z")
        ));

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().target().playerUuid()).isEqualTo("uuid-a");
        assertThat(received.get().target().ip()).isEqualTo("1.2.3.4");
        assertThat(received.get().server()).isEqualTo("alpha");

        subscription.unsubscribe();
    }

    @Test
    @DisplayName("rpc request/response roundtrip works for maps list")
    void rpcRequestResponseRoundtripWorks() throws InterruptedException {
        Config serverConfig = baseConfig("target");

        Config requesterConfig = baseConfig("discord");

        serverBackend = new RedisNetworkBackend(serverConfig);
        requesterBackend = new RedisNetworkBackend(requesterConfig);
        serverBackend.connect();
        requesterBackend.connect();

        Subscription<MapsListRequestV1> serverSubscription =
                serverBackend.subscribe(MapsListRequestV1.class,
                        request -> serverBackend.respond(
                                request,
                                mapsListResponse(
                                        mapEntry("A", "a.msav", "author-a", 100, 120, 1024L, 3, 1, 2, 4.5, 1.5, "pvp"),
                                        mapEntry("B", "b.msav", "author-b", 80, 80, 2048L)
                                )
                        ));

        CountDownLatch responseLatch = new CountDownLatch(1);
        CountDownLatch timeoutLatch = new CountDownLatch(1);
        AtomicReference<MapsListResponseV1> responseRef = new AtomicReference<>();

        RequestSubscription<MapsListResponseV1> requestHandle = requesterBackend.request(mapsListRequest("target"), response -> {
            responseRef.set(response);
            responseLatch.countDown();
        }, timeoutLatch::countDown);

        assertThat(requestHandle).isNotNull();
        assertThat(responseLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(timeoutLatch.getCount()).isEqualTo(1);
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().maps()).extracting(MapEntryV1::name).containsExactly("A", "B");
        assertThat(responseRef.get().maps()).extracting(MapEntryV1::like).containsExactly(3, null);
        assertThat(responseRef.get().maps()).extracting(MapEntryV1::reputation).containsExactly(2, null);
        assertThat(responseRef.get().maps()).extracting(MapEntryV1::gameMode).containsExactly("pvp", null);
        assertThat(requesterBackend.metricsSnapshot().getOrDefault("rpc_requests", 0L)).isGreaterThanOrEqualTo(1L);
        assertThat(serverBackend.metricsSnapshot().getOrDefault("rpc_responses", 0L)).isGreaterThanOrEqualTo(1L);

        serverSubscription.unsubscribe();
    }

    @Test
    @DisplayName("rpc dispatch filters by rpc_type and does not cross-trigger handlers")
    void rpcDispatchDoesNotCrossTriggerHandlers() throws InterruptedException {
        Config serverConfig = baseConfig("target");

        Config requesterConfig = baseConfig("discord");

        serverBackend = new RedisNetworkBackend(serverConfig);
        requesterBackend = new RedisNetworkBackend(requesterConfig);
        serverBackend.connect();
        requesterBackend.connect();

        CountDownLatch listLatch = new CountDownLatch(1);
        Subscription<MapsListRequestV1> listSubscription =
                serverBackend.subscribe(MapsListRequestV1.class, request -> {
                    listLatch.countDown();
                    serverBackend.respond(
                            request,
                            mapsListResponse(
                                    mapEntry("A", "a.msav", "author-a", 100, 120, 1024L, 3, 1, 2, 4.5, 1.5, "pvp"),
                                    mapEntry("B", "b.msav", "author-b", 80, 80, 2048L)
                            )
                    );
                });

        Subscription<MapsRemoveRequestV1> removeSubscription =
                serverBackend.subscribe(MapsRemoveRequestV1.class,
                        request -> serverBackend.respond(request, mapRemoveResponse(request.server(), "Removed")));

        CountDownLatch responseLatch = new CountDownLatch(1);
        CountDownLatch timeoutLatch = new CountDownLatch(1);
        AtomicReference<MapsRemoveResponseV1> responseRef = new AtomicReference<>();

        RequestSubscription<MapsRemoveResponseV1> requestHandle = requesterBackend.request(mapRemoveRequest("target", "MapX"), response -> {
            responseRef.set(response);
            responseLatch.countDown();
        }, timeoutLatch::countDown);

        assertThat(requestHandle).isNotNull();
        assertThat(responseLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(timeoutLatch.getCount()).isEqualTo(1);
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().result()).isEqualTo("Removed");
        assertThat(listLatch.getCount()).isEqualTo(1);

        listSubscription.unsubscribe();
        removeSubscription.unsubscribe();
    }

    @Test
    @DisplayName("expired rpc request is ACKed and dropped without handler execution")
    void expiredRpcRequestIsDropped() throws InterruptedException {
        Config serverConfig = baseConfig("target");

        serverBackend = new RedisNetworkBackend(serverConfig);
        serverBackend.connect();

        CountDownLatch handlerLatch = new CountDownLatch(1);
        Subscription<MapsListRequestV1> subscription =
                serverBackend.subscribe(MapsListRequestV1.class, request -> handlerLatch.countDown());

        try (RedisClient client = RedisClient.create(serverConfig.redisUrl);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            long now = System.currentTimeMillis();
            connection.sync().xadd("xcore:rpc:req:target", java.util.Map.ofEntries(
                    java.util.Map.entry("schema_version", "1"),
                    java.util.Map.entry("rpc_type", "maps.list.request"),
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

        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        AtomicInteger executions = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        Subscription<TransportEvents.LoadMapsV2> subscription = requesterBackend.subscribe(
                TransportEvents.LoadMapsV2.class,
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
        Subscription<ChatMessageV1> subscription = requesterBackend.subscribe(ChatMessageV1.class, event -> {
            failureSeen.countDown();
            throw new IllegalStateException("intentional failure");
        });

        requesterBackend.send(new ChatMessageV1("tester", "poison", "alpha"));
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

    @Test
    @DisplayName("unsubscribe stops subscriber lifecycle threads")
    void unsubscribeStopsSubscriberLifecycleThreads() {
        Config config = baseConfig("alpha");
        config.redisReclaimEnabled = true;

        requesterBackend = new RedisNetworkBackend(config);
        requesterBackend.connect();

        Subscription<ChatMessageV1> subscription = requesterBackend.subscribe(ChatMessageV1.class, event -> {
        });

        assertThat(requesterBackend.metricsSnapshot().getOrDefault("active_subscriber_threads", 0L)).isEqualTo(2L);
        assertThat(subscription.unsubscribe()).isTrue();

        waitForMetricValue("active_subscriber_threads", 0L, requesterBackend, 5);
        assertThat(subscription.unsubscribe()).isFalse();
    }

    @Test
    @DisplayName("request cancel stops rpc await lifecycle")
    void requestCancelStopsRpcAwaitLifecycle() {
        Config requesterConfig = baseConfig("discord");

        requesterBackend = new RedisNetworkBackend(requesterConfig);
        requesterBackend.connect();

        AtomicInteger responses = new AtomicInteger();
        AtomicInteger timeouts = new AtomicInteger();

        RequestSubscription<MapsListResponseV1> requestHandle = requesterBackend.request(
                mapsListRequest("target"),
                response -> responses.incrementAndGet(),
                timeouts::incrementAndGet
        );

        assertThat(requestHandle).isNotNull();
        requestHandle.cancel();

        waitForMetricValue("active_request_handles", 0L, requesterBackend, 5);
        assertThat(responses.get()).isZero();
        assertThat(timeouts.get()).isZero();
    }

    private static void waitForMetricValue(String key, long expectedValue, RedisNetworkBackend backend, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (backend.metricsSnapshot().getOrDefault(key, -1L) == expectedValue) {
                return;
            }

            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        assertThat(backend.metricsSnapshot().getOrDefault(key, -1L)).isEqualTo(expectedValue);
    }

    private Config baseConfig(String server) {
        Config config = new Config();
        config.server = server;
        config.redisUrl = "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
        config.redisReclaimEnabled = false;
        return config;
    }

    private static <T extends Punishment> T punishment(T value, String uuid, String name) {
        value.uuid = uuid;
        value.name = name;
        value.adminName = "admin";
        value.reason = "rule";
        value.expireDate = Instant.now().plusSeconds(3600);
        return value;
    }

    private static MapsListRequestV1 mapsListRequest(String server) {
        return new MapsListRequestV1(server);
    }

    private static MapsRemoveRequestV1 mapRemoveRequest(String server, String fileName) {
        return new MapsRemoveRequestV1(server, fileName);
    }

    private static MapsListResponseV1 mapsListResponse(MapEntryV1... entries) {
        return new MapsListResponseV1("target", List.of(entries));
    }

    private static MapsRemoveResponseV1 mapRemoveResponse(String server, String result) {
        return new MapsRemoveResponseV1(server, result);
    }

    private static MapEntryV1 mapEntry(
            String name,
            String fileName,
            String author,
            Integer width,
            Integer height,
            Long fileSizeBytes
    ) {
        return mapEntry(name, fileName, author, width, height, fileSizeBytes, null, null, null, null, null, null);
    }

    private static MapEntryV1 mapEntry(
            String name,
            String fileName,
            String author,
            Integer width,
            Integer height,
            Long fileSizeBytes,
            Integer like,
            Integer dislike,
            Integer reputation,
            Double popularity,
            Double interest,
            String gameMode
    ) {
        Integer fileSize = fileSizeBytes == null ? null : Math.toIntExact(fileSizeBytes);
        return new MapEntryV1(name, fileName, author, width, height, fileSize, like, dislike, reputation, popularity, interest, gameMode);
    }
}
