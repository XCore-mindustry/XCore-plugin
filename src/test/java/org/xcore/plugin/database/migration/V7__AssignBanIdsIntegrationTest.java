package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.AuditRecordRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class V7__AssignBanIdsIntegrationTest {
    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    private static MongoClient client;
    private MongoDatabase database;
    private final V7__AssignBanIds migration = new V7__AssignBanIds();

    @BeforeAll
    static void connect() {
        client = MongoClients.create("mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017));
    }

    @AfterAll
    static void disconnect() {
        if (client != null) client.close();
    }

    @BeforeEach
    void setUp() {
        database = client.getDatabase("v7_migration_test");
        database.drop();
    }

    @Test
    void backfillsInIdOrderAndIsIdempotent() {
        database.getCollection("players").insertMany(List.of(
                new Document("uuid", "first").append("pid", 567),
                new Document("uuid", "second").append("pid", -7)));
        database.getCollection("bans").insertMany(List.of(
                new Document("_id", 2).append("uuid", "second"),
                new Document("_id", 1).append("uuid", "first"),
                new Document("_id", 3).append("ip", "1.2.3.4"),
                new Document("_id", 4).append("uuid", "first").append("ban_id", 40).append("pid", 99),
                new Document("_id", 5).append("uuid", "first").append("ban_id", 41)));
        database.getCollection("counters").insertOne(new Document("_id", "ban_id").append("seq", 41));

        migration.up(database);
        var before = bans();
        migration.up(database);

        assertThat(bans()).isEqualTo(before);
        assertThat(before.get(0).get("ban_id")).isInstanceOf(Integer.class).isEqualTo(42);
        assertThat(before.get(0).get("pid")).isInstanceOf(Integer.class).isEqualTo(567);
        assertThat(before.get(1).getInteger("ban_id")).isEqualTo(43);
        assertThat(before.get(1).getInteger("pid")).isEqualTo(-7);
        assertThat(before.get(2).getInteger("ban_id")).isEqualTo(44);
        assertThat(before.get(2)).doesNotContainKey("pid");
        assertThat(before.get(3).getInteger("ban_id")).isEqualTo(40);
        assertThat(before.get(3).getInteger("pid")).isEqualTo(99);
        assertThat(before.get(4).getInteger("pid")).isEqualTo(567);
        assertThat(database.getCollection("counters").find().first().getInteger("seq")).isEqualTo(44);
    }

    @Test
    void createsExactlyTheBotCompatibleIndexes() {
        migration.up(database);
        // Both the bot's index creation and the repository's startup indexes may run again.
        database.getCollection("bans").createIndex(new Document("ban_id", 1),
                new com.mongodb.client.model.IndexOptions().unique(true)
                        .partialFilterExpression(new Document("ban_id", new Document("$type", "int"))));
        new AuditRecordRepository(database, new TomlSecretsConfig());

        var banIndex = database.getCollection("bans").listIndexes().into(new ArrayList<>()).stream()
                .filter(index -> "ban_id_1".equals(index.getString("name"))).findFirst().orElseThrow();
        assertThat(banIndex.get("key", Document.class)).isEqualTo(new Document("ban_id", 1));
        assertThat(banIndex.getBoolean("unique")).isTrue();
        assertThat(banIndex.get("partialFilterExpression", Document.class))
                .isEqualTo(new Document("ban_id", new Document("$type", "int")));
        assertThat(banIndex).doesNotContainKey("sparse");
        var auditIndex = database.getCollection("moderation_audit").listIndexes().into(new ArrayList<>()).stream()
                .filter(index -> "details.extra.ban_id_1".equals(index.getString("name"))).findFirst().orElseThrow();
        assertThat(auditIndex.get("key", Document.class)).isEqualTo(new Document("details.extra.ban_id", 1));
        assertThat(auditIndex.getBoolean("sparse")).isTrue();
        assertThat(auditIndex).doesNotContainKeys("unique", "partialFilterExpression");
    }

    @Test
    void guardedUpdatesPreserveConcurrentWriterIdentityAndPid() {
        database.getCollection("players").insertOne(new Document("uuid", "target").append("pid", 567));
        database.getCollection("bans").insertOne(new Document("_id", 1).append("uuid", "target"));
        MongoDatabase hooked = mock(MongoDatabase.class);
        MongoCollection<Document> bans = spy(database.getCollection("bans"));
        when(hooked.getCollection("bans")).thenReturn(bans);
        when(hooked.getCollection("players")).thenReturn(database.getCollection("players"));
        when(hooked.getCollection("counters")).thenReturn(database.getCollection("counters"));
        when(hooked.getCollection("moderation_audit")).thenReturn(database.getCollection("moderation_audit"));
        var injected = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            if (!injected.compareAndSet(false, true)) return invocation.callRealMethod();
            var assigned = database.getCollection("counters").findOneAndUpdate(
                    new Document("_id", "ban_id"), new Document("$inc", new Document("seq", 1)),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            database.getCollection("bans").updateOne(new Document("_id", 1),
                    new Document("$set", new Document("ban_id", assigned.getInteger("seq")).append("pid", 99)));
            return invocation.callRealMethod();
        }).when(bans).updateOne(any(Bson.class), any(Bson.class));

        migration.up(hooked);

        assertThat(bans().getFirst().getInteger("ban_id")).isEqualTo(2);
        assertThat(bans().getFirst().getInteger("pid")).isEqualTo(99);
    }

    @Test
    void concurrentBotWriterUsesTheSameSequenceWithoutCollisions() throws Exception {
        for (int i = 0; i < 100; i++) {
            database.getCollection("bans").insertOne(new Document("_id", i).append("uuid", "legacy-" + i));
        }
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var migrationResult = executor.submit(() -> {
                start.await();
                migration.up(database);
                return true;
            });
            var writerResult = executor.submit(() -> {
                start.await();
                for (int i = 100; i < 200; i++) {
                    var counter = database.getCollection("counters").findOneAndUpdate(
                            new Document("_id", "ban_id"), new Document("$inc", new Document("seq", 1)),
                            new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
                    database.getCollection("bans").insertOne(new Document("_id", i)
                            .append("uuid", "bot-" + i).append("ban_id", counter.getInteger("seq")));
                }
                return true;
            });
            start.countDown();
            assertThat(migrationResult.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(writerResult.get(30, TimeUnit.SECONDS)).isTrue();
        }
        migration.up(database);

        assertThat(bans()).hasSize(200);
        assertThat(bans().stream().map(ban -> ban.getInteger("ban_id")).toList())
                .doesNotHaveDuplicates().doesNotContainNull();
        assertThat(database.getCollection("counters").find().first().getInteger("seq")).isEqualTo(200);
    }

    @Test
    void runsThroughTheExistingMigrationLockAndVersionRegistration() {
        var config = new TomlSecretsConfig();
        config.database.migrationEnabled = true;
        var service = new MigrationService(database, config, new TomlXcoreConfig(), List.of(migration));

        assertThat(service.run()).isTrue();
        assertThat(service.run()).isTrue();

        assertThat(database.getCollection("settings").find(new Document("_id", "db_version"))
                .first().getInteger("version")).isEqualTo(7);
        assertThat(migration.getVersion()).isEqualTo(7);
    }

    private List<Document> bans() {
        return database.getCollection("bans").find().sort(new Document("_id", 1)).into(new ArrayList<>());
    }
}
