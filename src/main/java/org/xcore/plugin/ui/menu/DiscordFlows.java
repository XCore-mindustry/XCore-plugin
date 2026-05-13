package org.xcore.plugin.ui.menu;

import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.flow.NoState;
import org.xcore.plugin.ui.route.MenuRoute;

import static com.ospx.flubundle.Bundle.args;

final class DiscordFlows {

    static final String ROUTE_MAIN = "discord.main";
    static final String ROUTE_LINKING = "discord.linking";

    private DiscordFlows() {
    }

    static final class MainFlow extends BaseMenuFlow<NoState> {
        private final DiscordMenu menu;
        private final DiscordLinkService discordLinkService;

        MainFlow(DiscordMenu menu, DiscordLinkService discordLinkService) {
            super(ROUTE_MAIN, NoState.class);
            this.menu = menu;
            this.discordLinkService = discordLinkService;

            action("open", ctx -> ctx.session().menuService.openUri(ctx.session(), menu.globalConfig.discordUrl));
            action("status", ctx -> ctx.render());
            action("link", ctx -> {
                ctx.session().clearDraft(LinkingState.class);
                ctx.openRoute(MenuRoute.of(ROUTE_LINKING).withParam("regenerate", "false"));
            });
            action("unlink", ctx -> {
                var local = ctx.session().locale();
                if (!discordLinkService.unlink(ctx.session())) {
                    local.send("commands-discord-unlink-not-linked", args());
                } else {
                    local.send("commands-discord-unlink-success", args());
                }
                ctx.render();
            });
        }

        @Override
        public MenuScreen render(MenuRenderContext<NoState> context) {
            Session session = context.session();
            var local = context.locale();
            var status = discordLinkService.status(session);
            String statusText = status.linked()
                    ? local.t("discord-menu-status-linked", args(
                            "discordId", status.discordId(),
                            "discordUsername", status.displayName()
                    ))
                    : local.t("discord-menu-status-not-linked", args());

            var grid = new MenuGrid()
                    .row(
                            MenuButton.of(local.t("discord-menu-open"), "open"),
                            MenuButton.of(local.t("discord-menu-status"), "status")
                    );
            if (!status.linked()) {
                grid.row(MenuButton.of(local.t("discord-menu-link"), "link"));
            } else {
                grid.row(MenuButton.of(local.t("discord-menu-unlink"), "unlink"));
            }
            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("discord-menu-title"),
                    local.t("discord-menu-content", args(
                            "status", statusText,
                            "discordUrl", menu.globalConfig.discordUrl
                    )),
                    grid.build()
            );
        }
    }

    static final class LinkingFlow extends BaseMenuFlow<LinkingState> {
        private final DiscordMenu menu;
        private final DiscordLinkService discordLinkService;

        LinkingFlow(DiscordMenu menu, DiscordLinkService discordLinkService) {
            super(ROUTE_LINKING, LinkingState.class);
            this.menu = menu;
            this.discordLinkService = discordLinkService;

            action("open", ctx -> ctx.session().menuService.openUri(ctx.session(), menu.globalConfig.discordUrl));
            action("copy", ctx -> {
                var result = ctx.state().result;
                if (result != null && result.success()) {
                    ctx.session().menuService.copyToClipboard(ctx.session(), result.code());
                }
            });
            action("refresh", ctx -> {
                ctx.session().clearDraft(LinkingState.class);
                ctx.openRoute(MenuRoute.of(ROUTE_LINKING).withParam("regenerate", "false"));
            });
            action("regenerate", ctx -> {
                ctx.session().clearDraft(LinkingState.class);
                ctx.openRoute(MenuRoute.of(ROUTE_LINKING).withParam("regenerate", "true"));
            });
            action("status", ctx -> {
                ctx.close();
                ctx.openRoute(MenuRoute.of(ROUTE_MAIN));
            });
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
        public MenuScreen render(MenuRenderContext<LinkingState> context) {
            Session session = context.session();
            var local = context.locale();
            var result = context.state().result;

            if (result == null || !result.success()) {
                var grid = new MenuGrid()
                        .row(MenuButton.of(local.t("discord-link-menu-status"), "status"))
                        .defaultNavigation(session, local);
                return MenuScreen.followUp(
                        local.t("discord-link-menu-title"),
                        local.t("discord-link-menu-content", args(
                                "code", "----",
                                "expireMinutes", 0,
                                "discordUrl", menu.globalConfig.discordUrl
                        )),
                        grid.build()
                );
            }

            var grid = new MenuGrid()
                    .row(
                            MenuButton.of(local.t("discord-menu-open"), "open"),
                            MenuButton.of(local.t("discord-link-menu-copy"), "copy")
                    )
                    .row(
                            MenuButton.of(local.t("discord-link-menu-refresh"), "refresh"),
                            MenuButton.of(local.t("discord-link-menu-regenerate"), "regenerate"),
                            MenuButton.of(local.t("discord-link-menu-status"), "status")
                    )
                    .defaultNavigation(session, local);

            return MenuScreen.followUp(
                    local.t("discord-link-menu-title"),
                    local.t("discord-link-menu-content", args(
                            "code", result.code(),
                            "expireMinutes", result.remainingMinutes(System.currentTimeMillis()),
                            "discordUrl", menu.globalConfig.discordUrl
                    )),
                    grid.build()
            );
        }
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
