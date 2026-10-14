package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.database.migration.V7__AssignBanIds;
import org.xcore.plugin.model.BanData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class BanDataRepositoryIntegrationTest {
    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    private static MongoClient client;
    private MongoDatabase database;
    private BanDataRepository repository;
    private TomlSecretsConfig config;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(connectionString());
    }

    private static String connectionString() {
        return "mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017);
    }

    @AfterAll
    static void disconnect() {
        if (client != null) client.close();
    }

    @BeforeEach
    void setUp() {
        database = client.getDatabase("ban_identity_repository_test").withCodecRegistry(fromRegistries(
                getDefaultCodecRegistry(), fromProviders(PojoCodecProvider.builder().automatic(true).build())));
        database.drop();
        config = new TomlSecretsConfig();
        repository = new BanDataRepository(database, config);
        new V7__AssignBanIds().up(database);
        database.getCollection("players").insertOne(new Document("uuid", "target").append("pid", 567));
    }

    @Test
    void newBanHasIdentityAndInt32Fields() {
        var ban = BanData.builder().uuid("target").caseNumber(57).build();

        assertThat(repository.save(ban)).isTrue();

        var stored = database.getCollection("bans").find().first();
        assertThat(stored.get("ban_id")).isInstanceOf(Integer.class).isEqualTo(1);
        assertThat(stored.get("pid")).isInstanceOf(Integer.class).isEqualTo(567);
        assertThat(stored.get("case_number")).isInstanceOf(Integer.class).isEqualTo(57);
        assertThat(repository.findByBanId(1).pid).isEqualTo(567);
        assertThat(database.getCollection("counters").find(new Document("_id", "ban_id")).first()
                .get("seq")).isInstanceOf(Integer.class).isEqualTo(1);
    }

    @Test
    void longCounterStillProducesInt32BanIdentity() {
        database.getCollection("counters").insertOne(new Document("_id", "ban_id").append("seq", 100L));

        repository.save(BanData.builder().uuid("target").build());

        assertThat(database.getCollection("bans").find().first().get("ban_id"))
                .isInstanceOf(Integer.class).isEqualTo(101);
    }

    @Test
    void exhaustedInt32RangeDoesNotWriteALongBanId() {
        database.getCollection("counters").insertOne(new Document("_id", "ban_id").append("seq", Integer.MAX_VALUE));

        assertThatThrownBy(() -> repository.save(BanData.builder().uuid("target").build()))
                .isInstanceOf(ArithmeticException.class);
        assertThat(database.getCollection("bans").countDocuments()).isZero();
    }

    @Test
    void replacementBanGetsNewIdentity() {
        var first = BanData.builder().uuid("target").build();
        var second = BanData.builder().uuid("target").build();
        repository.save(first);

        repository.save(second);

        assertThat(second.banId).isGreaterThan(first.banId);
        assertThat(repository.findByBanId(first.banId)).isNull();
        assertThat(repository.findByBanId(second.banId).pid).isEqualTo(567);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void asyncSaveAllocatesIdentityAndPid() throws Exception {
        config.database.mongoConnectionString = connectionString();
        config.database.name = database.getName();
        var store = new ReactiveMongoStore(config);
        try {
            var reactiveRepository = new BanDataRepository(database, store, config);
            var first = BanData.builder().uuid("target").build();
            var second = BanData.builder().uuid("target").build();

            assertThat(reactiveRepository.saveAsync(first).toCompletableFuture().get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(reactiveRepository.saveAsync(second).toCompletableFuture().get(10, TimeUnit.SECONDS)).isTrue();

            assertThat(second.banId).isGreaterThan(first.banId);
            var stored = database.getCollection("bans").find().first();
            assertThat(stored.get("ban_id")).isInstanceOf(Integer.class).isEqualTo(second.banId);
            assertThat(stored.get("pid")).isInstanceOf(Integer.class).isEqualTo(567);
        } finally {
            store.close();
        }
    }

    @Test
    void concurrentAllocationsAcrossRepositoriesAreUnique() throws Exception {
        var peer = new BanDataRepository(database, config);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> allocations = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                var writer = i % 2 == 0 ? repository : peer;
                allocations.add(() -> {
                    start.await();
                    return writer.nextBanId();
                });
            }
            var results = allocations.stream().map(executor::submit).toList();
            start.countDown();
            List<Integer> ids = new ArrayList<>();
            for (var result : results) ids.add(result.get(10, TimeUnit.SECONDS));

            assertThat(ids).hasSize(100).doesNotHaveDuplicates().contains(1, 100);
        }
    }

    @Test
    void missingIdentityAndPidAreHealedWithoutReplacingTheRow() {
        database.getCollection("bans").insertOne(new Document("uuid", "target").append("reason", "legacy")
                .append("custom", "keep"));
        var ban = repository.find("target", null);

        repository.assignBanIdIfMissing(ban);

        assertThat(ban.banId).isPositive();
        assertThat(ban.pid).isEqualTo(567);
        assertThat(database.getCollection("bans").find().first().getString("custom")).isEqualTo("keep");
        repository.assignBanIdIfMissing(ban);
        assertThat(database.getCollection("counters").find().first().getInteger("seq")).isEqualTo(ban.banId);
    }

    @Test
    void staleReaderUsesTheWinningIdAndNeverOverwritesItsPid() {
        database.getCollection("bans").insertOne(new Document("uuid", "target"));
        var stale = repository.find("target", null);
        var winner = repository.find("target", null);
        repository.assignBanIdIfMissing(winner);
        database.getCollection("bans").updateOne(new Document("_id", winner.id),
                new Document("$set", new Document("pid", -7)));

        repository.assignBanIdIfMissing(stale);

        assertThat(stale.banId).isEqualTo(winner.banId);
        assertThat(stale.pid).isEqualTo(-7);
        assertThat(repository.find("target", null).banId).isEqualTo(winner.banId);
    }

    @Test
    void concurrentHealersAgreeOnTheStoredIdentity() throws Exception {
        database.getCollection("bans").insertOne(new Document("uuid", "target"));
        var first = repository.find("target", null);
        var second = repository.find("target", null);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = List.of(first, second).stream().map(ban -> executor.submit(() -> {
                start.await();
                repository.assignBanIdIfMissing(ban);
                return ban.banId;
            })).toList();
            start.countDown();

            for (var result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(repository.find("target", null).banId);
            }
        }
    }

    @Test
    void banAndUnbanAuditPersistTheBanIdAsAString() {
        var auditRepository = new AuditRecordRepository(database, config);
        var auditService = new org.xcore.plugin.service.moderation.DefaultAuditService(auditRepository,
                new org.xcore.plugin.config.TomlXcoreConfig());
        var service = new org.xcore.plugin.service.moderation.ModerationService(
                new PlayerDataRepository(database, config), repository,
                org.mockito.Mockito.mock(MuteDataRepository.class),
                org.mockito.Mockito.mock(org.xcore.plugin.session.SessionService.class),
                org.mockito.Mockito.mock(org.xcore.plugin.service.NetworkService.class),
                org.mockito.Mockito.mock(org.xcore.plugin.service.FindService.class),
                org.mockito.Mockito.mock(org.xcore.plugin.service.TimeService.class), auditService,
                new org.xcore.plugin.config.TomlXcoreConfig());
        var target = org.xcore.plugin.model.PlayerData.builder().uuid("target").pid(567).nickname("Target").build();

        var result = service.tempBanByUuidOrIp("target", null, "Target", java.time.Duration.ofHours(1),
                "reason", "console", null);
        assertThat(result.isSuccess()).isTrue();
        int banId = result.getData().orElseThrow().banId;
        assertThat(service.unbanPlayer(target, "console", null).isSuccess()).isTrue();

        var records = database.getCollection("moderation_audit").find().into(new ArrayList<>());
        assertThat(records).hasSize(2);
        assertThat(records.stream().map(record -> record.getString("action")).toList()).contains("BAN", "UNBAN");
        assertThat(records).allSatisfy(record -> {
            var extra = record.get("details", Document.class).get("extra", Document.class);
            assertThat(extra.get("ban_id")).isInstanceOf(String.class).isEqualTo(Integer.toString(banId));
        });
    }

    @Test
    void readOnlyHealingDoesNotAllocateOrWrite() {
        database.getCollection("bans").insertOne(new Document("uuid", "target"));
        config.database.readOnly = true;

        repository.assignBanIdIfMissing(repository.find("target", null));
        assertThat(repository.save(BanData.builder().uuid("other").build())).isFalse();

        assertThat(database.getCollection("counters").countDocuments()).isZero();
        assertThat(repository.find("target", null).banId).isNull();
    }
}
