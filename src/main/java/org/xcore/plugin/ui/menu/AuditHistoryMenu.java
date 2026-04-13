package org.xcore.plugin.ui.menu;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.Slice;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class AuditHistoryMenu extends Menu {

    private final AuditService auditService;

    @Inject
    public AuditHistoryMenu(Config config,
                            GlobalConfig globalConfig,
                            SessionService sessionService,
                            AuditService auditService) {
        super(config, globalConfig, sessionService);
        this.auditService = auditService;
    }

    public void history(String viewerUuid, PlayerData targetData) {
        history(viewerUuid, targetData, AuditViewMode.TARGET, null, true);
    }

    public void actions(String viewerUuid, PlayerData targetData) {
        history(viewerUuid, targetData, AuditViewMode.ACTOR, null, true);
    }

    private void history(String viewerUuid, PlayerData targetData, AuditViewMode mode, AuditCursor cursor, boolean resetState) {
        Session session = sessionService.get(viewerUuid);
        if (session == null || session.data == null || targetData == null) {
            return;
        }
        session.clear();

        AuditHistoryState state = session.getDraft(AuditHistoryState.class);
        if (state == null) {
            session.locale().send("error-internal", args());
            return;
        }

        if (resetState || !targetData.uuid.equals(state.targetUuid) || state.mode != mode) {
            state.targetUuid = targetData.uuid;
            state.mode = mode;
            state.backStack.clear();
            state.currentCursor = null;
            state.nextCursor = null;
        }

        var slice = switch (mode) {
            case TARGET -> auditService.findSummaryByTargetUuid(targetData.uuid, cursor, globalConfig.eventsPerPage);
            case ACTOR -> findSummaryByActor(AuditActorType.PLAYER_ADMIN, actorLookupIds(targetData), cursor, globalConfig.eventsPerPage);
        };
        state.currentCursor = cursor;
        state.nextCursor = slice.nextCursor();

        String content = buildSummaryContent(session, targetData, mode, slice.items().size(), cursor, slice.hasNext());

        var builder = session.builder()
                .title(mode == AuditViewMode.TARGET ? "audit-menu-history-title" : "audit-menu-actions-title")
                .rawContent(content)
                .start()
                .ifAddLocal(!state.backStack.isEmpty(), "previous", () -> {
                    AuditCursor previous = state.backStack.pollLast();
                    history(viewerUuid, targetData, mode, previous, false);
                })
                .ifAddLocal(slice.hasNext() && state.nextCursor != null, "next", () -> {
                    state.backStack.addLast(state.currentCursor);
                    history(viewerUuid, targetData, mode, state.nextCursor, false);
                })
                .end();

        builder.addForEach(slice.items(), (menu, item) -> menu.addRow(formatSummaryRow(session.locale(), item, mode), () -> {
            session.pushHistory(() -> history(viewerUuid, targetData, mode, state.currentCursor, false));
            details(viewerUuid, targetData, item.auditId());
        }));

        builder.addNavigationRow().show();
    }

    public void details(String viewerUuid, PlayerData targetData, String auditId) {
        Session session = sessionService.get(viewerUuid);
        if (session == null || session.data == null || targetData == null || auditId == null || auditId.isBlank()) {
            return;
        }
        session.clear();

        AuditRecord record = auditService.findByAuditId(auditId).orElse(null);
        if (record == null) {
            session.locale().send("error-processing-request", args());
            Runnable previous = session.popHistory();
            if (previous != null) {
                previous.run();
            }
            return;
        }

        session.builder()
                .title("audit-menu-details-title")
                .rawContent(formatDetailsContent(session, targetData, record))
                .addNavigationRow()
                .show();
    }

    static String formatSummaryRow(Localization local, AuditRecordSummary item) {
        return formatSummaryRow(local, item, AuditViewMode.TARGET);
    }

    static String formatSummaryRow(Localization local, AuditRecordSummary item, AuditViewMode mode) {
        String actor = item.actorName() == null || item.actorName().isBlank()
                ? local.t("audit-menu-unknown-actor")
                : item.actorName();
        String target = item.targetName() == null || item.targetName().isBlank()
                ? local.t("audit-menu-unknown-target")
                : item.targetName();
        String reason = item.reason() == null || item.reason().isBlank()
                ? local.t("audit-menu-reason-unspecified")
                : item.reason();
        return local.t(mode == AuditViewMode.TARGET ? "audit-menu-summary-row" : "audit-menu-action-summary-row", args(
                "action", actionLabel(local, item.action().name()),
                "actor", actor,
                "target", target,
                "reason", summarizeReason(reason)
        ));
    }

    static String summarizeReason(String reason) {
        if (reason == null) {
            return "Not Specified";
        }
        return reason.length() <= 48 ? reason : reason.substring(0, 45) + "...";
    }

    private String buildSummaryContent(Session session,
                                       PlayerData targetData,
                                       AuditViewMode mode,
                                       int itemCount,
                                       AuditCursor cursor,
                                       boolean hasNext) {
        Localization local = session.locale();
        String pageHint = cursor == null
                ? local.t("audit-menu-history-page-first")
                : local.t("audit-menu-history-page-older");
        String nextHint = hasNext
                ? local.t("audit-menu-history-more")
                : local.t("audit-menu-history-end");
        String emptyState = itemCount == 0
                ? local.t(mode == AuditViewMode.TARGET ? "audit-menu-history-empty" : "audit-menu-actions-empty")
                : local.t(mode == AuditViewMode.TARGET ? "audit-menu-history-hint" : "audit-menu-actions-hint");

        return local.t(mode == AuditViewMode.TARGET ? "audit-menu-history-content" : "audit-menu-actions-content", args(
                "player", targetData.nickname,
                "pid", targetData.pid,
                "entriesShown", itemCount,
                "pageState", pageHint,
                "nextState", nextHint,
                "hint", emptyState
        ));
    }

    private String formatDetailsContent(Session session, PlayerData targetData, AuditRecord record) {
        Localization local = session.locale();
        String actor = record.actor == null || record.actor.nameSnapshot == null || record.actor.nameSnapshot.isBlank()
                ? local.t("audit-menu-unknown-actor")
                : record.actor.nameSnapshot;
        String reason = record.reason == null || record.reason.isBlank() ? local.t("audit-menu-reason-unspecified") : record.reason;
        String occurredAt = record.occurredAt == null ? session.locale().t("never") : formatInstant(record.occurredAt, session);
        String expiresAt = record.details == null || record.details.expiresAt == null
                ? session.locale().t("never")
                : formatInstant(record.details.expiresAt, session);
        String duration = record.details == null || record.details.durationMs == null
                ? local.t("audit-menu-duration-permanent")
                : formatDuration(record.details.durationMs, local);

        return local.t("audit-menu-details-content", args(
                "player", targetData.nickname,
                "pid", targetData.pid,
                "action", actionLabel(local, record.action.name()),
                "actor", actor,
                "reason", reason,
                "occurredAt", occurredAt,
                "duration", duration,
                "expiresAt", expiresAt,
                "auditId", record.auditId
        ));
    }

    private String formatInstant(Instant value, Session session) {
        return formatTime(value.toEpochMilli(), session);
    }

    private String formatDuration(Long durationMs, Localization local) {
        long totalMinutes = Math.max(1L, durationMs / 60000L);
        if (totalMinutes > Integer.MAX_VALUE) {
            totalMinutes = Integer.MAX_VALUE;
        }
        return formatPlayTime((int) totalMinutes, local);
    }

    private static String actionLabel(Localization local, String action) {
        return local.t("audit-menu-action-" + action.toLowerCase());
    }

    public static final class AuditHistoryState {
        public String targetUuid;
        public AuditViewMode mode = AuditViewMode.TARGET;
        public Deque<AuditCursor> backStack = new ArrayDeque<>();
        public AuditCursor currentCursor;
        public AuditCursor nextCursor;
    }

    enum AuditViewMode {
        TARGET,
        ACTOR
    }

    private Slice<AuditRecordSummary> findSummaryByActor(AuditActorType actorType,
                                                         List<String> actorIds,
                                                         AuditCursor cursor,
                                                         int limit) {
        if (auditService instanceof org.xcore.plugin.service.moderation.DefaultAuditService defaultAuditService) {
            return defaultAuditService.findSummaryByActor(actorType, actorIds, cursor, limit);
        }
        if (actorIds == null || actorIds.isEmpty()) {
            return new Slice<>(List.of(), false, null);
        }
        for (String actorId : actorIds) {
            if (actorId != null && !actorId.isBlank()) {
                return auditService.findSummaryByActor(actorType, actorId, cursor, limit);
            }
        }
        return new Slice<>(List.of(), false, null);
    }

    private static List<String> actorLookupIds(PlayerData targetData) {
        String discordId = targetData.discordId;
        String nickname = targetData.nickname;
        if (discordId != null && !discordId.isBlank()) {
            return List.of(discordId, nickname);
        }
        return List.of(nickname);
    }
}
