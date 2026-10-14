package org.xcore.plugin.service.network;

import org.xcore.plugin.cases.CaseEvidence;
import org.xcore.plugin.cases.CaseText;
import org.xcore.plugin.cases.ReportDraft;
import org.xcore.plugin.cases.ReportTarget;
import org.xcore.plugin.integration.blockhistory.BlockHistorySummary;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.protocol.generated.messages.cases.CasesMessages.CasesEvidenceResponseV1;
import org.xcore.protocol.generated.messages.cases.CasesMessages.CasesReportSubmittedV1;
import org.xcore.protocol.generated.shared.CaseBlockAreaV1;
import org.xcore.protocol.generated.shared.CaseBlockEntryV1;
import org.xcore.protocol.generated.shared.CaseBlockSummaryV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

public final class CasesProtocolMapper {
    private CasesProtocolMapper() {}

    public static CasesReportSubmittedV1 toReport(String submissionId, String server, ReportTarget reporter,
                                                 ReportDraft draft, String locale, boolean sameNetwork,
                                                 CaseEvidence evidence) {
        return new CasesReportSubmittedV1(submissionId, server, player(reporter), player(draft.target()),
                evidence.targetOnline(), draft.reason().code(),
                draft.comment() == null ? null : CaseText.plain(draft.comment(), 300),
                locale == null ? null : CaseText.plain(locale, 16), evidence.mapName(), evidence.mapFileName(),
                evidence.gameMode(), sameNetwork, evidence.chat(), toBlockSummary(evidence.blockSummary()),
                evidence.capturedAt().toString());
    }

    public static CasesEvidenceResponseV1 toEvidence(String server, CaseEvidence evidence) {
        return new CasesEvidenceResponseV1(server, evidence.targetOnline(), evidence.mapName(), evidence.gameMode(),
                evidence.chat(), toBlockSummary(evidence.blockSummary()), evidence.capturedAt().toString());
    }

    private static PlayerRefV1 player(ReportTarget target) {
        return new PlayerRefV1(target.uuid(), PlayerPids.orNull(target.pid()),
                CaseText.name(target.name()), null);
    }

    public static CaseBlockSummaryV1 toBlockSummary(BlockHistorySummary summary) {
        if (summary == null) return null;
        var area = summary.area();
        return new CaseBlockSummaryV1(summary.windowSeconds(), summary.built(), summary.destroyed(),
                summary.configured(), summary.truncated(), summary.topBlocks().stream()
                        .map(entry -> new CaseBlockEntryV1(entry.block(), entry.built(), entry.destroyed())).toList(),
                area == null ? null : new CaseBlockAreaV1(area.x1(), area.y1(), area.x2(), area.y2()),
                summary.lastActionSecondsAgo());
    }

    public static BlockHistorySummary fromBlockSummary(CaseBlockSummaryV1 summary) {
        if (summary == null) return null;
        var area = summary.area();
        return new BlockHistorySummary(summary.windowSeconds(), summary.built(), summary.destroyed(),
                summary.configured(), summary.truncated(), summary.topBlocks().stream()
                        .map(entry -> new BlockHistorySummary.Entry(entry.block(), entry.built(), entry.destroyed())).toList(),
                area == null ? null : new BlockHistorySummary.Area(area.x1(), area.y1(), area.x2(), area.y2()),
                summary.lastActionSecondsAgo());
    }
}
