package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cases.*;
import org.xcore.plugin.integration.blockhistory.BlockHistorySummary;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.protocol.generated.messages.cases.CasesMessages.*;
import org.xcore.protocol.generated.shared.CaseChatLineV1;
import org.xcore.protocol.generated.shared.CaseChatLineV1Role;
import org.xcore.protocol.generated.routes.CasesRoutes;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CasesProtocolMapperTest {
    // Outbound roles use generated wire values; this plugin only consumes the request and wake-up.
    private final Gson gson = new GsonBuilder().registerTypeAdapter(CaseChatLineV1Role.class,
            (JsonDeserializer<CaseChatLineV1Role>) (json, type, context) -> CaseChatLineV1Role.fromValue(json.getAsString())).create();
    private final BlockHistorySummary blocks = new BlockHistorySummary(600, 3, 2, 1, true,
            List.of(new BlockHistorySummary.Entry("router", 3, 2)), new BlockHistorySummary.Area(1, 2, 3, 4), 5);
    private final CaseEvidence evidence = new CaseEvidence(true, "Map", "map.msav", "survival",
            List.of(new CaseChatLineV1(100, "Target", 2, CaseChatLineV1Role.TARGET, "chat", true)), blocks,
            Instant.parse("2026-10-13T12:00:00Z"));

    @Test
    void reportAndEvidenceRoundTripWirePayloadIncludingRolesAndBlockSummary() {
        var reporter = new ReportTarget("reporter", 1, "[red]Reporter", true, 0);
        var draft = new ReportDraft("reporter", new ReportTarget("target", 2, "Target", true, 0),
                ReportReason.GRIEFING, "[red]" + "x".repeat(350));
        var report = CasesProtocolMapper.toReport("submission", "survival", reporter, draft, "uk_UA", true, evidence);
        var decoded = gson.fromJson(gson.toJson(report.toPayload()), CasesReportSubmittedV1.class);
        assertThat(decoded).isEqualTo(report);
        assertThat(decoded.comment()).hasSize(300);
        assertThat(decoded.reporter().playerName()).isEqualTo("Reporter");
        assertThat(decoded.reporter().ip()).isNull();
        assertThat(decoded.target().ip()).isNull();
        assertThat(report.toPayload().get("reporter").toString()).doesNotContain("ip=");
        assertThat(report.toPayload().get("target").toString()).doesNotContain("ip=");
        var response = CasesProtocolMapper.toEvidence("survival", evidence);
        assertThat(gson.fromJson(gson.toJson(response.toPayload()), CasesEvidenceResponseV1.class)).isEqualTo(response);
        assertThat(response.toPayload().toString()).doesNotContain("playerUuid", "ip=");
        assertThat(CasesProtocolMapper.fromBlockSummary(CasesProtocolMapper.toBlockSummary(blocks))).isEqualTo(blocks);
    }

    @Test
    void requestAndNoticeRoundTripUsingActualRedisGson() {
        Gson redis = RedisNetworkBackend.createRedisGson();
        var request = new CasesEvidenceRequestV1("survival", "target", 30, 600);
        var notice = new CasesNoticeCommandV1("survival", "57:reporter:report_received", "reporter", "2026-10-13T12:00:00Z");
        assertThat(redis.fromJson(redis.toJson(request.toPayload()), CasesEvidenceRequestV1.class)).isEqualTo(request);
        assertThat(redis.fromJson(redis.toJson(notice.toPayload()), CasesNoticeCommandV1.class)).isEqualTo(notice);
    }

    @Test
    void nullOptionalEvidenceAndUnknownPidAreOmitted() {
        var reporter = new ReportTarget("reporter", PlayerPids.NONE, "", true, 0);
        var empty = new CaseEvidence(false, null, null, null, List.of(), null, evidence.capturedAt());
        var draft = new ReportDraft("reporter", reporter, ReportReason.OTHER, null);
        var report = CasesProtocolMapper.toReport("s", "server", reporter, draft, null, false, empty);
        assertThat(report.reporter().playerPid()).isNull();
        assertThat(report.reporter().playerName()).isEqualTo("Player");
        assertThat(report.toPayload()).doesNotContainKeys("comment", "reporterLocale", "mapName", "blockSummary");
        assertThat(CasesProtocolMapper.fromBlockSummary(null)).isNull();
    }

    @Test
    void routesResolveWithoutAnyPluginRouteChanges() {
        var adapter = new RedisProtocolRouteAdapter();
        assertThat(adapter.routeDescriptorFor(CasesReportSubmittedV1.class).streamPattern()).isEqualTo("xcore:evt:cases:report");
        assertThat(adapter.routeDescriptorFor(CasesNoticeCommandV1.class).streamPattern()).isEqualTo("xcore:cmd:cases-notice:{server}");
        assertThat(adapter.routeDescriptorFor(CasesEvidenceRequestV1.class).streamPattern()).isEqualTo("xcore:rpc:req:{server}");
        assertThat(CasesRoutes.CASES_REPORT_SUBMITTED_V1.ttlMs()).isEqualTo(604800000);
    }
}
