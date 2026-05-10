package org.xcore.plugin.ui.menu;

import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

final class DiscordFlows {

    static final String ROUTE_MAIN = "discord.main";
    static final String ROUTE_LINKING = "discord.linking";

    private static final String ACTION_OPEN = "open";
    private static final String ACTION_STATUS = "status";
    private static final String ACTION_LINK = "link";
    private static final String ACTION_UNLINK = "unlink";
    private static final String ACTION_COPY = "copy";
    private static final String ACTION_REFRESH = "refresh";
    private static final String ACTION_REGENERATE = "regenerate";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";

    private DiscordFlows() {
    }

    static final class MainFlow implements RoutedMenuFlow<MainState> {
        private final DiscordMenu menu;
        private final DiscordLinkService discordLinkService;

        MainFlow(DiscordMenu menu, DiscordLinkService discordLinkService) {
            this.menu = menu;
            this.discordLinkService = discordLinkService;
        }

        @Override
        public String routeId() {
            return ROUTE_MAIN;
        }

        @Override
        public MainState createState(Session session, MenuRoute route, MainState currentState) {
            return currentState == null ? new MainState() : currentState;
        }

        @Override
        public Class<MainState> stateType() {
            return MainState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<MainState> context) {
            Session session = context.session();
            var local = context.locale();
            var status = discordLinkService.status(session);
            String statusText = status.linked()
                    ? local.t("discord-menu-status-linked", args(
                            "discordId", status.discordId(),
                            "discordUsername", status.displayName()
                    ))
                    : local.t("discord-menu-status-not-linked", args());

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    MenuButton.of(local.t("discord-menu-open"), ACTION_OPEN),
                    MenuButton.of(local.t("discord-menu-status"), ACTION_STATUS)
            ));

            if (!status.linked()) {
                rows.add(List.of(MenuButton.of(local.t("discord-menu-link"), ACTION_LINK)));
            } else {
                rows.add(List.of(MenuButton.of(local.t("discord-menu-unlink"), ACTION_UNLINK)));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.hasHistory() || session.hasRouteHistory()) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("discord-menu-title"),
                    local.t("discord-menu-content", args(
                            "status", statusText,
                            "discordUrl", menu.globalConfig.discordUrl
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<MainState> context, String actionId) {
            Session session = context.session();
            switch (actionId) {
                case ACTION_OPEN -> session.menuService.openUri(session, menu.globalConfig.discordUrl);
                case ACTION_STATUS -> context.render();
                case ACTION_LINK -> {
                    session.clearDraft(LinkingState.class);
                    context.openRoute(MenuRoute.of(ROUTE_LINKING).withParam("regenerate", "false"));
                }
                case ACTION_UNLINK -> {
                    var local = session.locale();
                    if (!discordLinkService.unlink(session)) {
                        local.send("commands-discord-unlink-not-linked", args());
                    } else {
                        local.send("commands-discord-unlink-success", args());
                    }
                    context.render();
                }
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }
    }

    static final class LinkingFlow implements RoutedMenuFlow<LinkingState> {
        private final DiscordMenu menu;
        private final DiscordLinkService discordLinkService;

        LinkingFlow(DiscordMenu menu, DiscordLinkService discordLinkService) {
            this.menu = menu;
            this.discordLinkService = discordLinkService;
        }

        @Override
        public String routeId() {
            return ROUTE_LINKING;
        }

        @Override
        public LinkingState createState(Session session, MenuRoute route, LinkingState currentState) {
            if (currentState != null && currentState.result != null && currentState.result.success()) {
                return currentState;
            }
            boolean regenerate = Boolean.parseBoolean(route.param("regenerate"));
            var result = regenerate
                    ? discordLinkService.createCode(session)
                    : discordLinkService.getOrCreateActiveCode(session);
            return new LinkingState(result);
        }

        @Override
        public Class<LinkingState> stateType() {
            return LinkingState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<LinkingState> context) {
            Session session = context.session();
            var local = context.locale();
            var result = context.state().result;

            if (result == null || !result.success()) {
                List<List<MenuButton>> rows = new ArrayList<>();
                rows.add(List.of(MenuButton.of(local.t("discord-link-menu-status"), ACTION_STATUS)));
                List<MenuButton> nav = new ArrayList<>();
                if (session.hasHistory() || session.hasRouteHistory()) {
                    nav.add(MenuButton.of(local.t("back"), ACTION_BACK));
                }
                nav.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
                rows.add(nav);
                return MenuScreen.followUp(
                        local.t("discord-link-menu-title"),
                        local.t("discord-link-menu-content", args(
                                "code", "----",
                                "expireMinutes", 0,
                                "discordUrl", menu.globalConfig.discordUrl
                        )),
                        rows
                );
            }

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    MenuButton.of(local.t("discord-menu-open"), ACTION_OPEN),
                    MenuButton.of(local.t("discord-link-menu-copy"), ACTION_COPY)
            ));
            rows.add(List.of(
                    MenuButton.of(local.t("discord-link-menu-refresh"), ACTION_REFRESH),
                    MenuButton.of(local.t("discord-link-menu-regenerate"), ACTION_REGENERATE),
                    MenuButton.of(local.t("discord-link-menu-status"), ACTION_STATUS)
            ));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.hasHistory() || session.hasRouteHistory()) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.followUp(
                    local.t("discord-link-menu-title"),
                    local.t("discord-link-menu-content", args(
                            "code", result.code(),
                            "expireMinutes", result.remainingMinutes(System.currentTimeMillis()),
                            "discordUrl", menu.globalConfig.discordUrl
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<LinkingState> context, String actionId) {
            Session session = context.session();
            var result = context.state().result;
            switch (actionId) {
                case ACTION_OPEN -> session.menuService.openUri(session, menu.globalConfig.discordUrl);
                case ACTION_COPY -> {
                    if (result != null && result.success()) {
                        session.menuService.copyToClipboard(session, result.code());
                    }
                }
                case ACTION_REFRESH -> {
                    session.clearDraft(LinkingState.class);
                    context.openRoute(MenuRoute.of(ROUTE_LINKING).withParam("regenerate", "false"));
                }
                case ACTION_REGENERATE -> {
                    session.clearDraft(LinkingState.class);
                    context.openRoute(MenuRoute.of(ROUTE_LINKING).withParam("regenerate", "true"));
                }
                case ACTION_STATUS -> {
                    context.close();
                    context.openRoute(MenuRoute.of(ROUTE_MAIN));
                }
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }
    }

    static final class MainState {
    }

    static final class LinkingState {
        public DiscordLinkService.LinkCodeResult result;

        LinkingState() {
        }

        LinkingState(DiscordLinkService.LinkCodeResult result) {
            this.result = result;
        }
    }
}
