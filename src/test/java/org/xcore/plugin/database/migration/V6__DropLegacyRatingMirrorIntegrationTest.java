package org.xcore.plugin.database.migration;

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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class V6__DropLegacyRatingMirrorIntegrationTest {

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
        if (client != null) client.close();
    }

    @BeforeEach
    void setUp() {
        database = client.getDatabase("v6_migration_test");
        database.drop();
        database.getCollection("players").insertMany(List.of(
                new Document("uuid", "veteran").append("pvp_rating", 1340).append("pvp_matches", 25)
                        .append("pvp_wins", 14).append("legacy_pvp_rating", 1800).append("hexed_points", 9),
                new Document("uuid", "never-played").append("nickname", "n")));
        database.getCollection(V6__DropLegacyRatingMirror.HEXED_LEGACY)
                .insertOne(new Document("player_uuid", "veteran").append("data", new Document("rating", 1180)));
    }

    private List<String> collections() {
        return database.listCollectionNames().into(new ArrayList<>());
    }

    @Test
    @DisplayName("up unsets the pvp mirror but keeps the legacy rating and every other field")
    void up_unsetsMirror() {
        new V6__DropLegacyRatingMirror().up(database);

        Document veteran = database.getCollection("players").find(new Document("uuid", "veteran")).first();
        assertThat(veteran).doesNotContainKeys("pvp_rating", "pvp_matches", "pvp_wins");
        assertThat(veteran.getInteger("legacy_pvp_rating")).isEqualTo(1800);
        assertThat(veteran.getInteger("hexed_points")).isEqualTo(9);
        assertThat(database.getCollection("players").countDocuments()).isEqualTo(2);
    }

    @Test
    @DisplayName("up archives the old HexedCore collection instead of dropping it")
    void up_archivesHexedCollection() {
        new V6__DropLegacyRatingMirror().up(database);

        assertThat(collections()).doesNotContain(V6__DropLegacyRatingMirror.HEXED_LEGACY)
                .contains(V6__DropLegacyRatingMirror.HEXED_ARCHIVE);
        assertThat(database.getCollection(V6__DropLegacyRatingMirror.HEXED_ARCHIVE).countDocuments()).isEqualTo(1);
    }

    @Test
    @DisplayName("re-running changes nothing and never overwrites an existing archive")
    void up_isRerunnable() {
        var migration = new V6__DropLegacyRatingMirror();
        migration.up(database);
        database.getCollection(V6__DropLegacyRatingMirror.HEXED_LEGACY).insertOne(new Document("player_uuid", "late"));

        migration.up(database);

        assertThat(database.getCollection(V6__DropLegacyRatingMirror.HEXED_ARCHIVE).countDocuments()).isEqualTo(1);
        assertThat(database.getCollection(V6__DropLegacyRatingMirror.HEXED_LEGACY).countDocuments()).isEqualTo(1);
    }

    @Test
    @DisplayName("up works on a database without the old collection")
    void up_onEmptyDatabase() {
        database.drop();

        new V6__DropLegacyRatingMirror().up(database);

        assertThat(collections()).doesNotContain(V6__DropLegacyRatingMirror.HEXED_ARCHIVE);
    }

    @Test
    @DisplayName("declares version 6")
    void version() {
        assertThat(new V6__DropLegacyRatingMirror().getVersion()).isEqualTo(6);
    }
}
