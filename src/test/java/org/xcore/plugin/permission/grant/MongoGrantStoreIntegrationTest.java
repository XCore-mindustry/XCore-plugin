package org.xcore.plugin.permission.grant;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MongoGrantStoreIntegrationTest {

    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0").withExposedPorts(27017);

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private static MongoClient client;
    private MongoGrantStore store;

    @BeforeAll
    static void connect() {
        client = MongoClients.create("mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void setUp() {
        var collection = client.getDatabase("xcore_test").getCollection(MongoGrantStore.COLLECTION, Document.class);
        collection.drop();
        store = new MongoGrantStore(collection);
    }

    private static Grant role(String id, String role, Instant expiresAt) {
        return new Grant(id, role, null, true, null, expiresAt, Grant.SOURCE_MANUAL, "console@main", NOW, "reason");
    }

    @Test
    @DisplayName("A player nobody wrote about has an empty document at revision 0")
    void empty() {
        assertThat(store.find("uuid-1")).isEqualTo(GrantDocument.empty("uuid-1"));
        assertThat(store.findAll(List.of("uuid-1"))).isEmpty();
    }

    @Test
    @DisplayName("Grants come back as they were written: roles, denials, servers, expiry and source")
    void roundTrip() {
        Grant moderator = new Grant("g-1", "moderator", null, true, "event", NOW.plusSeconds(3600),
                Grant.discordSource("111111111111111111"), "system:discord-sync", NOW, null);
        Grant denial = new Grant("g-2", null, "xcore.moderation.ban", false, null, null,
                Grant.SOURCE_MANUAL, "console@main", NOW, "restriction");

        Optional<GrantDocument> written = store.replace(null, "uuid-1", 0, List.of(moderator, denial));

        assertThat(written).isPresent();
        assertThat(written.get().revision()).isEqualTo(1);
        assertThat(store.find("uuid-1")).isEqualTo(new GrantDocument("uuid-1", 1, List.of(moderator, denial)));
    }

    @Test
    @DisplayName("A write computed from an old revision is refused and changes nothing")
    void compareAndSet() {
        store.replace(null, "uuid-1", 0, List.of(role("g-1", "moderator", null)));

        assertThat(store.replace(null, "uuid-1", 0, List.of(role("g-2", "admin", null)))).isEmpty();
        assertThat(store.find("uuid-1").grants()).extracting(Grant::id).containsExactly("g-1");

        assertThat(store.replace(null, "uuid-1", 1, List.of())).isPresent();
        assertThat(store.find("uuid-1")).isEqualTo(new GrantDocument("uuid-1", 2, List.of()));
    }

    @Test
    @DisplayName("Two first writes for the same player: one wins, the other starts over")
    void firstWriteRace() {
        assertThat(store.replace(null, "uuid-1", 0, List.of(role("g-1", "moderator", null)))).isPresent();
        assertThat(store.replace(null, "uuid-1", 0, List.of(role("g-2", "admin", null)))).isEmpty();
    }

    @Test
    @DisplayName("Many players are read in one go, and the ones with expired grants can be found")
    void batchAndExpired() {
        store.replace(null, "uuid-1", 0, List.of(role("g-1", "moderator", NOW.minusSeconds(1))));
        store.replace(null, "uuid-2", 0, List.of(role("g-2", "admin", NOW.plusSeconds(60))));
        store.replace(null, "uuid-3", 0, List.of(role("g-3", "admin", null)));

        assertThat(store.findAll(List.of("uuid-1", "uuid-3", "uuid-9")).keySet()).containsExactlyInAnyOrder("uuid-1", "uuid-3");
        assertThat(store.findUuidsWithExpiredGrants(NOW)).containsExactly("uuid-1");
    }

    @Test
    @DisplayName("A password reset raises the epoch and the revision, keeps the grants, and survives later grant writes")
    void credentialsEpoch() {
        GrantDocument fresh = store.bumpCredentialsEpoch(null, GrantDocument.empty("uuid-1")).orElseThrow();
        assertThat(fresh).isEqualTo(new GrantDocument("uuid-1", 1, List.of(), 1));
        assertThat(store.find("uuid-1")).isEqualTo(fresh);

        Grant moderator = role("g-1", "moderator", null);
        GrantDocument granted = store.replace(null, "uuid-1", 1, List.of(moderator)).orElseThrow();
        assertThat(granted).isEqualTo(new GrantDocument("uuid-1", 2, List.of(moderator), 1));

        assertThat(store.bumpCredentialsEpoch(null, fresh)).as("computed from an old revision").isEmpty();
        GrantDocument again = store.bumpCredentialsEpoch(null, granted).orElseThrow();
        assertThat(again).isEqualTo(new GrantDocument("uuid-1", 3, List.of(moderator), 2));
        assertThat(store.findAll(List.of("uuid-1")).get("uuid-1")).isEqualTo(again);

        assertThat(store.bumpCredentialsEpoch(null, GrantDocument.empty("uuid-1"))).as("a first write that came second").isEmpty();
    }
}
