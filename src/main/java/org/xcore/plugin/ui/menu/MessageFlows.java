package org.xcore.plugin.ui.menu;

import org.bson.types.ObjectId;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import static com.ospx.flubundle.Bundle.args;

final class MessageFlows {

    static final String ROUTE_INBOX = "message.inbox";
    static final String ROUTE_BLOCKED = "message.blocked";
    static final String ROUTE_DETAILS = "message.details";

    private static final String PROMPT_REPLY = "private-message-reply";
    private static final String PROMPT_COMPOSE_TARGET = "private-message-compose-target";
    private static final String PROMPT_COMPOSE_BODY = "private-message-compose-body";

    private MessageFlows() {
    }

    static final class InboxFlow extends BaseMenuFlow<InboxState> {
        private final MessageMenu menu;
        private final PrivateMessageService privateMessageService;

        InboxFlow(MessageMenu menu, PrivateMessageService privateMessageService) {
            super(ROUTE_INBOX, InboxState.class);
            this.menu = menu;
            this.privateMessageService = privateMessageService;

            action("previous", ctx -> {
                Session session = ctx.session();
                int currentPage = Math.max(1, ctx.state().page);
                session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_INBOX).withParam("page", String.valueOf(currentPage - 1)));
            });
            action("next", ctx -> {
                Session session = ctx.session();
                int currentPage = Math.max(1, ctx.state().page);
                session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_INBOX).withParam("page", String.valueOf(currentPage + 1)));
            });
            action("compose", ctx -> promptCompose(menu, privateMessageService, menu.getUuid(ctx.session()), ctx::render));
            action("blocked", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_BLOCKED).withParam("page", "1")));
            actionPrefix("message:", (ctx, messageIdHex) -> {
                ObjectId messageId = new ObjectId(messageIdHex);
                int currentPage = Math.max(1, ctx.state().page);
                ctx.openRoute(MenuRoute.of(ROUTE_DETAILS)
                        .withParam("messageId", messageId.toHexString())
                        .withParam("returnPage", String.valueOf(currentPage)));
            });
        }

        @Override
        public InboxState createState(Session session, MenuRoute route, InboxState currentState) {
            InboxState state = currentState == null ? new InboxState() : currentState;
            state.page = route.intParam("page", 1);
            return state;
        }

        @Override
        public MenuScreen render(MenuRenderContext<InboxState> context) {
            Session session = context.session();
            String uuid = menu.getUuid(session);
            int requestedPage = Math.max(1, context.state().page);

            long total = privateMessageService.countInbox(session.data.uuid);
            var pagination = CustomGatherers.calculatePagination(total, menu.globalConfig.privateMessagesPerPage);
            int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(requestedPage);
            context.state().page = validPage;
            java.util.List<PrivateMessage> messages = privateMessageService.inbox(session.data.uuid, validPage);

            var grid = new MenuGrid();
            var pagingRow = new java.util.ArrayList<MenuButton>();
            if (validPage > 1) {
                pagingRow.add(MenuButton.of(session.locale().t("previous"), "previous"));
            }
            if (validPage < Math.max(1, pagination.totalPages())) {
                pagingRow.add(MenuButton.of(session.locale().t("next"), "next"));
            }
            if (!pagingRow.isEmpty()) {
                grid.row(pagingRow.toArray(new MenuButton[0]));
            }

            for (PrivateMessage message : messages) {
                String text = session.locale().t(
                        message.readAt > 0 ? "private-message-menu-entry-read" : "private-message-menu-entry-unread",
                        args(
                                "author", message.fromName,
                                "pid", message.fromPid,
                                "message", preview(message.message),
                                "time", menu.formatTime(message.createdModelTime, session)
                        )
                );
                grid.row(MenuButton.of(text, "message:" + message.id.toHexString()));
            }

            grid.row(
                    MenuButton.of(session.locale().t("private-message-compose"), "compose"),
                    MenuButton.of(session.locale().t("private-message-blocked"), "blocked")
            );

            grid.defaultNavigation(session, session.locale());

            return MenuScreen.normal(
                    session.locale().t("private-message-menu-title"),
                    session.locale().t(total == 0 ? "private-message-menu-empty" : "private-message-menu-content", args(
                            "page", validPage,
                            "total", Math.max(1, pagination.totalPages()),
                            "unread", privateMessageService.countUnread(session.data.uuid)
                    )),
                    grid.build()
            );
        }
    }

    static final class DetailsFlow extends BaseMenuFlow<DetailsState> {
        private final MessageMenu menu;
        private final PrivateMessageService privateMessageService;

        DetailsFlow(MessageMenu menu, PrivateMessageService privateMessageService) {
            super(ROUTE_DETAILS, DetailsState.class);
            this.menu = menu;
            this.privateMessageService = privateMessageService;

            action("reply", ctx -> {
                DetailsView view = resolveDetailsView(ctx.session(), ctx.state(), privateMessageService);
                if (view != null) {
                    ctx.openPrompt(new MenuPrompt(
                            PROMPT_REPLY,
                            ctx.session().locale().t("private-message-reply-title"),
                            ctx.session().locale().t("private-message-reply-message", args("pid", view.message.fromPid)),
                            menu.globalConfig.privateMessageMaxLength,
                            "",
                            false
                    ));
                }
            });
            action("delete", ctx -> {
                Session session = ctx.session();
                DetailsView view = resolveDetailsView(session, ctx.state(), privateMessageService);
                if (view == null) {
                    if (ctx.session().hasRouteHistory()) {
                        ctx.goBack();
                    } else {
                        menu.inbox(menu.getUuid(session), ctx.state().returnPage);
                    }
                    return;
                }
                privateMessageService.softDelete(view.message.id, session.data.uuid);
                if (session.hasRouteHistory()) {
                    ctx.goBack();
                } else {
                    menu.inbox(menu.getUuid(session), ctx.state().returnPage);
                }
            });
            action("toggle-block", ctx -> {
                Session session = ctx.session();
                DetailsView view = resolveDetailsView(session, ctx.state(), privateMessageService);
                if (view != null) {
                    if (view.blocked) {
                        privateMessageService.unblock(session, view.message.fromPid);
                    } else {
                        privateMessageService.block(session, view.message.fromPid);
                    }
                    ctx.render();
                }
            });

            onPrompt(PROMPT_REPLY,
                    ctx -> {
                        Session session = ctx.renderContext().session();
                        DetailsView view = resolveDetailsView(session, ctx.renderContext().state(), privateMessageService);
                        if (view != null) {
                            privateMessageService.send(session, view.message.fromPid, ctx.text());
                        }
                        ctx.renderContext().render();
                    },
                    ctx -> ctx.render()
            );
        }

        @Override
        public DetailsState createState(Session session, MenuRoute route, DetailsState currentState) {
            DetailsState state = currentState == null ? new DetailsState() : currentState;
            state.messageId = route.param("messageId");
            state.returnPage = route.intParam("returnPage", 1);
            return state;
        }

        @Override
        public MenuScreen render(MenuRenderContext<DetailsState> context) {
            Session session = context.session();
            DetailsView view = resolveDetailsView(session, context.state(), privateMessageService);
            if (view == null) {
                return notFoundScreen(session);
            }

            var grid = new MenuGrid();
            grid.row(
                    MenuButton.of(session.locale().t("reply"), "reply"),
                    MenuButton.of(session.locale().t("delete"), "delete")
            );
            grid.row(MenuButton.of(
                    session.locale().t(view.blocked ? "private-message-unblock" : "private-message-block"),
                    "toggle-block"
            ));
            grid.defaultNavigation(session, session.locale());

            return MenuScreen.normal(
                    session.locale().t("private-message-details-title"),
                    session.locale().t("private-message-details-content", args(
                            "author", view.message.fromName,
                            "pid", view.message.fromPid,
                            "time", menu.formatTime(view.message.createdModelTime, session),
                            "message", view.message.message,
                            "status", session.locale().t(view.markedRead || view.message.readAt > 0 ? "private-message-status-read" : "private-message-status-unread")
                    )),
                    grid.build()
            );
        }
    }

    static final class BlockedFlow extends BaseMenuFlow<BlockedState> {
        private final MessageMenu menu;
        private final PrivateMessageService privateMessageService;

        BlockedFlow(MessageMenu menu, PrivateMessageService privateMessageService) {
            super(ROUTE_BLOCKED, BlockedState.class);
            this.menu = menu;
            this.privateMessageService = privateMessageService;

            action("previous", ctx -> {
                Session session = ctx.session();
                int currentPage = Math.max(1, ctx.state().page);
                session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_BLOCKED).withParam("page", String.valueOf(currentPage - 1)));
            });
            action("next", ctx -> {
                Session session = ctx.session();
                int currentPage = Math.max(1, ctx.state().page);
                session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_BLOCKED).withParam("page", String.valueOf(currentPage + 1)));
            });
            actionPrefix("unblock:", (ctx, pidStr) -> {
                int pid = Integer.parseInt(pidStr);
                privateMessageService.unblock(ctx.session(), pid);
                ctx.render();
            });
        }

        @Override
        public BlockedState createState(Session session, MenuRoute route, BlockedState currentState) {
            BlockedState state = currentState == null ? new BlockedState() : currentState;
            state.page = route.intParam("page", 1);
            return state;
        }

        @Override
        public MenuScreen render(MenuRenderContext<BlockedState> context) {
            Session session = context.session();
            int requestedPage = Math.max(1, context.state().page);

            java.util.List<PlayerData> blockedPlayers = privateMessageService.listBlocked(session);
            var pagination = CustomGatherers.calculatePagination(blockedPlayers.size(), menu.globalConfig.privateMessagesPerPage);
            int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(requestedPage);
            context.state().page = validPage;
            int start = (validPage - 1) * menu.globalConfig.privateMessagesPerPage;
            int end = Math.min(start + menu.globalConfig.privateMessagesPerPage, blockedPlayers.size());
            java.util.List<PlayerData> pageItems = start >= blockedPlayers.size() ? java.util.List.of() : blockedPlayers.subList(start, end);

            var grid = new MenuGrid();
            var pagingRow = new java.util.ArrayList<MenuButton>();
            if (validPage > 1) {
                pagingRow.add(MenuButton.of(session.locale().t("previous"), "previous"));
            }
            if (validPage < Math.max(1, pagination.totalPages())) {
                pagingRow.add(MenuButton.of(session.locale().t("next"), "next"));
            }
            if (!pagingRow.isEmpty()) {
                grid.row(pagingRow.toArray(new MenuButton[0]));
            }

            for (PlayerData playerData : pageItems) {
                grid.row(MenuButton.of(
                        session.locale().t("private-message-blocked-entry", args("target", playerData.nickname, "pid", playerData.pid)),
                        "unblock:" + playerData.pid
                ));
            }

            grid.defaultNavigation(session, session.locale());

            return MenuScreen.normal(
                    session.locale().t("private-message-blocked-title"),
                    session.locale().t(blockedPlayers.isEmpty() ? "private-message-blocked-empty" : "private-message-blocked-content", args(
                            "page", validPage,
                            "total", Math.max(1, pagination.totalPages()),
                            "count", blockedPlayers.size()
                    )),
                    grid.build()
            );
        }
    }

    static final class InboxState {
        public int page = 1;
    }

    static final class BlockedState {
        public int page = 1;
    }

    static final class DetailsState {
        public String messageId;
        public int returnPage = 1;
    }

    private static void promptCompose(MessageMenu menu, PrivateMessageService privateMessageService, String uuid, Runnable onBack) {
        Session session = menu.sessionService.get(uuid);
        if (session == null || session.data == null) return;

        session.menuService.openPrompt(session,
                new MenuPrompt(
                        PROMPT_COMPOSE_TARGET,
                        session.locale().t("private-message-compose-target-title"),
                        session.locale().t("private-message-compose-target-message"),
                        32,
                        session.lastPrivateTargetPid == null ? "" : "#" + session.lastPrivateTargetPid,
                        false),
                pidText -> handleComposeTarget(menu, privateMessageService, uuid, onBack, pidText),
                onBack);
    }

    private static void handleComposeTarget(MessageMenu menu,
                                             PrivateMessageService privateMessageService,
                                             String uuid,
                                             Runnable onBack,
                                             String pidText) {
        Session session = menu.sessionService.get(uuid);
        if (session == null || session.data == null) return;

        Integer targetPid = privateMessageService.parseMenuPid(pidText);
        if (targetPid == null) {
            session.locale().send("error-private-message-invalid-pid", args());
            onBack.run();
            return;
        }

        session.menuService.openPrompt(session,
                new MenuPrompt(
                        PROMPT_COMPOSE_BODY,
                        session.locale().t("private-message-compose-body-title"),
                        session.locale().t("private-message-compose-body-message", args("pid", "#" + targetPid)),
                        menu.globalConfig.privateMessageMaxLength,
                        "",
                        false),
                message -> {
                    privateMessageService.send(session, targetPid, message);
                    onBack.run();
                },
                onBack);
    }

    private static String preview(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 40 ? message : message.substring(0, 37) + "...";
    }

    private static DetailsView resolveDetailsView(Session session,
                                                    DetailsState state,
                                                    PrivateMessageService privateMessageService) {
        if (session == null || session.data == null || state == null || state.messageId == null) {
            return null;
        }

        ObjectId messageId;
        try {
            messageId = new ObjectId(state.messageId);
        } catch (IllegalArgumentException e) {
            return null;
        }

        PrivateMessage message = privateMessageService.getMessage(messageId, session.data.uuid);
        if (message == null) {
            return null;
        }

        boolean markedRead = privateMessageService.markRead(messageId, session.data.uuid);
        boolean blocked = session.data.blockedPrivateUuids != null && session.data.blockedPrivateUuids.contains(message.fromUuid);
        return new DetailsView(message, markedRead, blocked);
    }

    private static MenuScreen notFoundScreen(Session session) {
        return MenuScreen.normal(
                session.locale().t("private-message-details-title"),
                session.locale().t("error-private-message-not-found"),
                new MenuGrid().row(
                        MenuButton.of(session.locale().t("back"), "back"),
                        MenuButton.of(session.locale().t("close"), "close")
                ).build()
        );
    }

    private record DetailsView(PrivateMessage message, boolean markedRead, boolean blocked) {
    }
}
