package org.xcore.plugin.rating.season;

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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class MongoSeasonStoreIntegrationTest {
    private static final Instant START = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant END = Instant.parse("2027-01-03T00:00:00Z");

    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    private static MongoClient client;
    private MongoDatabase database;
    private MongoSeasonStore store;

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
        database = client.getDatabase("season_store_test");
        database.drop();
        store = new MongoSeasonStore(database, new TomlSecretsConfig());
    }

    private Season created(String ladderId, int number) {
        Season season = Season.opening(ladderId, number, START, END);
        assertThat(store.create(season)).isTrue();
        return store.find(ladderId, number).orElseThrow();
    }

    @Test
    @DisplayName("a created season reads back as it was written and is stored in the documented shape")
    void create_roundTrips() {
        Season season = created("duel", 1);

        assertThat(season).isEqualTo(Season.opening("duel", 1, START, END));

        Document document = database.getCollection(MongoSeasonStore.COLLECTION).find().first();
        assertThat(document.getString("_id")).isEqualTo("duel:1");
        assertThat(document.getString("ladder")).isEqualTo("duel");
        assertThat(document.getInteger("number")).isEqualTo(1);
        assertThat(document.getString("status")).isEqualTo("ACTIVE");
        assertThat(document.getDate("starts_at")).isEqualTo(Date.from(START));
        assertThat(document.getDate("ends_at")).isEqualTo(Date.from(END));
        assertThat(document.getList("sent_notices", String.class)).isEmpty();
        assertThat(document.get("summary")).isNull();
        assertThat(document.getDate("created_at")).isNotNull();
    }

    @Test
    @DisplayName("creating a season that exists changes nothing")
    void create_isIdempotent() {
        created("duel", 1);

        assertThat(store.create(Season.opening("duel", 1, START.plusSeconds(99), END.plusSeconds(99)))).isFalse();
        assertThat(store.find("duel", 1).orElseThrow().endsAt()).isEqualTo(END);
    }

    @Test
    @DisplayName("update stores every transition field and bumps the revision")
    void update_storesTransition() {
        Season season = created("duel", 1);
        SeasonReschedule change = new SeasonReschedule(END, END.plus(Duration.ofDays(3)), "server_console:console",
                START.plusSeconds(5), "holidays");
        SeasonPodiumEntry winner = new SeasonPodiumEntry(1, "ace", 7, "Ace", 1500, "SILICON", 12, 6, "1234", "ace");

        Season moved = store.update(season, season.reschedule(change, Set.of("7d"))).orElseThrow();
        Season archived = store.update(moved, moved.close().archive(List.of(winner), new SeasonSummary(42, 17)))
                .orElseThrow();

        assertThat(moved.revision()).isEqualTo(1);
        assertThat(moved.endsAt()).isEqualTo(END.plus(Duration.ofDays(3)));
        assertThat(moved.sentNotices()).containsExactly("7d");
        assertThat(archived.revision()).isEqualTo(2);
        assertThat(archived.status()).isEqualTo(SeasonStatus.ARCHIVED);
        assertThat(archived.rescheduled()).containsExactly(change);
        assertThat(archived.podium()).containsExactly(winner);
        assertThat(archived.summary()).isEqualTo(new SeasonSummary(42, 17));
        assertThat(store.find("duel", 1)).contains(archived);
    }

    @Test
    @DisplayName("an update based on a stale revision is refused")
    void update_rejectsStaleRevision() {
        Season season = created("duel", 1);
        store.update(season, season.withSentNotices(Set.of("7d"))).orElseThrow();

        assertThat(store.update(season, season.close())).isEmpty();
        assertThat(store.find("duel", 1).orElseThrow().status()).isEqualTo(SeasonStatus.ACTIVE);
    }

    @Test
    @DisplayName("of many servers racing to close a season exactly one wins")
    void update_concurrentTransitionHasOneWinner() throws Exception {
        Season season = created("duel", 1);
        List<Callable<Optional<Season>>> tasks = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            tasks.add(() -> store.update(season, season.close()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(16);
        int winners = 0;
        try {
            for (Future<Optional<Season>> result : executor.invokeAll(tasks)) {
                if (result.get().isPresent()) {
                    winners++;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(winners).isEqualTo(1);
        assertThat(store.find("duel", 1).orElseThrow().revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("counting matches neither blocks nor is undone by a concurrent transition")
    void countMatch_survivesUpdates() {
        Season season = created("duel", 1);

        store.countMatch("duel", 1);
        store.countMatch("duel", 1);
        // The transition was prepared before the matches were counted.
        Season closed = store.update(season, season.close()).orElseThrow();
        store.countMatch("duel", 1);
        store.countMatch("duel", 404);

        assertThat(closed.matches()).isEqualTo(2);
        assertThat(store.find("duel", 1).orElseThrow().matches()).isEqualTo(3);
        assertThat(store.find("duel", 404)).isEmpty();
    }

    @Test
    @DisplayName("seasons are listed by ladder and newest first; open ones exclude the archive")
    void list_all_open() {
        Season duel1 = created("duel", 1);
        duel1 = store.update(duel1, duel1.close().archive(List.of(), new SeasonSummary(0, 0))).orElseThrow();
        Season duel2 = created("duel", 2);
        duel2 = store.update(duel2, duel2.close()).orElseThrow();
        Season duel3 = created("duel", 3);
        Season ffa1 = created("ffa", 1);

        assertThat(store.list("duel")).containsExactly(duel3, duel2, duel1);
        assertThat(store.list("unknown")).isEmpty();
        assertThat(store.all()).containsExactly(duel3, duel2, duel1, ffa1);
        assertThat(store.open()).containsExactly(duel3, duel2, ffa1);
    }

    @Test
    @DisplayName("a read-only database rejects writes and skips index management")
    void readOnly_rejectsWrites() {
        Season season = created("duel", 1);
        TomlSecretsConfig readOnly = new TomlSecretsConfig();
        readOnly.database.readOnly = true;
        MongoSeasonStore readOnlyStore = new MongoSeasonStore(database, readOnly);

        assertThat(readOnlyStore.open()).containsExactly(season);
        assertThatThrownBy(() -> readOnlyStore.create(Season.opening("duel", 2, START, END)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> readOnlyStore.update(season, season.close()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> readOnlyStore.countMatch("duel", 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("prizes are stored in the documented shape, read back, and survive later transitions")
    void update_storesPrizes() {
        Season season = created("duel", 1);
        SeasonPrize badge = new SeasonPrize(1, 1, PrizeKind.BADGE, "season-champion", "");
        SeasonPrize nitro = new SeasonPrize(1, 3, PrizeKind.CUSTOM, "Discord Nitro", "1 month");

        Season prized = store.update(season, season.withPrizes(List.of(badge, nitro))).orElseThrow();
        Season moved = store.update(prized, prized.close()).orElseThrow();

        assertThat(prized.prizes()).containsExactly(badge, nitro);
        assertThat(moved.prizes()).containsExactly(badge, nitro);
        assertThat(store.find("duel", 1).orElseThrow().prizes()).containsExactly(badge, nitro);
        Document stored = database.getCollection(MongoSeasonStore.COLLECTION).find().first()
                .getList("prizes", Document.class).get(1);
        assertThat(stored.getInteger("place_from")).isEqualTo(1);
        assertThat(stored.getInteger("place_to")).isEqualTo(3);
        assertThat(stored.getString("kind")).isEqualTo("CUSTOM");
        assertThat(stored.getString("value")).isEqualTo("Discord Nitro");
        assertThat(stored.getString("description")).isEqualTo("1 month");
    }

    @Test
    @DisplayName("two servers editing the prizes at once cannot both win")
    void update_prizeEditsAreCompareAndSet() {
        Season season = created("duel", 1);

        assertThat(store.update(season, season.withPrizes(List.of(new SeasonPrize(1, 1, PrizeKind.CUSTOM, "A", ""))))).isPresent();
        assertThat(store.update(season, season.withPrizes(List.of(new SeasonPrize(2, 2, PrizeKind.CUSTOM, "B", ""))))).isEmpty();

        assertThat(store.find("duel", 1).orElseThrow().prizes()).hasSize(1);
    }
}
