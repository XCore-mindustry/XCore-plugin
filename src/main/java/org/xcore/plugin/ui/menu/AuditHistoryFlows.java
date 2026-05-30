package org.xcore.plugin.ui.menu;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import static com.ospx.flubundle.Bundle.args;

final class AuditHistoryFlows {

    static final String ROUTE_HISTORY = "audit.history";
    static final String ROUTE_DETAILS = "audit.details";

    private AuditHistoryFlows() {
    }

    static final class HistoryFlow extends BaseMenuFlow<AuditHistoryMenu.AuditHistoryState> {
        private final AuditHistoryMenu menu;

        HistoryFlow(AuditHistoryMenu menu) {
            super(ROUTE_HISTORY, AuditHistoryMenu.AuditHistoryState.class);
            this.menu = menu;

            action("previous", ctx -> {
                var state = ctx.state();
                AuditCursor previous = state.backStack.pollLast();
                state.currentCursor = AuditHistoryMenu.restoreCursor(previous);
                ctx.render();
            });
            action("next", ctx -> {
                var state = ctx.state();
                state.backStack.addLast(state.currentCursor == null ? AuditHistoryMenu.FIRST_PAGE_MARKER : state.currentCursor);
                state.currentCursor = state.nextCursor;
                ctx.render();
            });
            actionPrefix("details:", (ctx, auditId) -> {
                var session = ctx.session();
                AuditRecord record = menu.auditService.findByAuditId(auditId).orElse(null);
                if (record == null) {
                    session.locale().send("error-processing-request", args());
                    ctx.render();
                } else {
                    ctx.openRoute(MenuRoute.of(ROUTE_DETAILS).withParam("auditId", auditId));
                }
            });
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
        public MenuScreen render(MenuRenderContext<AuditHistoryMenu.AuditHistoryState> context) {
            var session = context.session();
            var state = context.state();
            Localization local = context.locale();

            if (state.targetUuid == null) {
                return MenuScreen.normal(
                        local.t(state.mode == AuditHistoryMenu.AuditViewMode.TARGET ? "audit-menu-history-title" : "audit-menu-actions-title"),
                        local.t("error-internal"),
                        new MenuGrid().row(MenuButton.of(local.t("close"), "close")).build()
                );
            }

            var slice = switch (state.mode) {
                case TARGET -> menu.auditService.findSummaryByTargetUuid(state.targetUuid, state.currentCursor, menu.secretsConfig.pagination.eventsPerPage);
                case ACTOR -> menu.findSummaryByActor(
                        AuditActorType.PLAYER_ADMIN,
                        menu.actorLookupIds(state.targetDiscordId, state.targetNickname),
                        state.currentCursor,
                        menu.secretsConfig.pagination.eventsPerPage
                );
            };
            state.nextCursor = slice.nextCursor();

            String content = menu.buildSummaryContent(session, state.targetNickname, state.targetPid, state.mode, slice.items().size(), state.currentCursor, slice.hasNext());

            var grid = new MenuGrid();
            var paginationRow = new java.util.ArrayList<MenuButton>();
            if (!state.backStack.isEmpty()) {
                paginationRow.add(MenuButton.of(local.t("previous"), "previous"));
            }
            if (slice.hasNext() && state.nextCursor != null) {
                paginationRow.add(MenuButton.of(local.t("next"), "next"));
            }
            if (!paginationRow.isEmpty()) {
                grid.row(paginationRow.toArray(new MenuButton[0]));
            }

            for (var item : slice.items()) {
                grid.row(MenuButton.of(
                        AuditHistoryMenu.formatSummaryRow(local, item, state.mode),
                        "details:" + item.auditId()
                ));
            }

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t(state.mode == AuditHistoryMenu.AuditViewMode.TARGET ? "audit-menu-history-title" : "audit-menu-actions-title"),
                    content,
                    grid.build()
            );
        }
    }

    static final class DetailsFlow extends BaseMenuFlow<AuditHistoryMenu.AuditHistoryState> {
        private final AuditHistoryMenu menu;

        DetailsFlow(AuditHistoryMenu menu) {
            super(ROUTE_DETAILS, AuditHistoryMenu.AuditHistoryState.class);
            this.menu = menu;
        }

        @Override
        public AuditHistoryMenu.AuditHistoryState createState(org.xcore.plugin.session.Session session,
                                                               MenuRoute route,
                                                               AuditHistoryMenu.AuditHistoryState currentState) {
            return currentState != null ? currentState : new AuditHistoryMenu.AuditHistoryState();
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

            var grid = new MenuGrid().defaultNavigation(session, local);

            return MenuScreen.followUp(
                    local.t("audit-menu-details-title"),
                    content,
                    grid.build()
            );
        }

        private MenuScreen errorScreen(Localization local) {
            return MenuScreen.followUp(
                    local.t("audit-menu-details-title"),
                    local.t("error-processing-request"),
                    new MenuGrid().row(MenuButton.of(local.t("back"), "back")).build()
            );
        }
    }
}
