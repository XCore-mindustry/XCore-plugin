package org.xcore.plugin.rating.prize;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.rating.season.PrizeKind;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class MongoPrizeGrantRepositoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2027-01-03T00:05:00Z");

    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    private static MongoClient client;
    private MongoDatabase database;
    private MongoPrizeGrantRepository repository;

    @BeforeAll
    static void connect() {
        client = MongoClients.create("mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017));
    }

    @AfterAll
    static void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @BeforeEach
    void setUp() {
        database = client.getDatabase("prize_grants_test");
        database.drop();
        repository = new MongoPrizeGrantRepository(database, new TomlSecretsConfig());
    }

    private static PrizeGrant grant(int place, String uuid, int index, PrizeKind kind, String value) {
        return new PrizeGrant(PrizeGrant.id("duel:1", place, uuid, index), "duel:1", place, uuid, index, kind, value,
                "d", PrizeStatus.PENDING, PrizeGrant.SYSTEM, NOW, "");
    }

    @Test
    @DisplayName("a grant is stored in the documented shape and reads back as written")
    void roundTrips() {
        PrizeGrant grant = grant(1, "ace", 0, PrizeKind.BADGE, "season-champion");

        assertThat(repository.createIfAbsent(grant)).isTrue();

        assertThat(repository.findBySeason("duel:1")).containsExactly(grant);
        Document document = database.getCollection(MongoPrizeGrantRepository.COLLECTION).find().first();
        assertThat(document.getString("_id")).isEqualTo("duel:1:1:ace:0");
        assertThat(document.getString("status")).isEqualTo("PENDING");
        assertThat(document.get("prize", Document.class).getString("kind")).isEqualTo("BADGE");
        assertThat(document.getString("granted_by")).isEqualTo("system");
    }

    @Test
    @DisplayName("creating a grant that exists leaves it as it was")
    void createIsIdempotent() {
        PrizeGrant grant = grant(1, "ace", 0, PrizeKind.CUSTOM, "Nitro");
        repository.createIfAbsent(grant);
        repository.transition(grant.id(), PrizeStatus.PENDING, PrizeStatus.DELIVERED, "console:console", "sent", NOW);

        assertThat(repository.createIfAbsent(grant)).isFalse();

        assertThat(repository.findBySeason("duel:1").getFirst().status()).isEqualTo(PrizeStatus.DELIVERED);
    }

    @Test
    @DisplayName("a transition only applies while the grant is still in the expected status")
    void transitionIsCompareAndSet() {
        PrizeGrant grant = grant(1, "ace", 0, PrizeKind.BADGE, "season-champion");
        repository.createIfAbsent(grant);

        assertThat(repository.transition(grant.id(), PrizeStatus.PENDING, PrizeStatus.GRANTED, "system", "ok", NOW)).isTrue();
        assertThat(repository.transition(grant.id(), PrizeStatus.PENDING, PrizeStatus.FAILED, "system", "late", NOW)).isFalse();

        PrizeGrant stored = repository.findBySeason("duel:1").getFirst();
        assertThat(stored.status()).isEqualTo(PrizeStatus.GRANTED);
        assertThat(stored.note()).isEqualTo("ok");
    }

    @Test
    @DisplayName("delivering a place settles its pending and failed grants and nothing else")
    void markDelivered() {
        repository.createIfAbsent(grant(1, "ace", 0, PrizeKind.BADGE, "season-champion"));
        repository.createIfAbsent(grant(1, "ace", 1, PrizeKind.CUSTOM, "Nitro"));
        repository.createIfAbsent(grant(2, "bob", 1, PrizeKind.CUSTOM, "Nitro"));
        repository.transition("duel:1:1:ace:0", PrizeStatus.PENDING, PrizeStatus.GRANTED, "system", "", NOW);

        int changed = repository.markDelivered("duel:1", 1, null, "discord_user:222", "code sent", NOW.plusSeconds(60));

        assertThat(changed).isEqualTo(1);
        var grants = repository.findBySeason("duel:1");
        assertThat(grants).extracting(PrizeGrant::status).containsExactly(
                PrizeStatus.GRANTED, PrizeStatus.DELIVERED, PrizeStatus.PENDING);
        assertThat(grants.get(1).grantedBy()).isEqualTo("discord_user:222");
        assertThat(grants.get(1).note()).isEqualTo("code sent");
        assertThat(repository.markDelivered("duel:1", 1, null, "x", "", NOW)).isZero();
    }

    @Test
    @DisplayName("delivering to one player settles only that player's grants")
    void markDelivered_onePlayer() {
        repository.createIfAbsent(grant(1, "ace", 0, PrizeKind.CUSTOM, "Nitro"));
        repository.createIfAbsent(grant(1, "bob", 0, PrizeKind.CUSTOM, "Nitro"));

        assertThat(repository.markDelivered("duel:1", 1, "bob", "discord_user:222", "sent", NOW)).isEqualTo(1);

        assertThat(repository.findBySeason("duel:1")).extracting(PrizeGrant::playerUuid, PrizeGrant::status)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ace", PrizeStatus.PENDING),
                        org.assertj.core.groups.Tuple.tuple("bob", PrizeStatus.DELIVERED));
    }

    @Test
    @DisplayName("a read-only server cannot write grants")
    void readOnly() {
        TomlSecretsConfig config = new TomlSecretsConfig();
        config.database.readOnly = true;
        var readOnly = new MongoPrizeGrantRepository(database, config);

        assertThatThrownBy(() -> readOnly.createIfAbsent(grant(1, "ace", 0, PrizeKind.BADGE, "season-champion")))
                .isInstanceOf(IllegalStateException.class);
    }
}
