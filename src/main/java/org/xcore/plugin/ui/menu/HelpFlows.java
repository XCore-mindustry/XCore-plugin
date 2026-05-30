package org.xcore.plugin.ui.menu;

import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.flow.NoState;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.Comparator;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

final class HelpFlows {

    static final String ROUTE_LIST = "help.list";
    static final String ROUTE_DETAILS = "help.details";

    private static final int MAX_DESC_LEN = 40;

    private HelpFlows() {
    }

    static final class HelpListFlow extends BaseMenuFlow<NoState> {
        private final HelpMenu menu;

        HelpListFlow(HelpMenu menu) {
            super(ROUTE_LIST, NoState.class);
            this.menu = menu;

            action("previous", ctx -> {
                int currentPage = ctx.route().intParam("page", 1);
                ctx.session().menuService.renderRoute(ctx.session(),
                        MenuRoute.of(ROUTE_LIST).withParam("page", String.valueOf(currentPage - 1)));
            });
            action("next", ctx -> {
                int currentPage = ctx.route().intParam("page", 1);
                ctx.session().menuService.renderRoute(ctx.session(),
                        MenuRoute.of(ROUTE_LIST).withParam("page", String.valueOf(currentPage + 1)));
            });
            actionPrefix("cmd:", (ctx, cmdName) -> {
                int currentPage = ctx.route().intParam("page", 1);
                ctx.openRoute(MenuRoute.of(ROUTE_DETAILS)
                        .withParam("cmd", cmdName)
                        .withParam("returnPage", String.valueOf(currentPage)));
            });
        }

        @Override
        public MenuScreen render(MenuRenderContext<NoState> context) {
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

            var pagination = CustomGatherers.calculatePagination(allCommands.size(), menu.secretsConfig.pagination.commandsPerPage);
            int currentPage = pagination.clampPage(page);
            int skip = (currentPage - 1) * menu.secretsConfig.pagination.commandsPerPage;
            List<HelpMenu.UnifiedCommand> pageSlice = allCommands.subList(skip, Math.min(skip + menu.secretsConfig.pagination.commandsPerPage, allCommands.size()));

            var local = context.locale();
            var grid = new MenuGrid()
                    .pagination(currentPage, pagination.totalPages(), "previous", "next", local);

            for (HelpMenu.UnifiedCommand cmd : pageSlice) {
                String desc = menu.resolveDescription(session, cmd);
                String btnText = local.t("help-menu-button", args(
                        "command", menu.formatCommandLabel(session, cmd),
                        "description", menu.truncate(desc, MAX_DESC_LEN)
                ));
                grid.row(MenuButton.of(btnText, "cmd:" + cmd.name()));
            }

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("help-menu-title"),
                    local.t("help-menu-content", args("page", currentPage, "total", pagination.totalPages())),
                    grid.build()
            );
        }
    }

    static final class HelpDetailsFlow extends BaseMenuFlow<NoState> {
        private final HelpMenu menu;

        HelpDetailsFlow(HelpMenu menu) {
            super(ROUTE_DETAILS, NoState.class);
            this.menu = menu;
        }

        @Override
        public MenuScreen render(MenuRenderContext<NoState> context) {
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
                        List.of(List.of(MenuButton.of(session.locale().t("back"), "back")))
                );
            }

            String title = session.locale().t("help-command-title", args("name", cmd.name()));
            String content = menu.buildCommandContent(session, cmd);
            var local = context.locale();

            var grid = new MenuGrid()
                    .row(MenuButton.of(local.t("help-back"), "back"))
                    .defaultNavigation(session, local);

            return MenuScreen.normal(title, content, grid.build());
        }
    }

    private static MenuScreen errorScreen(Session session) {
        return MenuScreen.normal(
                session.locale().t("help-menu-title"),
                "",
                List.of(List.of(MenuButton.of(session.locale().t("close"), "close")))
        );
    }
}
