package org.xcore.plugin.security.ingress.checks;

import arc.files.Fi;
import arc.util.Strings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.ospx.flubundle.Bundle;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import org.bson.Document;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.security.ingress.AccessResult;

import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class BanCheckIntegrationTest {
    @Container
    private static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017);

    private static MongoClient client;
    private MongoDatabase database;
    private BanDataRepository repository;
    private BanCheck check;
    private IngressChecksTestSupport.VarsState varsState;
    private StorageExecutor storage;
    private Administration admins;

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
        varsState = IngressChecksTestSupport.captureVarsState();
        admins = mock(Administration.class);
        var server = mock(NetServer.class);
        server.admins = admins;
        Vars.netServer = server;
        database = client.getDatabase("ban_check_identity_test").withCodecRegistry(fromRegistries(
                getDefaultCodecRegistry(), fromProviders(PojoCodecProvider.builder().automatic(true).build())));
        database.drop();
        var config = new TomlSecretsConfig();
        config.externalLinks.discordUrl = "https://discord.example";
        repository = new BanDataRepository(database, config);
        database.getCollection("players").insertOne(new Document("uuid", "uuid-1").append("pid", 567));
        var bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi("src/main/resources/bundles"));
        storage = new StorageExecutor(2);
        check = new BanCheck(repository, bundle, config, new Async(storage, Runnable::run));
    }

    @AfterEach
    void tearDown() {
        storage.shutdown();
        varsState.restore();
    }

    @Test
    void nextJoinHealsAnOldWritersReplacementAndExistingIdSurvivesLaterJoins() {
        var bans = database.getCollection("bans");
        bans.insertOne(legacyBan("uuid-1"));

        assertThat(reason()).contains("Ban: B-1", "Player ID: 567", "Quote B-1 in your appeal.", "Time left:", "Expires:");
        assertThat(bans.find().first().get("ban_id")).isInstanceOf(Integer.class).isEqualTo(1);
        assertThat(reason()).contains("Ban: B-1");
        assertThat(database.getCollection("counters").find().first().getInteger("seq")).isEqualTo(1);
        var id = bans.find().first().get("_id");
        bans.replaceOne(new Document("_id", id), legacyBan("uuid-1"));

        assertThat(reason()).contains("Ban: B-2", "Player ID: 567");
        assertThat(bans.find().first().getInteger("ban_id")).isEqualTo(2);
        assertThat(reason()).contains("Ban: B-2");
        assertThat(database.getCollection("counters").find().first().getInteger("seq")).isEqualTo(2);
    }

    @Test
    void permanentDatabaseBanStillShowsBanIdentityWithoutAnExpiry() {
        var ban = legacyBan("uuid-1");
        ban.remove("expire_date");
        database.getCollection("bans").insertOne(ban);

        assertThat(reason()).contains("permanently banned", "Ban: B-1", "Player ID: 567", "Permanent")
                .doesNotContain("temporarily", "Expires:", "Time left:");
    }

    @Test
    void addressBanOfAnUnknownPlayerShowsUnknownPid() {
        database.getCollection("bans").insertOne(legacyBan(null).append("ip", "1.2.3.4"));

        assertThat(reason()).contains("Ban: B-1", "Player ID: -");
    }

    @Test
    void vanillaBanShowsKnownPidWithoutDatabaseBanNumber() {
        when(admins.isIPBanned("1.2.3.4")).thenReturn(true);

        assertThat(reason()).contains("Player ID: 567").doesNotContain("B-");
        assertThat(database.getCollection("counters").countDocuments()).isZero();
    }

    @Test
    void vanillaBanOfAnUnknownPlayerShowsDash() {
        database.getCollection("players").drop();
        when(admins.isIPBanned("1.2.3.4")).thenReturn(true);

        assertThat(reason()).contains("Player ID: -");
    }

    private String reason() {
        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.2.3.4"),
                IngressChecksTestSupport.newPacket());
        assertThat(result).isInstanceOf(AccessResult.Denied.class);
        return Strings.stripColors(((AccessResult.Denied) result).reason());
    }

    private static Document legacyBan(String uuid) {
        return new Document("uuid", uuid).append("name", "Player").append("admin_name", "Admin")
                .append("reason", "Griefing").append("expire_date", Date.from(Instant.now().plusSeconds(3600)));
    }
}
