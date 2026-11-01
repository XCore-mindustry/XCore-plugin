package org.xcore.plugin.rating.match;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class MongoMatchStoreIntegrationTest extends MatchStoreContract {

    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    private static MongoClient client;
    private MongoDatabase database;
    private MongoMatchStore store;

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
        database = client.getDatabase("match_store_test");
        database.drop();
        store = new MongoMatchStore(database, new TomlSecretsConfig());
    }

    @Override
    MatchStore store() {
        return store;
    }

    @Test
    @DisplayName("a match is stored under its ladder and ID, with the fields the history is queried by")
    void record_writesDocument() {
        store.record(match("duel", "m1", T0, "a", "b"));

        Document document = database.getCollection(MongoMatchStore.COLLECTION).find().first();
        assertThat(document).isNotNull();
        assertThat(document.getString("_id")).isEqualTo("duel:m1");
        assertThat(document.getString("ladder")).isEqualTo("duel");
        assertThat(document.getBoolean("rated")).isTrue();
        assertThat(document.getDate("ended_at").toInstant()).isEqualTo(T0);
        assertThat(document.getList("participants", Document.class)).hasSize(2);
    }

    @Test
    @DisplayName("a read-only store refuses to write")
    void readOnly_refusesWrites() {
        TomlSecretsConfig config = new TomlSecretsConfig();
        config.database.readOnly = true;
        MongoMatchStore readOnly = new MongoMatchStore(database, config);

        assertThatThrownBy(() -> readOnly.record(match("duel", "m1", T0, "a", "b")))
                .isInstanceOf(IllegalStateException.class);
    }
}
