package org.xcore.plugin.ui.menu;

import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

final class HelpFlows {

    static final String ROUTE_LIST = "help.list";
    static final String ROUTE_DETAILS = "help.details";

    private static final String ACTION_PREVIOUS = "previous";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final String CMD_PREFIX = "cmd:";

    private static final int MAX_DESC_LEN = 40;

    private HelpFlows() {
    }

    static final class HelpListFlow implements RoutedMenuFlow<HelpListState> {
        private final HelpMenu menu;

        HelpListFlow(HelpMenu menu) {
            this.menu = menu;
        }

        @Override
        public String routeId() {
            return ROUTE_LIST;
        }

        @Override
        public HelpListState createState(Session session, MenuRoute route, HelpListState currentState) {
            return currentState == null ? new HelpListState() : currentState;
        }

        @Override
        public Class<HelpListState> stateType() {
            return HelpListState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<HelpListState> context) {
            Session session = context.session();
            XCoreSender sender = session.sender;
            if (sender == null) {
                return errorScreen(session);
            }

            int page = context.route().intParam("page", 1);
            List<HelpMenu.UnifiedCommand> allCommands = menu.collectAllCommands(sender);
            allCommands.sort(Comparator.comparing(HelpMenu.UnifiedCommand::name));

            if (allCommands.isEmpty()) {
                session.locale().send("empty");
                return errorScreen(session);
            }

            var pagination = CustomGatherers.calculatePagination(allCommands.size(), menu.globalConfig.commandsPerPage);
            int currentPage = pagination.clampPage(page);
            int skip = (currentPage - 1) * menu.globalConfig.commandsPerPage;
            List<HelpMenu.UnifiedCommand> pageSlice = allCommands.subList(skip, Math.min(skip + menu.globalConfig.commandsPerPage, allCommands.size()));

            var local = context.locale();
            List<List<MenuButton>> rows = new ArrayList<>();

            List<MenuButton> paginationRow = new ArrayList<>();
            if (currentPage > 1) {
                paginationRow.add(MenuButton.of(local.t("previous"), ACTION_PREVIOUS));
            }
            if (currentPage < pagination.totalPages()) {
                paginationRow.add(MenuButton.of(local.t("next"), ACTION_NEXT));
            }
            if (!paginationRow.isEmpty()) {
                rows.add(paginationRow);
            }

            for (HelpMenu.UnifiedCommand cmd : pageSlice) {
                String desc = menu.resolveDescription(session, cmd);
                String btnText = local.t("help-menu-button", args(
                        "command", menu.formatCommandLabel(session, cmd),
                        "description", menu.truncate(desc, MAX_DESC_LEN)
                ));
                rows.add(List.of(MenuButton.of(btnText, CMD_PREFIX + cmd.name())));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("help-menu-title"),
                    local.t("help-menu-content", args("page", currentPage, "total", pagination.totalPages())),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<HelpListState> context, String actionId) {
            Session session = context.session();
            int currentPage = context.route().intParam("page", 1);

            switch (actionId) {
                case ACTION_PREVIOUS -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_LIST).withParam("page", String.valueOf(currentPage - 1)));
                case ACTION_NEXT -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_LIST).withParam("page", String.valueOf(currentPage + 1)));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                    if (actionId.startsWith(CMD_PREFIX)) {
                        String cmdName = actionId.substring(CMD_PREFIX.length());
                        context.openRoute(MenuRoute.of(ROUTE_DETAILS)
                                .withParam("cmd", cmdName)
                                .withParam("returnPage", String.valueOf(currentPage)));
                    }
                }
            }
        }
    }

    static final class HelpDetailsFlow implements RoutedMenuFlow<HelpDetailsState> {
        private final HelpMenu menu;

        HelpDetailsFlow(HelpMenu menu) {
            this.menu = menu;
        }

        @Override
        public String routeId() {
            return ROUTE_DETAILS;
        }

        @Override
        public HelpDetailsState createState(Session session, MenuRoute route, HelpDetailsState currentState) {
            return currentState == null ? new HelpDetailsState() : currentState;
        }

        @Override
        public Class<HelpDetailsState> stateType() {
            return HelpDetailsState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<HelpDetailsState> context) {
            Session session = context.session();
            XCoreSender sender = session.sender;
            String cmdName = context.route().param("cmd");

            if (sender == null || cmdName == null) {
                return errorScreen(session);
            }

            List<HelpMenu.UnifiedCommand> allCommands = menu.collectAllCommands(sender);
            HelpMenu.UnifiedCommand cmd = allCommands.stream()
                    .filter(command -> command.name().equalsIgnoreCase(cmdName))
                    .findFirst()
                    .orElse(null);

            if (cmd == null) {
                return MenuScreen.normal(
                        session.locale().t("help-menu-title"),
                        session.locale().t("error-command-not-found"),
                        List.of(List.of(MenuButton.of(session.locale().t("back"), ACTION_BACK)))
                );
            }

            String title = session.locale().t("help-command-title", args("name", cmd.name()));
            String content = menu.buildCommandContent(session, cmd);
            var local = context.locale();

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(MenuButton.of(local.t("help-back"), ACTION_BACK)));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(title, content, rows);
        }

        @Override
        public void onAction(MenuRenderContext<HelpDetailsState> context, String actionId) {
            switch (actionId) {
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
            }
        }
    }

    static final class HelpListState {
    }

    static final class HelpDetailsState {
    }

    private static MenuScreen errorScreen(Session session) {
        return MenuScreen.normal(
                session.locale().t("help-menu-title"),
                "",
                List.of(List.of(MenuButton.of(session.locale().t("close"), ACTION_CLOSE)))
        );
    }
}
