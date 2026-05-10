package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.Slice;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class AuditHistoryMenu extends Menu {

    static final AuditCursor FIRST_PAGE_MARKER = new AuditCursor(Long.MIN_VALUE, "__first_page__");

    final AuditService auditService;
    private final MenuService menuService;

    @Inject
    public AuditHistoryMenu(Config config,
                            GlobalConfig globalConfig,
                            SessionService sessionService,
                            AuditService auditService,
                            MenuService menuService) {
        super(config, globalConfig, sessionService);
        this.auditService = auditService;
        this.menuService = menuService;
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new AuditHistoryFlows.HistoryFlow(this));
        menuService.registerRoute(new AuditHistoryFlows.DetailsFlow(this));
    }

    public void history(String viewerUuid, PlayerData targetData) {
        openHistory(viewerUuid, targetData, AuditViewMode.TARGET);
    }

    public void actions(String viewerUuid, PlayerData targetData) {
        openHistory(viewerUuid, targetData, AuditViewMode.ACTOR);
    }

    private void openHistory(String viewerUuid, PlayerData targetData, AuditViewMode mode) {
        Session session = sessionService.get(viewerUuid);
        if (session == null || session.data == null || targetData == null) {
            return;
        }
        MenuRoute route = MenuRoute.of(AuditHistoryFlows.ROUTE_HISTORY)
                .withParam("targetUuid", targetData.uuid)
                .withParam("targetNickname", targetData.nickname == null ? "" : targetData.nickname)
                .withParam("targetPid", String.valueOf(targetData.pid))
                .withParam("mode", mode.name());
        if (targetData.discordId != null && !targetData.discordId.isBlank()) {
            route = route.withParam("targetDiscordId", targetData.discordId);
        }

        session.menuService.openRoute(session, route);
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
            menuService.goBack(session);
            return;
        }

        AuditHistoryState state = session.getDraft(AuditHistoryState.class);
        if (state == null) {
            session.locale().send("error-internal", args());
            return;
        }

        state.targetUuid = targetData.uuid;
        state.targetNickname = targetData.nickname;
        state.targetPid = targetData.pid;
        state.targetDiscordId = targetData.discordId;
        if (state.mode == null) {
            state.mode = AuditViewMode.TARGET;
        }

        session.menuService.renderRoute(session, MenuRoute.of(AuditHistoryFlows.ROUTE_DETAILS).withParam("auditId", auditId));
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

    String buildSummaryContent(Session session,
                               String targetNickname,
                               int targetPid,
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
                "player", targetNickname,
                "pid", targetPid,
                "entriesShown", itemCount,
                "pageState", pageHint,
                "nextState", nextHint,
                "hint", emptyState
        ));
    }

    String formatDetailsContent(Session session, String targetNickname, int targetPid, AuditRecord record) {
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
                "player", targetNickname,
                "pid", targetPid,
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
        public String targetNickname;
        public int targetPid;
        public String targetDiscordId;
        public AuditViewMode mode = AuditViewMode.TARGET;
        public Deque<AuditCursor> backStack = new ArrayDeque<>();
        public AuditCursor currentCursor;
        public AuditCursor nextCursor;
    }

    enum AuditViewMode {
        TARGET,
        ACTOR
    }

    static boolean shouldReuseHistoryState(Session session, MenuRoute route, AuditHistoryState currentState) {
        if (currentState == null) {
            return false;
        }
        var activeScreen = session.activeScreen();
        if (activeScreen == null || !activeScreen.hasRoute() || !AuditHistoryFlows.ROUTE_DETAILS.equals(activeScreen.route().id())) {
            return false;
        }
        return route.param("targetUuid").equals(currentState.targetUuid)
                && parseMode(route.param("mode")) == currentState.mode;
    }

    static AuditViewMode parseMode(String modeParam) {
        if (modeParam == null) {
            return AuditViewMode.TARGET;
        }
        try {
            return AuditViewMode.valueOf(modeParam);
        } catch (IllegalArgumentException ignored) {
            return AuditViewMode.TARGET;
        }
    }

    static AuditCursor restoreCursor(AuditCursor cursor) {
        return FIRST_PAGE_MARKER.equals(cursor) ? null : cursor;
    }

    Slice<AuditRecordSummary> findSummaryByActor(AuditActorType actorType,
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

    List<String> actorLookupIds(String discordId, String nickname) {
        if (discordId != null && !discordId.isBlank()) {
            return List.of(discordId, nickname);
        }
        return List.of(nickname);
    }
}
