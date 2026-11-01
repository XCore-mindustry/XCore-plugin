package org.xcore.plugin.database.repository;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.model.PlayerData;

import java.util.concurrent.*;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PlayerAccountPersistenceIntegrationTest {
    @Container static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0").withExposedPorts(27017);
    static MongoClient client;
    MongoDatabase database;
    PlayerDataRepository players;
    ReactiveMongoStore reactive;
    @BeforeAll static void connect() { client = MongoClients.create(uri()); }
    static String uri() { return "mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017); }
    @AfterAll static void close() { client.close(); }
    @BeforeEach void setUp() {
        database = client.getDatabase("account_persistence_test").withCodecRegistry(fromRegistries(
                getDefaultCodecRegistry(), fromProviders(PojoCodecProvider.builder().automatic(true).build())));
        database.drop();
        var config = new TomlSecretsConfig();
        config.database.mongoConnectionString = uri();
        config.database.name = database.getName();
        reactive = new ReactiveMongoStore(config);
        players = new PlayerDataRepository(database, reactive, config);
    }
    @AfterEach void closeReactive() { reactive.close(); }
    @Test void freshSyncAndAsyncProfilesNeedNoStoredDiscordDefaults() throws Exception {
        var first = PlayerData.builder().uuid("first").pid(7).discordId("untrusted-cache").discordLinkedAt(99L).build();
        var second = PlayerData.builder().uuid("second").pid(8).discordId("untrusted-cache").discordLinkedAt(99L).build();
        assertThat(players.save(first)).isTrue();
        assertThat(players.saveAsync(second).toCompletableFuture().get(5, TimeUnit.SECONDS)).isTrue();
        for (String uuid : java.util.List.of("first", "second")) {
            Document raw = database.getCollection("players").find(new Document("uuid", uuid)).first();
            assertThat(raw.keySet()).noneMatch(field -> field.startsWith("discord_"));
            var loaded = players.findByUuid(uuid);
            assertThat(loaded.discordId).isEmpty();
            assertThat(loaded.discordUsername).isEmpty();
            assertThat(loaded.discordLinkedAt).isZero();
            assertThat(players.updateDiscordLink(uuid, "123", "Discord", 50L)).isTrue();
        }
    }

    @Test void explicitMergeRenamesSourceWithoutDuplicatingItAndWritesBinding() {
        var source = PlayerData.builder().uuid("source").pid(7).password("retired-password-hash").build();
        var target = PlayerData.builder().uuid("target").pid(8).build();
        players.save(source);
        players.save(target);
        players.updateDiscordLink("source", "123", "Discord", 50L);
        source = players.findByUuid("source");
        var sourceBefore = players.findByUuid("source");
        var targetBefore = players.findByUuid("target");
        target.discordId = source.discordId;
        target.discordUsername = source.discordUsername;
        target.discordLinkedAt = source.discordLinkedAt;
        source.uuid = "merged:source";
        source.password = null;
        source.discordId = "";
        source.discordUsername = "";
        source.discordLinkedAt = 0L;
        assertThat(players.saveMergedPlayer(null, sourceBefore, source)).isTrue();
        assertThat(players.saveMergedPlayer(null, targetBefore, target)).isTrue();
        assertThat(players.count()).isEqualTo(2);
        assertThat(players.findByUuid("source")).isNull();
        assertThat(players.findByUuid("merged:source").discordId).isEmpty();
        assertThat(players.findByUuid("merged:source").password).isEmpty();
        assertThat(players.findByUuid("target").discordId).isEqualTo("123");
        assertThat(players.findByUuid("target").discordLinkedAt).isEqualTo(50L);
        source.discordId = "stale";
        target.discordId = "stale";
        assertThat(players.save(source)).isTrue();
        assertThat(players.save(target)).isTrue();
        assertThat(players.findByUuid("merged:source").discordId).isEmpty();
        assertThat(players.findByUuid("merged:source").password).isEmpty();
        assertThat(players.findByUuid("target").discordId).isEqualTo("123");
    }

    @Test void staleMergeSnapshotIsRejectedForSourceAndTarget() {
        players.save(PlayerData.builder().uuid("source").pid(7).build());
        players.save(PlayerData.builder().uuid("target").pid(8).build());
        // Legacy target document without any discord fields still matches an unlinked snapshot.
        database.getCollection("players").updateOne(new Document("uuid", "target"),
                new Document("$unset", new Document("discord_id", "").append("discord_linked_at", "")));
        var sourceBefore = players.findByUuid("source");
        var targetBefore = players.findByUuid("target");

        // Source linked after the snapshot: merge must not wipe the new binding.
        players.updateDiscordLink("source", "123", "Discord", 50L);
        var retired = players.findByUuid("source");
        retired.uuid = "merged:source";
        assertThat(players.saveMergedPlayer(null, sourceBefore, retired)).isFalse();
        assertThat(players.findByUuid("source").discordId).isEqualTo("123");

        // Target linked after the snapshot: merge must not overwrite it with the stale (empty) binding.
        var staleTarget = players.findByUuid("target");
        assertThat(players.saveMergedPlayer(null, targetBefore, staleTarget)).isTrue();
        players.updateDiscordLink("target", "456", "Other", 60L);
        assertThat(players.saveMergedPlayer(null, targetBefore, staleTarget)).isFalse();
        assertThat(players.findByUuid("target").discordId).isEqualTo("456");

        // Source unlinked after a linked snapshot: merge must not restore the old binding.
        var linkedBefore = players.findByUuid("source");
        assertThat(players.clearDiscordLink("source", "123", linkedBefore.discordLinkedAt)).isTrue();
        assertThat(players.saveMergedPlayer(null, linkedBefore, linkedBefore)).isFalse();
        assertThat(players.findByUuid("source").discordId).isEmpty();
    }

    @Test void staleSnapshotOfRetiredAccountNeitherRevivesNorDuplicates() throws Exception {
        var cached = PlayerData.builder().uuid("source").pid(7).build();
        assertThat(players.save(cached)).isTrue();
        var before = players.findByUuid("source");
        var retired = players.findByUuid("source");
        retired.uuid = "merged:source";
        assertThat(players.saveMergedPlayer(null, before, retired)).isTrue();

        cached.totalPlayTime = 99;
        assertThat(players.save(cached)).isFalse();
        assertThat(players.saveAsync(cached).toCompletableFuture().get(5, TimeUnit.SECONDS)).isFalse();
        assertThat(players.count()).isEqualTo(1);
        assertThat(players.findByUuid("source")).isNull();
        assertThat(players.findByUuid("merged:source").totalPlayTime).isZero();
    }

    @Test void staleSyncAndAsyncSavesPreserveLinkAndUnlink() throws Exception {
        var cached = PlayerData.builder().uuid("one").pid(7).nickname("Player").build();
        players.save(cached);
        assertThat(cached.id).isNotNull();
        assertThat(players.updateDiscordLink("one", "123", "Discord", 50L)).isTrue();
        cached.totalPlayTime = 42;
        assertThat(players.save(cached)).isTrue();
        assertThat(players.findByUuid("one").discordId).isEqualTo("123");
        cached.discordId = "foreign";
        cached.discordLinkedAt = 99L;
        cached.totalPlayTime = 88;
        assertThat(players.saveAsync(cached).toCompletableFuture().get(5, TimeUnit.SECONDS)).isTrue();
        var stored = players.findByUuid("one");
        assertThat(stored.discordId).isEqualTo("123");
        assertThat(stored.discordLinkedAt).isEqualTo(50L);
        assertThat(stored.totalPlayTime).isEqualTo(88);
        assertThat(players.clearDiscordLink("one", "123", 50L)).isTrue();
        players.save(cached);
        players.saveAsync(cached).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(players.findByUuid("one").discordId).isEmpty();
    }
    @Test void ownerAndGenerationFiltersRejectForeignAndStaleUnlinks() {
        players.save(PlayerData.builder().uuid("one").pid(7).build());
        players.updateDiscordLink("one", "123", "Discord", 50L);
        assertThat(players.clearDiscordLink("one", "foreign", 50L)).isFalse();
        assertThat(players.clearDiscordLink("one", "123", 49L)).isFalse();
        assertThat(players.clearDiscordLink("one", "123", 50L)).isTrue();
        assertThat(players.updateDiscordLink("one", "123", "Discord", 100L)).isTrue();
        assertThat(players.clearDiscordLink("one", "123", 50L)).isFalse();
        assertThat(players.findByUuid("one").discordLinkedAt).isEqualTo(100L);
    }
    @Test void sameMillisecondRelinkHasNewGeneration() {
        players.save(PlayerData.builder().uuid("one").pid(7).build());
        var first = players.updateDiscordLink("one", 7, "123", "$Discord", 50L);
        assertThat(first.discordUsername).isEqualTo("$Discord");
        assertThat(players.clearDiscordLink("one", "123", first.discordLinkedAt)).isTrue();
        var second = players.updateDiscordLink("one", 7, "123", "$Discord", 50L);
        assertThat(second.discordLinkedAt).isGreaterThan(first.discordLinkedAt);
        assertThat(players.clearDiscordLink("one", "123", first.discordLinkedAt)).isFalse();
    }

    @Test void legacyMissingGenerationCanBeUnlinkedOnlyByOwner() {
        database.getCollection("players").insertOne(new Document("uuid", "one").append("pid", 7).append("discord_id", "123"));
        assertThat(players.clearDiscordLink("one", "foreign", 0L)).isFalse();
        assertThat(players.clearDiscordLink("one", "123", 0L)).isTrue();
    }
    @Test void receiptClaimIsExclusiveAndKnownResultReplays() throws Exception {
        var receipts = new AccountOperationRepository(database, new TomlSecretsConfig());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var calls = new java.util.ArrayList<Future<AccountOperationRepository.Claim>>();
            for (int i = 0; i < 12; i++) calls.add(pool.submit(() -> { start.await(); return receipts.claim("intent", "payload"); }));
            start.countDown();
            int acquired = 0;
            for (var call : calls) if (call.get(5, TimeUnit.SECONDS).acquired()) acquired++;
            assertThat(acquired).isEqualTo(1);
        }
        assertThat(receipts.claim("intent", "payload").response()).isNull();
        assertThat(receipts.complete("intent", "payload", "known-result")).isTrue();
        assertThat(receipts.claim("intent", "payload").response()).isEqualTo("known-result");
        assertThat(receipts.claim("intent", "changed-payload").conflict()).isTrue();
    }
}
