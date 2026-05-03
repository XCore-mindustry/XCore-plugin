package org.xcore.protocol.generated;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerHeartbeatV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ActorRefV1ActorType;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class ProtocolCanonicalFixtureTest {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private static final String ADMIN_ACCESS_GRANT_FIXTURE = "spec/fixtures/valid/discord/discord.admin-access.changed.command.v1.grant.json";
    private static final String ADMIN_ACCESS_REVOKE_FIXTURE = "spec/fixtures/valid/discord/discord.admin-access.changed.command.v1.revoke.json";
    private static final String UNLINK_FIXTURE = "spec/fixtures/valid/discord/discord.unlink.command.v1.json";
    private static final String HEARTBEAT_FIXTURE = "spec/fixtures/valid/chat/server.heartbeat.v1.json";

    @Test
    void adminAccessGrantFixtureParsesAndRoundTripsCanonically() throws IOException {
        assertAdminAccessFixtureRoundTrip(ADMIN_ACCESS_GRANT_FIXTURE, true);
    }

    @Test
    void adminAccessRevokeFixtureParsesAndRoundTripsCanonically() throws IOException {
        assertAdminAccessFixtureRoundTrip(ADMIN_ACCESS_REVOKE_FIXTURE, false);
    }

    @Test
    void unlinkFixtureParsesAndRoundTripsCanonically() throws IOException {
        var fixture = readFixture(UNLINK_FIXTURE);
        assertCanonicalIdentity(fixture, "discord.unlink.command", 1);
        assertNoLegacyKeys(fixture);
        assertKeySet(fixture, Set.of("messageType", "messageVersion", "player", "discord", "actor", "server", "requestedAt"));

        var player = getMap(fixture, "player");
        assertPlayerRefShape(player);

        var discord = getMap(fixture, "discord");
        assertDiscordIdentityShape(discord);

        var actor = getMap(fixture, "actor");
        assertActorShape(actor, true);

        var model = new DiscordUnlinkCommandV1(
            parsePlayerRef(player),
            parseDiscordIdentityRef(discord),
            parseActorRef(actor),
            getString(fixture, "server"),
            getString(fixture, "requestedAt")
        );

        var canonicalPayload = model.toPayload();
        assertCanonicalIdentity(canonicalPayload, "discord.unlink.command", 1);
        assertEquals(normalizeMap(fixture), normalizeMap(canonicalPayload));
    }

    @Test
    void heartbeatFixtureParsesAndRoundTripsCanonically() throws IOException {
        var fixture = readFixture(HEARTBEAT_FIXTURE);
        assertCanonicalIdentity(fixture, "server.heartbeat", 1);
        assertNoLegacyKeys(fixture);
        assertKeySet(fixture, Set.of("messageType", "messageVersion", "serverName", "discordChannelId", "players", "maxPlayers", "version", "host", "port"));

        var model = new ServerHeartbeatV1(
            getString(fixture, "serverName"),
            getLong(fixture, "discordChannelId"),
            getInt(fixture, "players"),
            getInt(fixture, "maxPlayers"),
            getString(fixture, "version"),
            getOptionalString(fixture, "host"),
            getOptionalInt(fixture, "port")
        );

        var canonicalPayload = model.toPayload();
        assertCanonicalIdentity(canonicalPayload, "server.heartbeat", 1);
        assertEquals(normalizeMap(fixture), normalizeMap(canonicalPayload));
    }

    private static void assertAdminAccessFixtureRoundTrip(String fixturePath, boolean expectedAdmin) throws IOException {
        var fixture = readFixture(fixturePath);
        assertCanonicalIdentity(fixture, "discord.admin-access.changed.command", 1);
        assertNoLegacyKeys(fixture);
        assertKeySet(fixture, Set.of("messageType", "messageVersion", "player", "discord", "admin", "source", "actor", "reason", "server", "occurredAt"));

        var player = getMap(fixture, "player");
        assertPlayerRefShape(player);

        var discord = getMap(fixture, "discord");
        assertDiscordIdentityShape(discord);

        var source = getMap(fixture, "source");
        assertActorShape(source, false);

        var actor = getMap(fixture, "actor");
        assertActorShape(actor, true);

        assertEquals(expectedAdmin, getBoolean(fixture, "admin"));
        assertNotNull(getString(fixture, "reason"));
        assertNotNull(getString(fixture, "server"));
        assertNotNull(getString(fixture, "occurredAt"));

        var model = new DiscordAdminAccessChangedCommandV1(
            parsePlayerRef(player),
            parseDiscordIdentityRef(discord),
            getBoolean(fixture, "admin"),
            parseActorRef(source),
            parseActorRef(actor),
            getString(fixture, "reason"),
            getString(fixture, "server"),
            getString(fixture, "occurredAt")
        );

        var canonicalPayload = model.toPayload();
        assertCanonicalIdentity(canonicalPayload, "discord.admin-access.changed.command", 1);
        assertEquals(normalizeMap(fixture), normalizeMap(canonicalPayload));
    }

    private static Map<String, Object> readFixture(String relativePath) throws IOException {
        var fixturePath = resolveFixturePath(relativePath);
        try (var reader = Files.newBufferedReader(fixturePath)) {
            return GSON.fromJson(reader, MAP_TYPE);
        }
    }

    private static Path resolveFixturePath(String relativePath) {
        var candidates = List.of(
            Path.of(relativePath),
            Path.of("..", "..", relativePath),
            Path.of("..", "..", "..", relativePath)
        );

        for (Path candidate : candidates) {
            var normalized = candidate.normalize();
            if (Files.exists(normalized)) {
                return normalized;
            }
        }

        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            var candidate = current.resolve(relativePath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        fail("Unable to resolve fixture path: " + relativePath);
        throw new IllegalStateException("Unreachable");
    }

    private static void assertCanonicalIdentity(Map<String, Object> payload, String expectedType, int expectedVersion) {
        assertEquals(expectedType, getString(payload, "messageType"));
        assertEquals(expectedVersion, getInt(payload, "messageVersion"));
    }

    private static void assertPlayerRefShape(Map<String, Object> player) {
        assertKeySet(player, Set.of("playerUuid", "playerName", "playerPid", "ip"));
        assertFalse(player.containsKey("uuid"), "no legacy 'uuid' key");
        assertFalse(player.containsKey("name"), "no legacy 'name' key");
        assertFalse(player.containsKey("pid"), "no legacy 'pid' key");
    }

    private static void assertDiscordIdentityShape(Map<String, Object> discord) {
        assertKeySet(discord, Set.of("discordId", "discordUsername"));
    }

    private static void assertActorShape(Map<String, Object> actor, boolean requireDiscordId) {
        if (requireDiscordId) {
            assertKeySet(actor, Set.of("actorName", "actorDiscordId", "actorType"));
        } else {
            assertKeySet(actor, Set.of("actorName", "actorType"));
            assertFalse(actor.containsKey("actorDiscordId"), "system source must not include actorDiscordId");
        }
    }

    private static void assertKeySet(Map<String, Object> payload, Set<String> expectedKeys) {
        assertEquals(expectedKeys, payload.keySet());
    }

    @SuppressWarnings("unchecked")
    private static void assertNoLegacyKeys(Map<String, Object> payload) {
        var legacyKeys = Set.of(
            "player_uuid",
            "player_name",
            "player_pid",
            "discord_id",
            "discord_username",
            "actor_name",
            "actor_discord_id",
            "actor_type",
            "server_name",
            "max_players",
            "requested_at",
            "occurred_at"
        );

        for (var entry : payload.entrySet()) {
            var key = entry.getKey();
            assertFalse(key.contains("_"), "fixture contains legacy snake_case key: " + key);
            assertFalse(legacyKeys.contains(key), "fixture contains legacy key: " + key);

            var value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                assertNoLegacyKeys((Map<String, Object>) nestedMap);
            } else if (value instanceof List<?> nestedList) {
                for (Object item : nestedList) {
                    if (item instanceof Map<?, ?> listMap) {
                        assertNoLegacyKeys((Map<String, Object>) listMap);
                    }
                }
            }
        }
    }

    private static PlayerRefV1 parsePlayerRef(Map<String, Object> payload) {
        return new PlayerRefV1(
            getString(payload, "playerUuid"),
            getOptionalInt(payload, "playerPid"),
            getString(payload, "playerName"),
            getOptionalString(payload, "ip")
        );
    }

    private static DiscordIdentityRefV1 parseDiscordIdentityRef(Map<String, Object> payload) {
        return new DiscordIdentityRefV1(
            getString(payload, "discordId"),
            getOptionalString(payload, "discordUsername")
        );
    }

    private static ActorRefV1 parseActorRef(Map<String, Object> payload) {
        return new ActorRefV1(
            getString(payload, "actorName"),
            getOptionalString(payload, "actorDiscordId"),
            ActorRefV1ActorType.fromValue(getString(payload, "actorType"))
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertNotNull(value, () -> "Expected map value for key '" + key + "'");
        assertInstanceOf(Map.class, value, () -> "Expected map value for key '" + key + "'");
        return (Map<String, Object>) value;
    }

    private static String getString(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertInstanceOf(String.class, value, () -> "Expected string value for key '" + key + "'");
        return (String) value;
    }

    private static String getOptionalString(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value == null) {
            return null;
        }
        assertInstanceOf(String.class, value, () -> "Expected string value for key '" + key + "'");
        return (String) value;
    }

    private static boolean getBoolean(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertInstanceOf(Boolean.class, value, () -> "Expected boolean value for key '" + key + "'");
        return (Boolean) value;
    }

    private static int getInt(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertInstanceOf(Number.class, value, () -> "Expected numeric value for key '" + key + "'");
        return ((Number) value).intValue();
    }

    private static Integer getOptionalInt(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value == null) {
            return null;
        }
        assertInstanceOf(Number.class, value, () -> "Expected numeric value for key '" + key + "'");
        return ((Number) value).intValue();
    }

    private static long getLong(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        assertInstanceOf(Number.class, value, () -> "Expected numeric value for key '" + key + "'");
        return ((Number) value).longValue();
    }

    private static Map<String, Object> normalizeMap(Map<String, Object> payload) {
        return GSON.fromJson(GSON.toJson(payload), MAP_TYPE);
    }
}
