package org.xcore.protocol.generated;

import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.chat.ChatMessages.*;
import org.xcore.protocol.generated.messages.discord.DiscordLinkStatusChangedV1Action;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.*;
import org.xcore.protocol.generated.messages.maps.MapsMessages.*;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.*;
import org.xcore.protocol.generated.routes.MapsRoutes;
import org.xcore.protocol.generated.routes.ProtocolRoutes;
import org.xcore.protocol.generated.shared.*;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolRoutesTest {

    @Test
    void aggregateCatalogIncludesAllRoutes() {
        assertEquals(28, ProtocolRoutes.ROUTES_BY_MESSAGE.size(), "expected 28 total routes");
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("chat.message", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("maps.list.request", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("discord.link.status-changed", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("moderation.ban.created", 1)));
    }

    @Test
    void messageLookupWorksForEachFamily() {
        assertEquals("chat.message", ProtocolRoutes.routeFor("chat.message", 1).messageType());
        assertEquals("maps.list.request", ProtocolRoutes.routeFor("maps.list.request", 1).messageType());
        assertEquals("discord.link.status-changed", ProtocolRoutes.routeFor("discord.link.status-changed", 1).messageType());
        assertEquals("moderation.ban.created", ProtocolRoutes.routeFor("moderation.ban.created", 1).messageType());
    }

    @Test
    void payloadTypeLookupWorksForEachFamily() {
        var chatPayload = new ChatMessageV1("tester", "hello", "alpha");
        var chatRoute = ProtocolRoutes.routeFor(chatPayload);
        assertNotNull(chatRoute);
        assertEquals("chat.message", chatRoute.messageType());

        var mapsPayload = new MapsListRequestV1("alpha");
        var mapsRoute = ProtocolRoutes.routeFor(mapsPayload);
        assertNotNull(mapsRoute);
        assertEquals("maps.list.request", mapsRoute.messageType());

        var discordPayload = new DiscordLinkStatusChangedV1(
                new PlayerRefV1("uuid", 1, "name", null),
                new DiscordIdentityRefV1("disc", "user#1"),
                DiscordLinkStatusChangedV1Action.LINKED,
                "alpha",
                "2026-01-01T00:00:00Z"
        );
        var discordRoute = ProtocolRoutes.routeFor(discordPayload);
        assertNotNull(discordRoute);
        assertEquals("discord.link.status-changed", discordRoute.messageType());

        var moderationPayload = new ModerationBanCreatedV1(
                new PlayerRefV1("uuid", 1, "name", null),
                new ActorRefV1("admin", "123", ActorRefV1ActorType.DISCORD),
                "spam",
                new ExpirationInfoV1("2026-01-01T00:00:00Z", false),
                "alpha",
                "2026-01-01T00:00:00Z"
        );
        var moderationRoute = ProtocolRoutes.routeFor(moderationPayload);
        assertNotNull(moderationRoute);
        assertEquals("moderation.ban.created", moderationRoute.messageType());
    }

    @Test
    void rpcResponseDescriptorPreservedForMapsRoutes() {
        var protocolRoute = ProtocolRoutes.MAPS_LIST_REQUEST_V1;
        assertNotNull(protocolRoute.response());
        assertEquals("maps.list.response", protocolRoute.response().messageType());
        assertEquals(1, protocolRoute.response().messageVersion());
        assertEquals(MapsListResponseV1.class, protocolRoute.response().payloadType());

        var familyRoute = MapsRoutes.MAPS_LIST_REQUEST_V1;
        assertNotNull(familyRoute.response());
        assertEquals("maps.list.response", familyRoute.response().messageType());
    }
}
