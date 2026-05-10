package org.xcore.plugin.ui.menu;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

final class AuditHistoryFlows {

    static final String ROUTE_HISTORY = "audit.history";
    static final String ROUTE_DETAILS = "audit.details";

    private static final String ACTION_PREVIOUS = "previous";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_DETAILS_PREFIX = "details:";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";

    private AuditHistoryFlows() {
    }

    static final class HistoryFlow implements RoutedMenuFlow<AuditHistoryMenu.AuditHistoryState> {
        private final AuditHistoryMenu menu;

        HistoryFlow(AuditHistoryMenu menu) {
            this.menu = menu;
        }

        @Override
        public String routeId() {
            return ROUTE_HISTORY;
        }

        @Override
        public AuditHistoryMenu.AuditHistoryState createState(org.xcore.plugin.session.Session session,
                                                              MenuRoute route,
                                                              AuditHistoryMenu.AuditHistoryState currentState) {
            if (AuditHistoryMenu.shouldReuseHistoryState(session, route, currentState)) {
                return currentState;
            }
            AuditHistoryMenu.AuditHistoryState state = new AuditHistoryMenu.AuditHistoryState();
            state.targetUuid = route.param("targetUuid");
            state.targetNickname = route.param("targetNickname");
            state.targetPid = route.intParam("targetPid", 0);
            state.targetDiscordId = route.param("targetDiscordId");
            state.mode = AuditHistoryMenu.parseMode(route.param("mode"));
            return state;
        }

        @Override
        public Class<AuditHistoryMenu.AuditHistoryState> stateType() {
            return AuditHistoryMenu.AuditHistoryState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<AuditHistoryMenu.AuditHistoryState> context) {
            var session = context.session();
            var state = context.state();
            Localization local = context.locale();

            if (state.targetUuid == null) {
                return MenuScreen.normal(
                        local.t(state.mode == AuditHistoryMenu.AuditViewMode.TARGET ? "audit-menu-history-title" : "audit-menu-actions-title"),
                        local.t("error-internal"),
                        List.of(List.of(MenuButton.of(local.t("close"), ACTION_CLOSE)))
                );
            }

            var slice = switch (state.mode) {
                case TARGET -> menu.auditService.findSummaryByTargetUuid(state.targetUuid, state.currentCursor, menu.globalConfig.eventsPerPage);
                case ACTOR -> menu.findSummaryByActor(
                        AuditActorType.PLAYER_ADMIN,
                        menu.actorLookupIds(state.targetDiscordId, state.targetNickname),
                        state.currentCursor,
                        menu.globalConfig.eventsPerPage
                );
            };
            state.nextCursor = slice.nextCursor();

            String content = menu.buildSummaryContent(session, state.targetNickname, state.targetPid, state.mode, slice.items().size(), state.currentCursor, slice.hasNext());

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
                        AuditHistoryMenu.formatSummaryRow(local, item, state.mode),
                        ACTION_DETAILS_PREFIX + item.auditId()
                )));
            }

            List<MenuButton> navRow = new ArrayList<>();
            if (session.canGoBack(true)) {
                navRow.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navRow.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navRow);

            return MenuScreen.normal(
                    local.t(state.mode == AuditHistoryMenu.AuditViewMode.TARGET ? "audit-menu-history-title" : "audit-menu-actions-title"),
                    content,
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<AuditHistoryMenu.AuditHistoryState> context, String actionId) {
            var state = context.state();
            var session = context.session();

            switch (actionId) {
                case ACTION_PREVIOUS -> {
                    AuditCursor previous = state.backStack.pollLast();
                    state.currentCursor = AuditHistoryMenu.restoreCursor(previous);
                    context.render();
                }
                case ACTION_NEXT -> {
                    state.backStack.addLast(state.currentCursor == null ? AuditHistoryMenu.FIRST_PAGE_MARKER : state.currentCursor);
                    state.currentCursor = state.nextCursor;
                    context.render();
                }
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                    if (actionId.startsWith(ACTION_DETAILS_PREFIX)) {
                        String auditId = actionId.substring(ACTION_DETAILS_PREFIX.length());
                        AuditRecord record = menu.auditService.findByAuditId(auditId).orElse(null);
                        if (record == null) {
                            session.locale().send("error-processing-request", args());
                            context.render();
                        } else {
                            context.openRoute(MenuRoute.of(ROUTE_DETAILS).withParam("auditId", auditId));
                        }
                    }
                }
            }
        }
    }

    static final class DetailsFlow implements RoutedMenuFlow<AuditHistoryMenu.AuditHistoryState> {
        private final AuditHistoryMenu menu;

        DetailsFlow(AuditHistoryMenu menu) {
            this.menu = menu;
        }

        @Override
        public String routeId() {
            return ROUTE_DETAILS;
        }

        @Override
        public AuditHistoryMenu.AuditHistoryState createState(org.xcore.plugin.session.Session session,
                                                              MenuRoute route,
                                                              AuditHistoryMenu.AuditHistoryState currentState) {
            return currentState != null ? currentState : new AuditHistoryMenu.AuditHistoryState();
        }

        @Override
        public Class<AuditHistoryMenu.AuditHistoryState> stateType() {
            return AuditHistoryMenu.AuditHistoryState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<AuditHistoryMenu.AuditHistoryState> context) {
            var session = context.session();
            var state = context.state();
            Localization local = context.locale();
            String auditId = context.route().param("auditId");

            if (auditId == null || auditId.isBlank()) {
                session.locale().send("error-processing-request", args());
                return errorScreen(local);
            }

            AuditRecord record = menu.auditService.findByAuditId(auditId).orElse(null);
            if (record == null) {
                session.locale().send("error-processing-request", args());
                return errorScreen(local);
            }

            String content = menu.formatDetailsContent(session, state.targetNickname, state.targetPid, record);

            List<List<MenuButton>> rows = new ArrayList<>();
            List<MenuButton> navRow = new ArrayList<>();
            if (session.canGoBack(true)) {
                navRow.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navRow.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navRow);

            return MenuScreen.followUp(
                    local.t("audit-menu-details-title"),
                    content,
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<AuditHistoryMenu.AuditHistoryState> context, String actionId) {
            switch (actionId) {
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
            }
        }

        private MenuScreen errorScreen(Localization local) {
            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(MenuButton.of(local.t("back"), ACTION_BACK)));
            return MenuScreen.followUp(
                    local.t("audit-menu-details-title"),
                    local.t("error-processing-request"),
                    rows
            );
        }
    }
}
