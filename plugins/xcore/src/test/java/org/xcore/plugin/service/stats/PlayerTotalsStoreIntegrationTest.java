package org.xcore.plugin.service.stats;

import com.mongodb.client.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.*;
import org.xcore.plugin.database.MongoTransactions;
import org.xcore.plugin.database.migration.V8__CreatePlayerTotals;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.xcore.plugin.service.stats.ParticipantOutcomes.number;

@Testcontainers(disabledWithoutDocker = true)
class PlayerTotalsStoreIntegrationTest {
    @Container static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7.0")
            .withExposedPorts(27017).withCommand("--replSet", "rs0", "--bind_ip_all");
    static MongoClient client;
    MongoDatabase db;
    PlayerTotalsStore store;
    StorageExecutor storage;
    @BeforeAll static void connect() throws Exception {
        var initialized = MONGO.execInContainer("mongosh", "--quiet", "--eval",
                "rs.initiate({_id:'rs0',members:[{_id:0,host:'localhost:27017'}]})");
        assertThat(initialized.getExitCode()).isZero();
        org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*Transition to primary complete.*", 1)
                .withStartupTimeout(java.time.Duration.ofSeconds(40)).waitUntilReady(MONGO);
        client = MongoClients.create("mongodb://" + MONGO.getHost() + ":" + MONGO.getMappedPort(27017)
                + "/?replicaSet=rs0&directConnection=true&serverSelectionTimeoutMS=30000");
    }
    @AfterAll static void closeClient() { if (client != null) client.close(); }
    @BeforeEach void setup() {
        db = client.getDatabase("stats_test"); db.drop();
        new V8__CreatePlayerTotals().up(db);
        db.getCollection("games_v2").createIndex(new Document("match_id", 1), new com.mongodb.client.model.IndexOptions().unique(true).sparse(true));
        var config = new TomlXcoreConfig(); config.statistics.enabled = true;
        storage = new StorageExecutor(4);
        store = new PlayerTotalsStore(db, new MongoTransactions(client), storage, config, new TomlSecretsConfig());
    }
    @AfterEach void stopStorage() { storage.shutdown(); }
    private Document game(String uuid, long end, boolean win) {
        var game = ParticipantOutcomesTest.game(end);
        game.put("payload_hash", game.getString("match_id"));
        game.put("player_stats", List.of(ParticipantOutcomesTest.row(1_000, 0, true, win ? "blue" : "red").append("uuid", uuid)));
        return game;
    }
    private void ready() { while (store.rebuildOne()) { } }
    @Test void liveReplayAndOldDuplicateAgree() {
        var first = game("a", 101_000, true); store.write(first); ready();
        for (int i = 0; i < 15; i++) store.write(game("a", 201_000 + i * 100_000, i % 2 == 0));
        var before = store.find("a");
        assertThat(store.write(first)).isFalse();
        assertThat(number(store.find("a").get("all", Document.class), "games")).isEqualTo(16);
        store.invalidate(null, "a"); ready();
        assertThat(store.find("a").get("all")).isEqualTo(before.get("all"));
        assertThat(store.find("a").get("recent")).isEqualTo(before.get("recent"));
    }
    @Test void lateArrivalReordersStreakAndMergeRedirectsLateSnapshots() {
        store.write(game("a", 101_000, true)); ready();
        store.write(game("a", 301_000, true));
        store.write(game("a", 201_000, false)); ready();
        assertThat(number(store.find("a").get("all", Document.class), "streak")).isEqualTo(1);
        new MongoTransactions(client).runRequired(session -> {
            store.merge(session, "a", "b");
            db.getCollection("games_v2").updateMany(session, new Document("player_stats.uuid", "a"),
                    new Document("$set", new Document("player_stats.$[].uuid", "b")));
            return null;
        });
        store.write(game("a", 401_000, true)); ready();
        assertThat(number(store.find("a").get("all", Document.class), "games")).isZero();
        assertThat(number(store.find("b").get("all", Document.class), "games")).isEqualTo(4);
        assertThat(db.getCollection("games_v2").countDocuments(new Document("player_stats.uuid", "a"))).isZero();
    }
    @Test void readersWaitForTheBackfillAndFollowMergeRedirects() {
        // Until the first scan has enrolled everyone, a missing projection may still hide old games.
        assertThat(store.read("nobody").getString("status")).isEqualTo("pending");
        db.getCollection("settings").updateOne(new Document("_id", "player_totals_backfill"),
                new Document("$set", new Document("done", true)));
        assertThat(store.read("nobody")).isNull();

        store.write(game("a", 101_000, true)); ready();
        new MongoTransactions(client).runRequired(session -> {
            store.merge(session, "a", "b");
            db.getCollection("games_v2").updateMany(session, new Document("player_stats.uuid", "a"),
                    new Document("$set", new Document("player_stats.$[].uuid", "b")));
            return null;
        });
        ready();
        assertThat(store.read("a").getString("_id")).isEqualTo("b");
        assertThat(number(store.read("a").get("all", Document.class), "games")).isEqualTo(1);

        db.getCollection("player_totals").updateOne(new Document("_id", "b"),
                new Document("$set", new Document("redirect_uuid", "a")));
        assertThatThrownBy(() -> store.read("a")).isInstanceOf(IllegalStateException.class);
    }
    @Test void simultaneousWritersAndRebuildDoNotLoseGames() throws Exception {
        store.write(game("a", 101_000, true)); ready();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var writes = new ArrayList<Future<?>>();
            for (int i = 0; i < 10; i++) {
                final int n = i;
                writes.add(executor.submit(() -> store.write(game("a", 201_000 + n * 100_000, true))));
            }
            writes.add(executor.submit(() -> { store.invalidate(null, "a"); store.rebuildOne(); }));
            for (var result : writes) result.get(30, TimeUnit.SECONDS);
        }
        ready();
        assertThat(number(store.find("a").get("all", Document.class), "games")).isEqualTo(11);
        assertThat(number(store.find("a").get("all", Document.class), "streak")).isEqualTo(11);
    }
    @Test void simultaneousFirstGamesAndExpiredLeaseAreRecoverable() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> store.write(game("new", 101_000, true)));
            var second = executor.submit(() -> store.write(game("new", 201_000, true)));
            first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS);
        }
        db.getCollection("player_totals").updateOne(new Document("_id", "new"),
                new Document("$set", new Document("rebuild.lease_token", "dead-worker").append("rebuild.lease_until", 1L)));
        ready();
        assertThat(number(store.find("new").get("all", Document.class), "games")).isEqualTo(2);
        var stale = db.getCollection("player_totals").updateOne(new Document("_id", "new").append("rebuild.lease_token", "dead-worker"),
                new Document("$set", new Document("status", "pending")));
        assertThat(stale.getMatchedCount()).isZero();
    }
    @Test void mergedAccountsInOneGameAreOneGameAndConflictsAreExcluded() {
        var game = game("a", 101_000, true);
        game.put("player_stats", List.of(ParticipantOutcomesTest.row(1_000, 0, true, "blue").append("uuid", "a"),
                ParticipantOutcomesTest.row(1_000, 0, true, "blue").append("uuid", "b")));
        store.write(game); ready();
        new MongoTransactions(client).runRequired(session -> {
            store.merge(session, "a", "b");
            db.getCollection("games_v2").updateMany(session, new Document("player_stats.uuid", "a"),
                    new Document("$set", new Document("player_stats.$[p].uuid", "b")),
                    new com.mongodb.client.model.UpdateOptions().arrayFilters(List.of(new Document("p.uuid", "a"))));
            return null;
        });
        ready();
        assertThat(number(store.find("b").get("all", Document.class), "games")).isEqualTo(1);
        assertThat(number(store.find("b").get("all", Document.class), "blocks_built")).isEqualTo(10);
    }

    @Test void repositorySnapshotsUseTheRealBsonContractAndAreDetached() throws Exception {
        var registry = org.bson.codecs.configuration.CodecRegistries.fromRegistries(
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry(),
                org.bson.codecs.configuration.CodecRegistries.fromProviders(org.bson.codecs.pojo.PojoCodecProvider.builder().automatic(true).build()));
        var typed = db.withCodecRegistry(registry);
        var repository = new org.xcore.plugin.database.repository.GameDataRepository(typed, null,
                new TomlSecretsConfig(), store, storage, null);
        var player = org.xcore.plugin.model.PlayerGameStats.builder().uuid("typed").participantUuid("typed")
                .joinTime(1_000).playedToEnd(true).isWinner(true).finalTeam("blue")
                .presenceIntervals(new ArrayList<>(List.of(org.xcore.plugin.model.TeamGameData.builder()
                        .joinTime(1_000).leaveTime(101_000).team("blue").build()))).build();
        var game = org.xcore.plugin.model.GameData.builder().matchId("typed-contract").startGameTime(1_000).endGameTime(101_000)
                .winningTeam("blue").countedInStats(true).statsCategory(org.xcore.plugin.model.enums.GameStatsCategory.PVP)
                .playerStats(new ArrayList<>(List.of(player))).build();
        var frozen = repository.snapshot(game);
        player.uuid = "changed-after-snapshot";
        assertThat(store.write(frozen)).isTrue(); ready();
        assertThat(number(store.find("typed").get("all", Document.class), "wins")).isEqualTo(1);
        var raw = db.getCollection("games_v2").find(new Document("match_id", "typed-contract")).first();
        assertThat(raw.getList("player_stats", Document.class).getFirst().getString("outcome")).isEqualTo("WIN");
        assertThat(store.write(frozen)).isFalse();

        // The stored match, owner and outcome included, decoded and sent again is still the same match.
        merge("typed", "survivor");
        var stored = typed.getCollection("games_v2", org.xcore.plugin.model.GameData.class)
                .find(new Document("match_id", "typed-contract")).first();
        assertThat(stored.playerStats.getFirst().outcome).isEqualTo("WIN");
        assertThat(repository.snapshot(stored).getString("payload_hash")).isEqualTo(frozen.getString("payload_hash"));
        assertThat(store.write(repository.snapshot(stored))).isFalse();
        // Different participation under the same ID is still refused.
        stored.playerStats.getFirst().presenceIntervals.getFirst().leaveTime = 51_000;
        assertThatThrownBy(() -> store.write(repository.snapshot(stored))).isInstanceOf(com.mongodb.MongoWriteException.class);
    }

    private void merge(String source, String target) {
        new MongoTransactions(client).runRequired(session -> {
            store.merge(session, source, target);
            db.getCollection("games_v2").updateMany(session, new Document("player_stats.uuid", source),
                    new Document("$set", new Document("player_stats.$[p].uuid", target)),
                    new com.mongodb.client.model.UpdateOptions().arrayFilters(List.of(new Document("p.uuid", source))));
            return null;
        });
    }
    @Test void chainedAndRepeatedMergesFollowRedirectsAndCyclesAreRefused() {
        store.write(game("a", 101_000, true));
        merge("a", "b");
        merge("a", "b");
        store.write(game("b", 201_000, true));
        merge("b", "c");
        store.write(game("a", 301_000, false)); ready();
        assertThat(number(store.find("c").get("all", Document.class), "games")).isEqualTo(3);
        assertThat(number(store.find("a").get("all", Document.class), "games")).isZero();
        assertThat(store.find("a").getString("redirect_uuid")).isEqualTo("b");

        assertThatThrownBy(() -> merge("c", "a")).hasMessageContaining("merged into the source");
        assertThat(store.find("c").getString("redirect_uuid")).isNull();
    }

    @Test void waitingPlayersKeepTheirPlaceInTheRebuildQueue() {
        store.write(game("a", 101_000, true)); ready();
        store.invalidate(null, "a");
        db.getCollection("player_totals").updateOne(new Document("_id", "a"),
                new Document("$set", new Document("rebuild.queued_at", 1L)));
        store.invalidate(null, "a");
        assertThat(number(store.find("a").get("rebuild", Document.class), "queued_at")).isEqualTo(1);
        ready();
        assertThat(store.find("a").get("rebuild", Document.class)).doesNotContainKey("queued_at");
    }
    @Test void olderServersLeaveTotalsOfNewerRulesAlone() {
        store.write(game("a", 101_000, true)); ready();
        db.getCollection("player_totals").updateOne(new Document("_id", "a"),
                new Document("$set", new Document("rules_version", ParticipantOutcomes.RULES_VERSION + 1)));
        store.invalidate(null, "a");
        assertThat(store.rebuildOne()).isFalse();
        assertThat(number(store.find("a"), "rules_version")).isEqualTo(ParticipantOutcomes.RULES_VERSION + 1);
    }
    @Test void failedRebuildsBackOffExponentially() {
        var totals = db.getCollection("player_totals");
        var broken = game("a", 101_000, true);
        broken.getList("player_stats", Document.class).getFirst().put("presence_intervals", "broken");
        db.getCollection("games_v2").insertOne(broken);
        store.invalidate(null, "a");
        for (long expected : new long[]{30_000, 60_000}) {
            long before = System.currentTimeMillis();
            assertThatThrownBy(() -> store.rebuildOne()).isInstanceOf(RuntimeException.class);
            var rebuild = totals.find(new Document("_id", "a")).first().get("rebuild", Document.class);
            assertThat(number(rebuild, "retry_at") - before).isBetween(expected, expected + 5_000);
            assertThat(store.rebuildOne()).isFalse();
            totals.updateOne(new Document("_id", "a"), new Document("$set", new Document("rebuild.retry_at", 0L)));
        }
    }

    @Test void failedTransactionNeverKeepsOnlyTheGame() {
        store.invalidate(null, "a");
        db.getCollection("player_totals").updateOne(new Document("_id", "a"), new Document("$set", new Document("revision", "invalid")));
        var game = game("a", 101_000, true);
        assertThatThrownBy(() -> store.write(game)).isInstanceOf(RuntimeException.class);
        assertThat(db.getCollection("games_v2").countDocuments()).isZero();
    }
}
