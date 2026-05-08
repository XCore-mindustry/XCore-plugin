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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuFlow;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class AuditHistoryMenu extends Menu {

    private static final String ACTION_PREVIOUS = "previous";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_DETAILS_PREFIX = "details:";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final AuditCursor FIRST_PAGE_MARKER = new AuditCursor(Long.MIN_VALUE, "__first_page__");

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
        state.currentCursor = cursor;

        session.menuService.renderFlow(session, new HistoryFlow(viewerUuid, targetData));
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

        var builder = session.builder()
                .title("audit-menu-details-title")
                .rawContent(formatDetailsContent(session, targetData, record));

        if (session.hasHistory()) {
            builder.addLocal("back", () -> {
                session.menuService.hideFollowUp(session);
                Runnable previousMenu = session.popHistory();
                if (previousMenu != null) previousMenu.run();
            });
        }

        builder.addLocal("close", () -> session.menuService.close(session));
        builder.showFollowUp();
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

    private final class HistoryFlow implements MenuFlow<AuditHistoryState> {
        private final String viewerUuid;
        private final PlayerData targetData;

        HistoryFlow(String viewerUuid, PlayerData targetData) {
            this.viewerUuid = viewerUuid;
            this.targetData = targetData;
        }

        @Override
        public Class<AuditHistoryState> stateType() {
            return AuditHistoryState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<AuditHistoryState> context) {
            Session session = context.session();
            AuditHistoryState state = context.state();
            Localization local = context.locale();

            var slice = switch (state.mode) {
                case TARGET -> auditService.findSummaryByTargetUuid(targetData.uuid, state.currentCursor, globalConfig.eventsPerPage);
                case ACTOR -> findSummaryByActor(AuditActorType.PLAYER_ADMIN, actorLookupIds(targetData), state.currentCursor, globalConfig.eventsPerPage);
            };
            state.nextCursor = slice.nextCursor();

            String content = buildSummaryContent(session, targetData, state.mode, slice.items().size(), state.currentCursor, slice.hasNext());

            List<List<MenuButton>> rows = new ArrayList<>();

            List<MenuButton> paginationRow = new ArrayList<>();
            if (!state.backStack.isEmpty()) {
                paginationRow.add(MenuButton.of(local.t("previous"), ACTION_PREVIOUS));
            }
            if (slice.hasNext() && state.nextCursor != null) {
                paginationRow.add(MenuButton.of(local.t("next"), ACTION_NEXT));
            }
            if (!paginationRow.isEmpty()) {
                rows.add(paginationRow);
            }

            for (var item : slice.items()) {
                rows.add(List.of(MenuButton.of(
                        formatSummaryRow(local, item, state.mode),
                        ACTION_DETAILS_PREFIX + item.auditId()
                )));
            }

            List<MenuButton> navRow = new ArrayList<>();
            if (session.hasHistory()) {
                navRow.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navRow.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navRow);

            return MenuScreen.normal(
                    local.t(state.mode == AuditViewMode.TARGET ? "audit-menu-history-title" : "audit-menu-actions-title"),
                    content,
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<AuditHistoryState> context, String actionId) {
            AuditHistoryState state = context.state();
            Session session = context.session();

            switch (actionId) {
                case ACTION_PREVIOUS -> {
                    AuditCursor previous = state.backStack.pollLast();
                    state.currentCursor = restoreCursor(previous);
                    context.render();
                }
                case ACTION_NEXT -> {
                    state.backStack.addLast(state.currentCursor == null ? FIRST_PAGE_MARKER : state.currentCursor);
                    state.currentCursor = state.nextCursor;
                    context.render();
                }
                case ACTION_BACK -> {
                    Runnable previousMenu = session.popHistory();
                    if (previousMenu != null) previousMenu.run();
                }
                case ACTION_CLOSE -> context.close();
                default -> {
                    if (actionId.startsWith(ACTION_DETAILS_PREFIX)) {
                        String auditId = actionId.substring(ACTION_DETAILS_PREFIX.length());
                        session.pushHistory(() -> {
                            session.clear();
                            AuditHistoryState histState = session.getDraft(AuditHistoryState.class);
                            histState.targetUuid = state.targetUuid;
                            histState.mode = state.mode;
                            histState.backStack = new ArrayDeque<>(state.backStack);
                            histState.currentCursor = state.currentCursor;
                            histState.nextCursor = state.nextCursor;
                            session.menuService.renderFlow(session, new HistoryFlow(viewerUuid, targetData));
                        });
                        details(viewerUuid, targetData, auditId);
                    }
                }
            }
        }
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

    private static AuditCursor restoreCursor(AuditCursor cursor) {
        return FIRST_PAGE_MARKER.equals(cursor) ? null : cursor;
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
