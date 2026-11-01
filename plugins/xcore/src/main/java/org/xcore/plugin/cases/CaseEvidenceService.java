package org.xcore.plugin.cases;

import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Groups;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.integration.blockhistory.BlockHistoryRegistry;

import java.time.Instant;
import java.util.List;

@Singleton
public class CaseEvidenceService {
    private final CasesFeature feature;
    private final RecentChatBuffer chat;
    private final RecentPlayers recent;
    private final BlockHistoryRegistry history;

    public CaseEvidenceService(CasesFeature feature, RecentChatBuffer chat, RecentPlayers recent,
                               BlockHistoryRegistry history) {
        this.feature = feature;
        this.chat = chat;
        this.recent = recent;
        this.history = history;
    }

    /** No storage calls. Unknown players receive context with empty evidence. */
    public CaseEvidence capture(String targetUuid, String reporterUuid, int chatLines, int windowSeconds,
                                int blockWindowSeconds) {
        GameThread.require("CaseEvidenceService.capture");
        boolean online = Groups.player != null && Groups.player.contains(player -> player.uuid().equals(targetUuid)
                && Async.isPlayerOnline(player));
        boolean known = online || recent.find(targetUuid) != null;
        var map = Vars.state == null ? null : Vars.state.map;
        var rules = Vars.state == null ? null : Vars.state.rules;
        String mode = rules == null ? null : rules.modeName != null && !rules.modeName.isBlank()
                ? rules.modeName : rules.mode().name();
        return new CaseEvidence(online,
                map == null ? null : CaseText.plain(map.name(), 256),
                map == null || map.file == null ? null : CaseText.plain(map.file.name(), 256),
                mode == null ? null : CaseText.plain(mode, 64),
                feature.enabled() && known ? chat.excerpt(targetUuid, reporterUuid, chatLines, windowSeconds) : List.of(),
                feature.enabled() && known ? history.summarize(targetUuid, Math.max(blockWindowSeconds, 1)).orElse(null) : null,
                Instant.now());
    }
}
