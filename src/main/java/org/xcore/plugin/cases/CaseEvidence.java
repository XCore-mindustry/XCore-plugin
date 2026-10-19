package org.xcore.plugin.cases;

import org.xcore.plugin.integration.blockhistory.BlockHistorySummary;
import org.xcore.protocol.generated.shared.CaseChatLineV1;

import java.time.Instant;
import java.util.List;

/** Immutable evidence captured on the game thread, safe to publish from a worker. */
public record CaseEvidence(boolean targetOnline, String mapName, String mapFileName, String gameMode,
                           List<CaseChatLineV1> chat, BlockHistorySummary blockSummary, Instant capturedAt) {
    public CaseEvidence {
        chat = List.copyOf(chat);
    }
}
