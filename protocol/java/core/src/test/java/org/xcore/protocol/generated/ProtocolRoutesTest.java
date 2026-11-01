package org.xcore.protocol.generated;

import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.cases.CasesMessages.*;
import org.xcore.protocol.generated.messages.chat.ChatMessages.*;
import org.xcore.protocol.generated.messages.discord.DiscordLinkStatusChangedV1Action;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.*;
import org.xcore.protocol.generated.messages.maps.MapsMessages.*;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.*;
import org.xcore.protocol.generated.messages.rating.RatingMessages.*;
import org.xcore.protocol.generated.messages.sentinel.SentinelMessages.*;
import org.xcore.protocol.generated.routes.MapsRoutes;
import org.xcore.protocol.generated.routes.CasesRoutes;
import org.xcore.protocol.generated.routes.ProtocolRoutes;
import org.xcore.protocol.generated.shared.*;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolRoutesTest {

    @Test
    void aggregateCatalogIncludesAllRoutes() {
        assertEquals(48, ProtocolRoutes.ROUTES_BY_MESSAGE.size(), "expected 48 total routes");
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("chat.message", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("maps.list.request", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("discord.link.status-changed", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("moderation.ban.created", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("sentinel.subnet-rules.invalidated", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("sentinel.subnet-sweep.command", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("rating.season.ended", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("security.permissions.changed", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("security.staff.sync.request", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("security.staff.reset-password.request", 1)));
        assertNotNull(ProtocolRoutes.ROUTES_BY_MESSAGE.get(new ProtocolRoutes.MessageKey("discord.account.operation.request", 1)));
    }

    @Test
    void messageLookupWorksForEachFamily() {
        assertEquals("chat.message", ProtocolRoutes.routeFor("chat.message", 1).messageType());
        assertEquals("maps.list.request", ProtocolRoutes.routeFor("maps.list.request", 1).messageType());
        assertEquals("discord.link.status-changed", ProtocolRoutes.routeFor("discord.link.status-changed", 1).messageType());
        assertEquals("moderation.ban.created", ProtocolRoutes.routeFor("moderation.ban.created", 1).messageType());
        assertEquals("sentinel.subnet-rules.invalidated", ProtocolRoutes.routeFor("sentinel.subnet-rules.invalidated", 1).messageType());
        assertEquals("sentinel.subnet-sweep.command", ProtocolRoutes.routeFor("sentinel.subnet-sweep.command", 1).messageType());
        assertEquals("rating.season.ended", ProtocolRoutes.routeFor("rating.season.ended", 1).messageType());
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
                "2026-01-01T00:00:00Z",
                null
        );
        var moderationRoute = ProtocolRoutes.routeFor(moderationPayload);
        assertNotNull(moderationRoute);
        assertEquals("moderation.ban.created", moderationRoute.messageType());

        var sentinelPayload = new SentinelSubnetRulesInvalidatedV1("add:1.2.3.0/24", "alpha", "2026-01-01T00:00:00Z");
        var sentinelRoute = ProtocolRoutes.routeFor(sentinelPayload);
        assertNotNull(sentinelRoute);
        assertEquals("sentinel.subnet-rules.invalidated", sentinelRoute.messageType());
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

    @Test
    void casesAndInspectRoutesMatchSpecStreams() {
        var report = ProtocolRoutes.CASES_REPORT_SUBMITTED_V1;
        assertEquals(CasesReportSubmittedV1.class, report.payloadType());
        assertEquals("event", report.kind());
        assertEquals("xcore:evt:cases:report", report.stream());
        assertEquals("broadcast", report.targetScope());
        assertEquals(604800000, report.ttlMs());
        assertTrue(report.replayable());
        assertTrue(report.idempotentConsumerRecommended());
        assertEquals("cases", report.owner());
        assertEquals(report.stream(), CasesRoutes.CASES_REPORT_SUBMITTED_V1.stream());

        var notice = new CasesNoticeCommandV1("mini-pvp", "42:uuid:report_received", "uuid", null);
        var noticeRoute = ProtocolRoutes.routeFor(notice);
        assertEquals("xcore:cmd:cases-notice:mini-pvp", noticeRoute.stream().replace("{server}", notice.server()));
        assertEquals("payload.server", noticeRoute.bindings().get("server"));
        assertEquals("command", noticeRoute.kind());
        assertEquals(120000, noticeRoute.ttlMs());
        assertFalse(noticeRoute.replayable());

        var evidence = ProtocolRoutes.CASES_EVIDENCE_REQUEST_V1;
        assertEquals("xcore:rpc:req:mini-pvp", evidence.stream().replace("{server}", "mini-pvp"));
        assertEquals("payload.server", evidence.bindings().get("server"));
        assertEquals(15000, evidence.ttlMs());
        assertEquals(CasesEvidenceResponseV1.class, evidence.response().payloadType());
        assertEquals("xcore:rpc:resp:discord", evidence.response().stream().replace("{requester}", "discord"));
        assertEquals("rpc.requester", evidence.response().bindings().get("requester"));

        var inspect = ProtocolRoutes.MAPS_INSPECT_REQUEST_V1;
        assertEquals("xcore:rpc:req:mini-pvp", inspect.stream().replace("{server}", "mini-pvp"));
        assertEquals(60000, inspect.ttlMs());
        assertEquals(MapsInspectRequestV1.class, inspect.payloadType());
        assertEquals(MapsInspectResponseV1.class, inspect.response().payloadType());
        assertEquals("xcore:rpc:resp:discord", inspect.response().stream().replace("{requester}", "discord"));
        assertEquals(inspect.stream(), MapsRoutes.MAPS_INSPECT_REQUEST_V1.stream());
    }

    @Test
    void ratingRoutesCarryEventsAndRpcPairs() {
        var started = new RatingSeasonStartedV1(
                new SeasonRefV1("mini-pvp", 4, "Season 4", "2026-10-01T00:00:00Z", "2027-01-01T00:00:00Z"),
                3,
                "alpha",
                "2026-10-01T00:00:01Z"
        );
        var startedRoute = ProtocolRoutes.routeFor(started);
        assertNotNull(startedRoute);
        assertEquals("event", startedRoute.kind());
        assertEquals("xcore:evt:rating:season-started", startedRoute.stream());
        assertTrue(startedRoute.replayable());

        var reschedule = ProtocolRoutes.RATING_SEASON_RESCHEDULE_REQUEST_V1;
        assertEquals("rpc-request", reschedule.kind());
        assertEquals("xcore:rpc:req:{server}", reschedule.stream());
        assertEquals("payload.server", reschedule.bindings().get("server"));
        assertEquals(RatingSeasonRescheduleResponseV1.class, reschedule.response().payloadType());

        var merge = ProtocolRoutes.RATING_ACCOUNTS_MERGE_REQUEST_V1;
        assertEquals(RatingAccountsMergeResponseV1.class, merge.response().payloadType());

        var prizes = ProtocolRoutes.RATING_SEASON_PRIZES_SET_REQUEST_V1;
        assertEquals("rpc-request", prizes.kind());
        assertEquals("payload.server", prizes.bindings().get("server"));
        assertEquals(RatingSeasonPrizesSetResponseV1.class, prizes.response().payloadType());

        var grant = ProtocolRoutes.RATING_PRIZE_GRANT_UPDATE_REQUEST_V1;
        assertEquals(RatingPrizeGrantUpdateResponseV1.class, grant.response().payloadType());
    }
}
