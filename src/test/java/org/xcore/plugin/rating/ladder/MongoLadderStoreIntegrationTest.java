package org.xcore.plugin.rating.ladder;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class MongoLadderStoreIntegrationTest {

    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    private static MongoClient client;
    private MongoDatabase database;
    private MongoLadderStore store;

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
        database = client.getDatabase("ladder_store_test");
        database.drop();
        store = new MongoLadderStore(database, new TomlSecretsConfig());
    }

    private MongoCollection<Document> collection() {
        return database.getCollection(MongoLadderStore.COLLECTION);
    }

    @Test
    @DisplayName("applyOnce creates a standing from the starting rating")
    void applyOnce_createsStanding() {
        var result = store.applyOnce("duel", 1, "op-1",
                new StandingMutation("p1", 16, true, Map.of("top3", 1)), 1000, 100);

        assertThat(result.applied()).isTrue();
        LadderStanding standing = store.find("duel", 1, "p1").orElseThrow();
        assertThat(standing).isEqualTo(result.standing());
        assertThat(standing.ladderId()).isEqualTo("duel");
        assertThat(standing.season()).isEqualTo(1);
        assertThat(standing.rating()).isEqualTo(1016);
        assertThat(standing.peakRating()).isEqualTo(1016);
        assertThat(standing.matches()).isEqualTo(1);
        assertThat(standing.wins()).isEqualTo(1);
        assertThat(standing.stats()).containsExactly(Map.entry("top3", 1));

        Document raw = collection().find().first();
        assertThat(raw.getList("applied_operations", String.class)).containsExactly("op-1");
        assertThat(raw.get("created_at")).isNotNull();
        assertThat(raw.get("updated_at")).isNotNull();
    }

    @Test
    @DisplayName("applyOnce accumulates over matches, clamps to the minimum and keeps the peak")
    void applyOnce_accumulates() {
        store.applyOnce("duel", 1, "op-1", new StandingMutation("p1", 40, true, Map.of("top3", 1)), 1000, 100);
        store.applyOnce("duel", 1, "op-2", new StandingMutation("p1", -15, false, Map.of("disconnects", 1)), 1000, 100);
        var last = store.applyOnce("duel", 1, "op-3", new StandingMutation("p1", -5000, false, Map.of("top3", 1)), 1000, 100);

        assertThat(last.applied()).isTrue();
        assertThat(last.standing().rating()).isEqualTo(100);
        assertThat(last.standing().peakRating()).isEqualTo(1040);
        assertThat(last.standing().matches()).isEqualTo(3);
        assertThat(last.standing().wins()).isEqualTo(1);
        assertThat(last.standing().stats()).containsEntry("top3", 2).containsEntry("disconnects", 1);
        assertThat(collection().countDocuments()).isEqualTo(1);
    }

    @Test
    @DisplayName("replaying an operation changes nothing and reports the current standing")
    void applyOnce_replayIsIgnored() {
        StandingMutation mutation = new StandingMutation("p1", 16, true);
        store.applyOnce("duel", 1, "op-1", mutation, 1000, 100);

        var replay = store.applyOnce("duel", 1, "op-1", mutation, 1000, 100);

        assertThat(replay.applied()).isFalse();
        assertThat(replay.standing().rating()).isEqualTo(1016);
        assertThat(replay.standing().matches()).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrent applications of the same operation take effect exactly once")
    void applyOnce_concurrentReplays() throws Exception {
        StandingMutation mutation = new StandingMutation("p1", 10, true);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            tasks.add(() -> store.applyOnce("duel", 1, "op-1", mutation, 1000, 100).applied());
        }

        int applied = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            for (Future<Boolean> future : executor.invokeAll(tasks)) {
                if (future.get()) applied++;
            }
        }

        assertThat(applied).isEqualTo(1);
        assertThat(store.find("duel", 1, "p1").orElseThrow().rating()).isEqualTo(1010);
        assertThat(store.find("duel", 1, "p1").orElseThrow().matches()).isEqualTo(1);
    }

    @Test
    @DisplayName("the trail of applied operations is capped")
    void applyOnce_capsAppliedOperations() {
        for (int i = 0; i < MongoLadderStore.APPLIED_OPERATIONS_LIMIT + 5; i++) {
            store.applyOnce("duel", 1, "op-" + i, new StandingMutation("p1", 1, false), 1000, 100);
        }

        List<String> operations = collection().find().first().getList("applied_operations", String.class);
        assertThat(operations).hasSize(MongoLadderStore.APPLIED_OPERATIONS_LIMIT);
        assertThat(operations.getLast()).isEqualTo("op-" + (MongoLadderStore.APPLIED_OPERATIONS_LIMIT + 4));
    }

    @Test
    @DisplayName("standings are separate per ladder and season")
    void standings_areScoped() {
        store.applyOnce("duel", 1, "op-1", new StandingMutation("p1", 16, true), 1000, 100);
        store.applyOnce("duel", 2, "op-1", new StandingMutation("p1", 5, true), 1000, 100);
        store.applyOnce("ffa", 1, "op-1", new StandingMutation("p1", -7, false), 1000, 100);

        assertThat(store.find("duel", 1, "p1").orElseThrow().rating()).isEqualTo(1016);
        assertThat(store.find("duel", 2, "p1").orElseThrow().rating()).isEqualTo(1005);
        assertThat(store.find("ffa", 1, "p1").orElseThrow().rating()).isEqualTo(993);
        assertThat(store.find("ffa", 2, "p1")).isEmpty();
        assertThat(store.count("duel", 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("top pages best-first with a stable tie-break, and rankOf agrees with it")
    void top_andRank() {
        store.applyOnce("duel", 1, "op", new StandingMutation("c", 10, true), 1000, 100);
        store.applyOnce("duel", 1, "op", new StandingMutation("a", 10, true), 1000, 100);
        store.applyOnce("duel", 1, "op", new StandingMutation("b", 30, true), 1000, 100);
        store.applyOnce("duel", 1, "op", new StandingMutation("d", -10, false), 1000, 100);
        store.applyOnce("ffa", 1, "op", new StandingMutation("z", 500, true), 1000, 100);

        StandingPage first = store.top("duel", 1, 3, null);
        assertThat(first.standings()).extracting(LadderStanding::uuid).containsExactly("b", "a", "c");
        assertThat(first.hasNext()).isTrue();

        StandingPage second = store.top("duel", 1, 3, first.nextCursor());
        assertThat(second.standings()).extracting(LadderStanding::uuid).containsExactly("d");
        assertThat(second.hasNext()).isFalse();

        assertThat(store.rankOf("duel", 1, "b")).hasValue(1);
        assertThat(store.rankOf("duel", 1, "a")).hasValue(2);
        assertThat(store.rankOf("duel", 1, "c")).hasValue(3);
        assertThat(store.rankOf("duel", 1, "d")).hasValue(4);
        assertThat(store.rankOf("duel", 1, "z")).isEmpty();
        assertThat(store.count("duel", 1)).isEqualTo(4);

        assertThatThrownBy(() -> store.top("duel", 1, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a standing created from a carried rating records where it came from, once")
    void applyOnce_recordsSeed() {
        store.applyOnce("duel", 1, "op-1", new StandingMutation("p1", 400, true), 1000, 100);
        LadderStanding previous = store.find("duel", 1, "p1").orElseThrow();

        var created = store.applyOnce("duel", 2, "op-2", new StandingMutation("p1", 10, true),
                StandingSeed.carried(1200, previous), 100);
        // A later match must not rewrite the origin, whatever seed the caller passes.
        store.applyOnce("duel", 2, "op-3", new StandingMutation("p1", 5, true), new StandingSeed(9999, 7, 9999), 100);
        store.applyOnce("duel", 2, "op-4", new StandingMutation("fresh", 5, true), StandingSeed.fresh(1000), 100);

        assertThat(created.standing().rating()).isEqualTo(1210);
        assertThat(store.find("duel", 2, "p1").orElseThrow().rating()).isEqualTo(1215);
        Document stored = collection().find(new Document("season", 2).append("player_uuid", "p1")).first();
        assertThat(stored.get("seeded_from", Document.class))
                .isEqualTo(new Document("season", 1).append("rating", 1400));
        assertThat(collection().find(new Document("season", 2).append("player_uuid", "fresh")).first())
                .doesNotContainKey("seeded_from");
        assertThat(collection().find(new Document("season", 1).append("player_uuid", "p1")).first())
                .doesNotContainKey("seeded_from");
    }

    @Test
    @DisplayName("latestBefore finds the most recent earlier season a player has a standing in")
    void latestBefore_findsPreviousSeason() {
        store.applyOnce("duel", 1, "op-1", new StandingMutation("p1", 100, true), 1000, 100);
        store.applyOnce("duel", 3, "op-2", new StandingMutation("p1", 300, true), 1000, 100);
        store.applyOnce("ffa", 4, "op-3", new StandingMutation("p1", 50, true), 1000, 100);
        store.applyOnce("duel", 4, "op-4", new StandingMutation("other", 50, true), 1000, 100);

        assertThat(store.latestBefore("duel", 1, "p1")).isEmpty();
        assertThat(store.latestBefore("duel", 3, "p1").orElseThrow().season()).isEqualTo(1);
        assertThat(store.latestBefore("duel", 5, "p1").orElseThrow().rating()).isEqualTo(1300);
        assertThat(store.latestBefore("duel", 5, "nobody")).isEmpty();
    }

    @Test
    @DisplayName("history lists earlier seasons of one player, most recent first, and carries the final rank")
    void history_listsEarlierSeasons() {
        store.applyOnce("duel", 1, "op-1", new StandingMutation("p1", 100, true), 1000, 100);
        store.applyOnce("duel", 1, "op-1", new StandingMutation("p2", 200, true), 1000, 100);
        store.applyOnce("duel", 2, "op-2", new StandingMutation("p1", 300, true), 1000, 100);
        store.applyOnce("duel", 3, "op-3", new StandingMutation("p1", 50, true), 1000, 100);
        store.applyOnce("duel", 4, "op-4", new StandingMutation("p1", 10, true), 1000, 100);
        store.applyOnce("ffa", 1, "op-5", new StandingMutation("p1", 70, true), 1000, 100);
        store.assignFinalRanks("duel", 1);

        List<LadderStanding> history = store.history("duel", 4, "p1", 10);

        assertThat(history).extracting(LadderStanding::season).containsExactly(3, 2, 1);
        assertThat(history.getLast().finalRank()).isEqualTo(2);
        assertThat(history.getFirst().finalRank()).isNull();
        assertThat(store.history("duel", 4, "p1", 2)).extracting(LadderStanding::season).containsExactly(3, 2);
        assertThat(store.history("duel", 1, "p1", 10)).isEmpty();
        assertThat(store.history("duel", 4, "nobody", 10)).isEmpty();
    }

    @Test
    @DisplayName("final ranks follow the leaderboard order, and leaders skip players with too few matches")
    void finalRanks_andLeaders() {
        store.applyOnce("duel", 1, "op-1", new StandingMutation("b", 30, true), 1000, 100);
        store.applyOnce("duel", 1, "op-1", new StandingMutation("c", 10, true), 1000, 100);
        store.applyOnce("duel", 1, "op-1", new StandingMutation("a", 10, true), 1000, 100);
        store.applyOnce("duel", 1, "op-2", new StandingMutation("a", 0, false), 1000, 100);
        store.applyOnce("duel", 1, "op-2", new StandingMutation("c", 0, false), 1000, 100);
        store.applyOnce("duel", 2, "op-3", new StandingMutation("z", 500, true), 1000, 100);

        assertThat(store.leaders("duel", 1, 2, 10)).extracting(LadderStanding::uuid).containsExactly("a", "c");
        assertThat(store.leaders("duel", 1, 0, 2)).extracting(LadderStanding::uuid).containsExactly("b", "a");
        assertThat(store.leaders("duel", 1, 99, 10)).isEmpty();

        assertThat(store.assignFinalRanks("duel", 1)).isEqualTo(3);
        assertThat(store.assignFinalRanks("duel", 1)).isEqualTo(3);
        assertThat(store.assignFinalRanks("duel", 9)).isZero();

        assertThat(finalRank(1, "b")).isEqualTo(1);
        assertThat(finalRank(1, "a")).isEqualTo(2);
        assertThat(finalRank(1, "c")).isEqualTo(3);
        assertThat(collection().find(new Document("season", 2)).first()).doesNotContainKey("final_rank");
    }

    private Integer finalRank(int season, String uuid) {
        return collection().find(new Document("ladder", "duel").append("season", season)
                .append("player_uuid", uuid)).first().getInteger("final_rank");
    }

    @Test
    @DisplayName("mergePlayer combines overlapping standings and re-keys the rest")
    void mergePlayer_combines() {
        store.applyOnce("duel", 1, "op-1", new StandingMutation("old", 300, true, Map.of("top3", 2)), 1000, 100);
        store.applyOnce("duel", 1, "op-2", new StandingMutation("new", 100, true, Map.of("top3", 1)), 1000, 100);
        store.applyOnce("duel", 1, "op-3", new StandingMutation("new", -50, false, Map.of("disconnects", 1)), 1000, 100);
        store.applyOnce("ffa", 1, "op-4", new StandingMutation("old", -20, false), 1000, 100);

        int merged = store.mergePlayer(null, "old", "new");

        assertThat(merged).isEqualTo(2);
        LadderStanding duel = store.find("duel", 1, "new").orElseThrow();
        assertThat(duel.rating()).isEqualTo(1300);
        assertThat(duel.peakRating()).isEqualTo(1300);
        assertThat(duel.matches()).isEqualTo(3);
        assertThat(duel.wins()).isEqualTo(2);
        assertThat(duel.stats()).containsEntry("top3", 3).containsEntry("disconnects", 1);

        LadderStanding ffa = store.find("ffa", 1, "new").orElseThrow();
        assertThat(ffa.rating()).isEqualTo(980);
        assertThat(ffa.matches()).isEqualTo(1);

        assertThat(store.find("duel", 1, "old")).isEmpty();
        assertThat(store.find("ffa", 1, "old")).isEmpty();
        assertThat(collection().countDocuments()).isEqualTo(2);

        // A match the old account already played must not be counted again for the merged one.
        assertThat(store.applyOnce("duel", 1, "op-1", new StandingMutation("new", 300, true), 1000, 100).applied())
                .isFalse();
        assertThat(store.applyOnce("ffa", 1, "op-4", new StandingMutation("new", -20, false), 1000, 100).applied())
                .isFalse();
    }

    @Test
    @DisplayName("a read-only store refuses writes and creates no indexes")
    void readOnly_refusesWrites() {
        TomlSecretsConfig readOnly = new TomlSecretsConfig();
        readOnly.database.readOnly = true;
        MongoLadderStore readOnlyStore = new MongoLadderStore(database, readOnly);

        assertThatThrownBy(() -> readOnlyStore.applyOnce("duel", 1, "op-1",
                new StandingMutation("p1", 1, true), 1000, 100)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> readOnlyStore.mergePlayer(null, "a", "b")).isInstanceOf(IllegalStateException.class);
        assertThat(readOnlyStore.find("duel", 1, "p1")).isEmpty();
    }
}
