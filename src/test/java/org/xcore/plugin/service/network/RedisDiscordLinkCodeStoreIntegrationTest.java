package org.xcore.plugin.service.network;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.concurrent.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class RedisDiscordLinkCodeStoreIntegrationTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static RedisClient client;
    static StatefulRedisConnection<String, String> connection;
    RedisDiscordLinkCodeStore store;

    @BeforeAll static void connect() {
        client = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        connection = client.connect();
    }
    @AfterAll static void close() { connection.close(); client.shutdown(); }
    @BeforeEach void setUp() {
        connection.sync().flushdb();
        var backend = mock(RedisNetworkBackend.class);
        doAnswer(call -> {
            Function<io.lettuce.core.api.async.RedisAsyncCommands<String, String>, CompletionStage<Object>> operation = call.getArgument(0);
            return operation.apply(connection.async());
        }).when(backend).withAsyncCommands(any(), any());
        var config = new TomlXcoreConfig();
        config.server.name = "issuer";
        store = new RedisDiscordLinkCodeStore(backend, RedisNetworkBackend.createRedisGson(), config);
    }
    private RedisDiscordLinkCodeStore.LinkCodePayload payload(String code, String uuid) {
        long now = System.currentTimeMillis();
        return new RedisDiscordLinkCodeStore.LinkCodePayload(code, uuid, 7, "Player", "issuer", now, now + 60_000L);
    }
    @Test void globalNxCollisionKeepsBothOwnersPreviousProof() {
        var first = payload("ABC123", "one");
        var second = payload("XYZ123", "two");
        assertThat(store.store(first)).isTrue();
        assertThat(store.store(second)).isTrue();
        assertThat(store.store(payload("ABC123", "two"))).isFalse();
        assertThat(store.findPendingByPlayerUuid("one")).isEqualTo(first);
        assertThat(store.findPendingByPlayerUuid("two")).isEqualTo(second);
    }
    @Test void concurrentClaimReturnsConfirmedPayloadExactlyOnce() throws Exception {
        var proof = payload("ABC123", "one");
        store.store(proof);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<RedisDiscordLinkCodeStore.LinkCodePayload>>();
            for (int i = 0; i < 20; i++) futures.add(pool.submit(() -> { start.await(); return store.claimCode(proof); }));
            start.countDown();
            int claimed = 0;
            for (var future : futures) {
                var result = future.get(5, TimeUnit.SECONDS);
                if (result != null) { assertThat(result).isEqualTo(proof); claimed++; }
            }
            assertThat(claimed).isEqualTo(1);
        }
        assertThat(connection.sync().get("xcore:discord-link:player:issuer:one")).isNull();
    }
    @Test void claimUsesPayloadServerAndDoesNotDeleteNewerReverseKey() {
        var proof = payload("ABC123", "one");
        store.store(proof);
        connection.sync().set("xcore:discord-link:player:issuer:one", "NEW123");
        connection.sync().set("xcore:discord-link:player:another:one", "ABC123");
        assertThat(store.claimCode(proof)).isEqualTo(proof);
        assertThat(connection.sync().get("xcore:discord-link:player:issuer:one")).isEqualTo("NEW123");
        assertThat(connection.sync().get("xcore:discord-link:player:another:one")).isEqualTo("ABC123");
    }
    @Test void readOnlyExecutorLeavesRealProofRedeemable() {
        var proof = payload("ABC123", "one");
        store.store(proof);
        var players = mock(org.xcore.plugin.database.repository.PlayerDataRepository.class);
        when(players.isReadOnly()).thenReturn(true);
        var config = new TomlXcoreConfig();
        config.server.name = "issuer";
        var service = new org.xcore.plugin.service.DiscordLinkService(store, players,
                mock(org.xcore.plugin.session.SessionService.class), mock(org.xcore.plugin.service.NetworkService.class), config,
                new org.xcore.plugin.concurrent.Async(mock(org.xcore.plugin.concurrent.StorageExecutor.class), Runnable::run));
        assertThat(service.confirmLink("ABC123", "one", 7, "123", "Discord").errorKey()).isEqualTo("read-only");
        assertThat(store.findByCode("ABC123")).isEqualTo(proof);
        assertThat(store.claimCode(proof)).isEqualTo(proof);
    }

    @Test void replacedPayloadCannotBeRedeemedByOldConfirmation() {
        var proof = payload("ABC123", "one");
        store.store(proof);
        var replacement = payload("ABC123", "two");
        connection.sync().set("xcore:discord-link:code:ABC123", RedisNetworkBackend.createRedisGson().toJson(replacement));
        assertThat(store.claimCode(proof)).isNull();
        assertThat(store.findByCode("ABC123")).isEqualTo(replacement);
    }
}
