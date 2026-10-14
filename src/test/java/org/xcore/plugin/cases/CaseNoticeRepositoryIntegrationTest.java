package org.xcore.plugin.cases;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CaseNoticeRepositoryIntegrationTest {
    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0").withExposedPorts(27017);
    private static MongoClient client;
    private MongoDatabase database;
    private TomlSecretsConfig config;
    private CaseNoticeRepository repository;

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
        database = client.getDatabase("case_notice_p2_test");
        database.drop();
        config = new TomlSecretsConfig();
        config.cases.reports.enabled = true;
        repository = new CaseNoticeRepository(database, config);
        insert("n", "player", Instant.now().plusSeconds(3600));
    }

    private void insert(String id, String uuid, Instant expiry) {
        database.getCollection(CaseNoticeRepository.COLLECTION).insertOne(new Document("_id", id)
                .append("player_uuid", uuid).append("case_number", 57).append("kind", "report_received")
                .append("params", new Document("number", "57")).append("created_at", new Date())
                .append("expires_at", Date.from(expiry)).append("delivered_at", null));
    }

    @Test
    void claimAndAcknowledgementAreSeparateAndOnlyOneServerOwnsNotice() {
        var claim = repository.claim("n", "player", "one");
        assertThat(claim).isNotNull();
        assertThat(database.getCollection(CaseNoticeRepository.COLLECTION).find(eq("_id", "n")).first().get("delivered_at"))
                .isNull();
        assertThat(repository.claim("n", "player", "two")).isNull();
        assertThat(repository.acknowledge(claim, "one")).isTrue();
        assertThat(repository.acknowledge(claim, "one")).isFalse();
        assertThat(repository.findPending("player", 5)).isEmpty();
        var saved = database.getCollection(CaseNoticeRepository.COLLECTION).find(eq("_id", "n")).first();
        assertThat(saved.getString("delivered_server")).isEqualTo("one");
        assertThat(saved.getDate("delivered_at")).isNotNull();
        assertThat(saved).doesNotContainKey("claim_token");
    }

    @Test
    void simultaneousClaimsHaveOneWinnerAcrossServerInstances() {
        // Create indexes before the actual race so this tests ownership rather than DDL.
        repository.findPending("player", 5);
        var second = new CaseNoticeRepository(database, config);
        second.findPending("player", 5);
        var a = CompletableFuture.supplyAsync(() -> repository.claim("n", "player", "a"));
        var b = CompletableFuture.supplyAsync(() -> second.claim("n", "player", "b"));
        var first = a.join();
        var other = b.join();
        assertThat((first == null ? 0 : 1) + (other == null ? 0 : 1)).isEqualTo(1);
    }

    @Test
    void releaseSurvivesDisconnectAndCannotClearSuccessorClaimAfterExpiry() {
        var old = repository.claim("n", "player", "one");
        repository.release(old);
        var next = repository.claim("n", "player", "two");
        assertThat(next).isNotNull();
        repository.release(old);
        assertThat(repository.claim("n", "player", "three")).isNull();
        database.getCollection(CaseNoticeRepository.COLLECTION).updateOne(eq("_id", "n"),
                new Document("$set", new Document("claim_until", new Date(0))));
        var recovered = repository.claim("n", "player", "three");
        assertThat(recovered.claimToken()).isNotEqualTo(next.claimToken());
        assertThat(repository.acknowledge(next, "two")).isFalse();
        repository.release(next);
        assertThat(repository.claim("n", "player", "four")).isNull();
        assertThat(repository.acknowledge(recovered, "three")).isTrue();
    }

    @Test
    void expiryRecipientAndLimitAreRespectedWithoutRelyingOnTtlSweep() {
        insert("expired", "player", Instant.EPOCH);
        insert("other", "elsewhere", Instant.now().plusSeconds(3600));
        insert("n2", "player", Instant.now().plusSeconds(3600));
        assertThat(repository.findPending("player", 1)).hasSize(1);
        assertThat(repository.findPending("player", 5)).extracting(CaseNotice::id).containsExactly("n", "n2");
        assertThat(repository.claim("n", "elsewhere", "one")).isNull();
        assertThat(repository.claim("expired", "player", "one")).isNull();
    }

    @Test
    void constructionDoesNotCreateCollectionsAndReadOnlyNeverClaims() {
        var fresh = client.getDatabase("case_notice_no_startup_test");
        fresh.drop();
        new CaseNoticeRepository(fresh, new TomlSecretsConfig());
        assertThat(fresh.listCollectionNames()).isEmpty();
        config.database.readOnly = true;
        assertThat(repository.claim("n", "player", "one")).isNull();
        assertThat(repository.findPending("player", 5)).hasSize(1);
    }

    @Test
    void expiredOwnerCannotAcknowledgeEvenBeforeReclaimAndCompletedReleaseIsHarmless() {
        var old = repository.claim("n", "player", "one");
        database.getCollection(CaseNoticeRepository.COLLECTION).updateOne(eq("_id", "n"),
                new Document("$set", new Document("claim_until", new Date(0))));
        assertThat(repository.acknowledge(old, "one")).isFalse();
        var next = repository.claim("n", "player", "two");
        assertThat(repository.acknowledge(next, "two")).isTrue();
        repository.release(old);
        repository.release(next);
        assertThat(repository.findPending("player", 5)).isEmpty();
    }
}
