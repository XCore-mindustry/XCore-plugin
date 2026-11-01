package org.xcore.plugin.database.migration;

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
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.ladder.MongoLadderStore;
import org.xcore.plugin.rating.ladder.StandingMutation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class V5__CreateRatingStandingsIntegrationTest {

    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    private static MongoClient client;
    private MongoDatabase database;

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
        database = client.getDatabase("v5_migration_test");
        database.drop();

        MongoCollection<Document> players = database.getCollection("players");
        players.insertMany(List.of(
                new Document("uuid", "veteran").append("pvp_rating", 1340).append("pvp_matches", 25).append("pvp_wins", 14),
                new Document("uuid", "winless").append("pvp_rating", 960).append("pvp_matches", 3),
                new Document("uuid", "newcomer").append("pvp_rating", 1000).append("pvp_matches", 0).append("pvp_wins", 0),
                new Document("uuid", "never-played"),
                new Document("uuid", "merged:old").append("pvp_rating", 1500).append("pvp_matches", 40).append("pvp_wins", 30)));

        database.getCollection("xcore_plugin_hexedcore_rating_players").insertMany(List.of(
                new Document("player_uuid", "veteran").append("data", new Document("rating", 1180)
                        .append("matches", 12).append("wins", 4).append("top3", 7).append("disconnects", 1)
                        .append("best_placement", 1).append("last_placement", 3)),
                new Document("player_uuid", "hexed-only").append("data", new Document("rating", 890)
                        .append("matches", 2).append("wins", 0)),
                new Document("player_uuid", "seeded").append("data", new Document("rating", 1000).append("matches", 0))));
    }

    private MongoLadderStore store() {
        return new MongoLadderStore(database, new TomlSecretsConfig());
    }

    @Test
    @DisplayName("up copies every played rating into season 1 of its ladder")
    void up_copiesRatings() {
        new V5__CreateRatingStandings().up(database);
        MongoLadderStore store = store();

        LadderStanding veteran = store.find("minipvp", 1, "veteran").orElseThrow();
        assertThat(veteran.rating()).isEqualTo(1340);
        assertThat(veteran.peakRating()).isEqualTo(1340);
        assertThat(veteran.matches()).isEqualTo(25);
        assertThat(veteran.wins()).isEqualTo(14);
        assertThat(veteran.stats()).isEmpty();
        assertThat(store.find("minipvp", 1, "winless").orElseThrow().wins()).isZero();
        assertThat(store.count("minipvp", 1)).isEqualTo(2); // no unplayed, never-played or merged accounts

        LadderStanding hexed = store.find("hexed", 1, "veteran").orElseThrow();
        assertThat(hexed.rating()).isEqualTo(1180);
        assertThat(hexed.matches()).isEqualTo(12);
        assertThat(hexed.wins()).isEqualTo(4);
        assertThat(hexed.stats()).containsEntry("top3", 7).containsEntry("disconnects", 1).hasSize(2);
        assertThat(store.find("hexed", 1, "hexed-only").orElseThrow().stats())
                .containsEntry("top3", 0).containsEntry("disconnects", 0);
        assertThat(store.count("hexed", 1)).isEqualTo(2);

        // Sources stay as they were.
        assertThat(database.getCollection("players").countDocuments()).isEqualTo(5);
        assertThat(database.getCollection("players").find(new Document("uuid", "veteran")).first()
                .getInteger("pvp_rating")).isEqualTo(1340);
        assertThat(database.getCollection("xcore_plugin_hexedcore_rating_players").countDocuments()).isEqualTo(3);
    }

    @Test
    @DisplayName("migrated standings keep settling like any other")
    void up_standingsAreUsable() {
        new V5__CreateRatingStandings().up(database);
        MongoLadderStore store = store();

        var result = store.applyOnce("minipvp", 1, "op-1", new StandingMutation("veteran", 20, true), 1000, 100);

        assertThat(result.applied()).isTrue();
        assertThat(result.standing().rating()).isEqualTo(1360);
        assertThat(result.standing().peakRating()).isEqualTo(1360);
        assertThat(result.standing().matches()).isEqualTo(26);
        assertThat(result.standing().wins()).isEqualTo(15);
    }

    @Test
    @DisplayName("re-running the migration never overwrites a standing the ladder already owns")
    void up_isRerunnable() {
        V5__CreateRatingStandings migration = new V5__CreateRatingStandings();
        migration.up(database);
        store().applyOnce("minipvp", 1, "op-1", new StandingMutation("veteran", 20, true), 1000, 100);

        migration.up(database);

        LadderStanding veteran = store().find("minipvp", 1, "veteran").orElseThrow();
        assertThat(veteran.rating()).isEqualTo(1360);
        assertThat(veteran.matches()).isEqualTo(26);
        assertThat(store().count("minipvp", 1)).isEqualTo(2);
    }

    @Test
    @DisplayName("up works on a database that has neither source collection")
    void up_onEmptyDatabase() {
        database.drop();

        new V5__CreateRatingStandings().up(database);

        assertThat(store().count("minipvp", 1)).isZero();
        assertThat(store().count("hexed", 1)).isZero();
    }
}
