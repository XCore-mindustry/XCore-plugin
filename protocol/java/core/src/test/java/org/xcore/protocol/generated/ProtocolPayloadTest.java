package org.xcore.protocol.generated;

import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.chat.ChatMessages.*;
import org.xcore.protocol.generated.messages.discord.DiscordLinkStatusChangedV1Action;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.*;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.*;
import org.xcore.protocol.generated.messages.maps.MapsMessages.*;
import org.xcore.protocol.generated.shared.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolPayloadTest {

    // ── Shared types ────────────────────────────────────────────────

    @Test
    void voteKickParticipantCanonicalFieldNames() {
        var p = new VoteKickParticipantV1("foo", 12, "123");
        var payload = p.toPayload();

        assertEquals("foo", payload.get("playerName"));
        assertEquals(12, payload.get("playerPid"));
        assertEquals("123", payload.get("discordId"));
        assertFalse(payload.containsKey("name"), "no legacy 'name' key");
        assertFalse(payload.containsKey("pid"), "no legacy 'pid' key");
    }

    @Test
    void voteKickParticipantOmitsNullOptionals() {
        var p = new VoteKickParticipantV1("minimal", null, null);
        var payload = p.toPayload();

        assertTrue(payload.containsKey("playerName"));
        assertFalse(payload.containsKey("playerPid"));
        assertFalse(payload.containsKey("discordId"));
    }

    @Test
    void actorRefCanonicalFieldNames() {
        var actor = new ActorRefV1("admin", "123", ActorRefV1ActorType.DISCORD);
        var payload = actor.toPayload();

        assertEquals("admin", payload.get("actorName"));
        assertEquals("123", payload.get("actorDiscordId"));
        assertEquals("discord", payload.get("actorType"));
    }

    @Test
    void actorRefOmitsNullOptionals() {
        var actor = new ActorRefV1("system", null, null);
        var payload = actor.toPayload();

        assertEquals("system", payload.get("actorName"));
        assertFalse(payload.containsKey("actorDiscordId"));
        assertFalse(payload.containsKey("actorType"));
    }

    @Test
    void playerRefCanonicalFieldNames() {
        var player = new PlayerRefV1("uuid-1", 12, "tester", "1.2.3.4");
        var payload = player.toPayload();

        assertEquals("uuid-1", payload.get("playerUuid"));
        assertEquals(12, payload.get("playerPid"));
        assertEquals("tester", payload.get("playerName"));
        assertEquals("1.2.3.4", payload.get("ip"));
    }

    @Test
    void discordIdentityRefCanonicalFieldNames() {
        var discord = new DiscordIdentityRefV1("123", "user#1");
        var payload = discord.toPayload();

        assertEquals("123", payload.get("discordId"));
        assertEquals("user#1", payload.get("discordUsername"));
    }

    // ── Chat messages ────────────────────────────────────────────────

    @Test
    void chatMessagePayloadIncludesMessageIdentity() {
        var msg = new ChatMessageV1("tester", "hello", "alpha");
        var payload = msg.toPayload();

        assertEquals("chat.message", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));
        assertEquals("tester", payload.get("authorName"));
        assertEquals("hello", payload.get("message"));
        assertEquals("alpha", payload.get("server"));
    }

    @Test
    void chatGlobalPayloadIncludesMessageIdentity() {
        var msg = new ChatGlobalV1("tester", "gg", "alpha");
        var payload = msg.toPayload();

        assertEquals("chat.global", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));
    }

    @Test
    void playerJoinLeavePayloadIncludesMessageIdentity() {
        var event = new PlayerJoinLeaveV1("tester #1", "alpha", true);
        var payload = event.toPayload();

        assertEquals("player.join-leave", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));
        assertEquals("tester #1", payload.get("playerName"));
        assertEquals("alpha", payload.get("server"));
        assertEquals(true, payload.get("joined"));
    }

    @Test
    void serverActionPayloadIncludesMessageIdentity() {
        var event = new ServerActionV1("Server loaded", "alpha");
        var payload = event.toPayload();

        assertEquals("server.action", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));
        assertEquals("Server loaded", payload.get("message"));
        assertEquals("alpha", payload.get("server"));
    }

    @Test
    void serverHeartbeatPayloadIncludesMessageIdentity() {
        // ServerHeartbeatV1(serverName, discordChannelId:long, players, maxPlayers, version, host, port)
        var event = new ServerHeartbeatV1("alpha", 0L, 5, 20, "v7", null, null);
        var payload = event.toPayload();

        assertEquals("server.heartbeat", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));
        assertEquals("alpha", payload.get("serverName"));
        assertEquals(0L, payload.get("discordChannelId"));
        assertEquals(5, payload.get("players"));
        assertEquals(20, payload.get("maxPlayers"));
        assertEquals("v7", payload.get("version"));
    }

    @Test
    void serverHeartbeatOmitsNullHostAndPort() {
        var event = new ServerHeartbeatV1("alpha", 0L, 5, 20, "v7", null, null);
        var payload = event.toPayload();

        assertFalse(payload.containsKey("host"));
        assertFalse(payload.containsKey("port"));
    }

    @Test
    void serverHeartbeatIncludesHostAndPortWhenSet() {
        var event = new ServerHeartbeatV1("alpha", 0L, 5, 20, "v7", "127.0.0.1", 6567);
        var payload = event.toPayload();

        assertEquals("127.0.0.1", payload.get("host"));
        assertEquals(6567, payload.get("port"));
    }

    // ── Moderation messages ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void moderationBanCreatedNestedRefs() {
        var target = new PlayerRefV1("uuid-1", 12, "bad", "1.2.3.4");
        var actor = new ActorRefV1("admin", "123", ActorRefV1ActorType.DISCORD);
        var expiration = new ExpirationInfoV1("2026-01-01T00:00:00Z", false);
        var ban = new ModerationBanCreatedV1(target, actor, "spam", expiration, "alpha", "2026-01-01T00:00:00Z");
        var payload = ban.toPayload();

        assertEquals("moderation.ban.created", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));

        var targetPayload = (Map<String, Object>) payload.get("target");
        assertEquals("uuid-1", targetPayload.get("playerUuid"));
        assertEquals("bad", targetPayload.get("playerName"));

        var actorPayload = (Map<String, Object>) payload.get("actor");
        assertEquals("admin", actorPayload.get("actorName"));
        assertEquals("123", actorPayload.get("actorDiscordId"));
        assertEquals("discord", actorPayload.get("actorType"));

        var expPayload = (Map<String, Object>) payload.get("expiration");
        assertEquals("2026-01-01T00:00:00Z", expPayload.get("expiresAt"));
        assertEquals(false, expPayload.get("permanent"));

        assertEquals("spam", payload.get("reason"));
        assertEquals("alpha", payload.get("server"));
        assertEquals("2026-01-01T00:00:00Z", payload.get("occurredAt"));

        // No legacy flat keys
        assertFalse(payload.containsKey("uuid"));
        assertFalse(payload.containsKey("name"));
        assertFalse(payload.containsKey("adminName"));
        assertFalse(payload.containsKey("expire"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void moderationMuteCreatedNestedRefs() {
        var target = new PlayerRefV1("uuid-2", 24, "quiet", null);
        var actor = new ActorRefV1("mod", null, ActorRefV1ActorType.PLAYER);
        var mute = new ModerationMuteCreatedV1(target, actor, "spam", null, "alpha", "2026-01-01T00:00:00Z");
        var payload = mute.toPayload();

        assertEquals("moderation.mute.created", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));

        var targetPayload = (Map<String, Object>) payload.get("target");
        assertEquals("quiet", targetPayload.get("playerName"));

        var actorPayload = (Map<String, Object>) payload.get("actor");
        assertEquals("mod", actorPayload.get("actorName"));
        assertEquals("player", actorPayload.get("actorType"));

        assertNull(payload.get("expiration"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void moderationVoteKickCreatedParticipantsCanonical() {
        var target = new PlayerRefV1("uuid-3", 42, "sus", null);
        var starter = new ActorRefV1("starter", null, ActorRefV1ActorType.PLAYER);
        var votesFor = List.of(
            new VoteKickParticipantV1("voter1", 1, "disc1"),
            new VoteKickParticipantV1("voter2", 2, null)
        );
        var votesAgainst = List.of(
            new VoteKickParticipantV1("voter3", 3, "disc3")
        );

        var vk = new ModerationVoteKickCreatedV1(
            target, starter, "griefing", votesFor, votesAgainst, "alpha", "2026-01-01T00:00:00Z"
        );
        var payload = vk.toPayload();

        assertEquals("moderation.vote-kick.created", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));

        var votesForList = (List<Map<String, Object>>) payload.get("votesFor");
        assertEquals(2, votesForList.size());
        assertEquals("voter1", votesForList.get(0).get("playerName"));
        assertEquals(1, votesForList.get(0).get("playerPid"));
        assertEquals("disc1", votesForList.get(0).get("discordId"));
        assertFalse(votesForList.get(0).containsKey("name"), "no legacy name key");
        assertFalse(votesForList.get(0).containsKey("pid"), "no legacy pid key");

        assertEquals("voter2", votesForList.get(1).get("playerName"));
        assertFalse(votesForList.get(1).containsKey("discordId"));

        var votesAgainstList = (List<Map<String, Object>>) payload.get("votesAgainst");
        assertEquals(1, votesAgainstList.size());
        assertEquals("voter3", votesAgainstList.get(0).get("playerName"));
    }

    // ── Discord messages ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void discordLinkStatusChangedNestedRefs() {
        var player = new PlayerRefV1("uuid-4", 56, "linker", null);
        var discord = new DiscordIdentityRefV1("disc-1", "linker#1");
        var event = new DiscordLinkStatusChangedV1(
            player, discord, DiscordLinkStatusChangedV1Action.LINKED, "alpha", "2026-01-01T00:00:00Z"
        );
        var payload = event.toPayload();

        assertEquals("discord.link.status-changed", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));

        var playerPayload = (Map<String, Object>) payload.get("player");
        assertEquals("linker", playerPayload.get("playerName"));
        assertEquals(56, playerPayload.get("playerPid"));

        var discordPayload = (Map<String, Object>) payload.get("discord");
        assertEquals("disc-1", discordPayload.get("discordId"));
        assertEquals("linker#1", discordPayload.get("discordUsername"));

        assertEquals("linked", payload.get("action"));
        assertEquals("alpha", payload.get("server"));
        assertEquals("2026-01-01T00:00:00Z", payload.get("occurredAt"));
    }

    // ── Maps messages ────────────────────────────────────────────────

    @Test
    void mapsListRequestIncludesMessageIdentity() {
        var req = new MapsListRequestV1("alpha");
        var payload = req.toPayload();

        assertEquals("maps.list.request", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));
        assertEquals("alpha", payload.get("server"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void mapsListResponseIncludesMessageIdentityAndMaps() {
        var entry = new MapEntryV1("map1", "map1.msav", "author", 200, 150, 1024000, 5, 1, 4, 0.8, 0.9, "pvp");
        var resp = new MapsListResponseV1("alpha", List.of(entry));
        var payload = resp.toPayload();

        assertEquals("maps.list.response", payload.get("messageType"));
        assertEquals(1, payload.get("messageVersion"));
        assertEquals("alpha", payload.get("server"));

        var maps = (List<Map<String, Object>>) payload.get("maps");
        assertEquals(1, maps.size());
        assertEquals("map1", maps.get(0).get("name"));
    }
}
