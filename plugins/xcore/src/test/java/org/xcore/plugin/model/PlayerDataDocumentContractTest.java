package org.xcore.plugin.model;

import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.enums.IdentityDisplayMode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The players document as the plugin writes it. XCore-discord-bot reads the same collection and
 * maps a copy of contract/player_document.json in its tests/fixtures, so a renamed field breaks one
 * side's tests instead of production. Change both copies together.
 */
class PlayerDataDocumentContractTest {
    @Test
    void encodesTheDocumentTheBotReads() throws IOException {
        var codecs = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        var document = new BsonDocument();
        codecs.get(PlayerData.class).encode(new BsonDocumentWriter(document), player(), EncoderContext.builder().build());

        String expected;
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/contract/player_document.json"))) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Relaxed JSON reads 1 the same whether it was stored as int or long, as the bot sees it.
        var relaxed = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build();
        assertThat(BsonDocument.parse(document.toJson(relaxed))).isEqualTo(BsonDocument.parse(expected));
    }

    // Every persisted field differs from its default; collections hold one item so their order is fixed.
    private static PlayerData player() {
        var player = new PlayerData();
        player.uuid = "uuid-contract";
        player.pid = 42;
        player.username = "contract_user";
        player.canChangeUsername = true;
        player.identityDisplayMode = IdentityDisplayMode.USERNAME;
        player.ip = "203.0.113.7";
        player.nickname = "[red]Contract";
        player.customNickname = "Custom";
        player.description = "About me";
        player.password = "$2a$10$hash";
        player.deviceTokenHashes = Set.of("token-hash");
        player.deviceTokens = Map.of("token-hash", 1800000000000L);
        player.language = "uk";
        player.translatorLanguage = "en";
        player.globalChatVisible = false;
        player.discordRelayVisible = false;
        player.legacyPvpRating = 1500;
        player.hexedRank = 3;
        player.hexedPoints = 9;
        player.totalPlayTime = 15;
        player.eventVotes = Map.of("event-1", true);
        player.mapVotes = Map.of("map-1", false);
        player.blockedPrivateUuids = Set.of("uuid-blocked");
        player.unlockedBadges = Set.of("translator");
        player.activeBadge = "translator";
        player.badgeSymbolColorMode = "team";
        player.admin = true;
        player.adminSource = "DISCORD";
        player.leaderboard = false;
        player.discordId = "123456789012345678";
        player.discordUsername = "contract#1";
        player.discordLinkedAt = 1700000000001L;
        player.online = true;
        player.onlineSince = 1700000000002L;
        player.onlineServer = "survival";
        player.createdModelTime = 1700000000003L;
        player.editModelTime = 1700000000004L;
        player.deleteModelTime = 1700000000005L;
        player.isVisible = false;
        player.versionModel = 2;
        return player;
    }
}
