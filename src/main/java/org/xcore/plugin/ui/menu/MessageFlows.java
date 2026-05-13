package org.xcore.plugin.ui.menu;

import org.bson.types.ObjectId;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

final class MessageFlows {

    static final String ROUTE_INBOX = "message.inbox";
    static final String ROUTE_BLOCKED = "message.blocked";
    static final String ROUTE_DETAILS = "message.details";

    private static final String ACTION_PREVIOUS = "previous";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_COMPOSE = "compose";
    private static final String ACTION_BLOCKED = "blocked";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_MESSAGE_PREFIX = "message:";
    private static final String ACTION_UNBLOCK_PREFIX = "unblock:";
    private static final String ACTION_REPLY = "reply";
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_TOGGLE_BLOCK = "toggle-block";

    private static final String PROMPT_REPLY = "private-message-reply";
    private static final String PROMPT_COMPOSE_TARGET = "private-message-compose-target";
    private static final String PROMPT_COMPOSE_BODY = "private-message-compose-body";

    private MessageFlows() {
    }

    static final class InboxFlow implements RoutedMenuFlow<InboxState> {
        private final MessageMenu menu;
        private final PrivateMessageService privateMessageService;

        InboxFlow(MessageMenu menu, PrivateMessageService privateMessageService) {
            this.menu = menu;
            this.privateMessageService = privateMessageService;
        }

        @Override
        public String routeId() {
            return ROUTE_INBOX;
        }

        @Override
        public InboxState createState(Session session, MenuRoute route, InboxState currentState) {
            InboxState state = currentState == null ? new InboxState() : currentState;
            state.page = route.intParam("page", 1);
            return state;
        }

        @Override
        public Class<InboxState> stateType() {
            return InboxState.class;
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
            List<PrivateMessage> messages = privateMessageService.inbox(session.data.uuid, validPage);

            List<List<MenuButton>> rows = new ArrayList<>();

            List<MenuButton> pagingRow = new ArrayList<>();
            if (validPage > 1) {
                pagingRow.add(MenuButton.of(session.locale().t("previous"), ACTION_PREVIOUS));
            }
            if (validPage < Math.max(1, pagination.totalPages())) {
                pagingRow.add(MenuButton.of(session.locale().t("next"), ACTION_NEXT));
            }
            if (!pagingRow.isEmpty()) {
                rows.add(pagingRow);
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
                rows.add(List.of(MenuButton.of(text, ACTION_MESSAGE_PREFIX + message.id.toHexString())));
            }

            rows.add(List.of(
                    MenuButton.of(session.locale().t("private-message-compose"), ACTION_COMPOSE),
                    MenuButton.of(session.locale().t("private-message-blocked"), ACTION_BLOCKED)
            ));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack()) {
                navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    session.locale().t("private-message-menu-title"),
                    session.locale().t(total == 0 ? "private-message-menu-empty" : "private-message-menu-content", args(
                            "page", validPage,
                            "total", Math.max(1, pagination.totalPages()),
                            "unread", privateMessageService.countUnread(session.data.uuid)
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<InboxState> context, String actionId) {
            Session session = context.session();
            String uuid = menu.getUuid(session);
            int currentPage = Math.max(1, context.state().page);

            switch (actionId) {
                case ACTION_PREVIOUS -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_INBOX).withParam("page", String.valueOf(currentPage - 1)));
                case ACTION_NEXT -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_INBOX).withParam("page", String.valueOf(currentPage + 1)));
                case ACTION_COMPOSE -> promptCompose(menu, privateMessageService, uuid, context::render);
                case ACTION_BLOCKED -> context.openRoute(MenuRoute.of(ROUTE_BLOCKED).withParam("page", "1"));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                    if (actionId.startsWith(ACTION_MESSAGE_PREFIX)) {
                        ObjectId messageId = new ObjectId(actionId.substring(ACTION_MESSAGE_PREFIX.length()));
                        context.openRoute(MenuRoute.of(ROUTE_DETAILS)
                                .withParam("messageId", messageId.toHexString())
                                .withParam("returnPage", String.valueOf(currentPage)));
                    }
                }
            }
        }
    }

    static final class DetailsFlow implements RoutedMenuFlow<DetailsState> {
        private final MessageMenu menu;
        private final PrivateMessageService privateMessageService;

        DetailsFlow(MessageMenu menu, PrivateMessageService privateMessageService) {
            this.menu = menu;
            this.privateMessageService = privateMessageService;
        }

        @Override
        public String routeId() {
            return ROUTE_DETAILS;
        }

        @Override
        public DetailsState createState(Session session, MenuRoute route, DetailsState currentState) {
            DetailsState state = currentState == null ? new DetailsState() : currentState;
            state.messageId = route.param("messageId");
            state.returnPage = route.intParam("returnPage", 1);
            return state;
        }

        @Override
        public Class<DetailsState> stateType() {
            return DetailsState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<DetailsState> context) {
            Session session = context.session();
            DetailsView view = resolveDetailsView(session, context.state(), privateMessageService);
            if (view == null) {
                return notFoundScreen(session);
            }

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    MenuButton.of(session.locale().t("reply"), ACTION_REPLY),
                    MenuButton.of(session.locale().t("delete"), ACTION_DELETE)
            ));
            rows.add(List.of(MenuButton.of(
                    session.locale().t(view.blocked ? "private-message-unblock" : "private-message-block"),
                    ACTION_TOGGLE_BLOCK
            )));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack()) {
                navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    session.locale().t("private-message-details-title"),
                    session.locale().t("private-message-details-content", args(
                            "author", view.message.fromName,
                            "pid", view.message.fromPid,
                            "time", menu.formatTime(view.message.createdModelTime, session),
                            "message", view.message.message,
                            "status", session.locale().t(view.markedRead || view.message.readAt > 0 ? "private-message-status-read" : "private-message-status-unread")
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<DetailsState> context, String actionId) {
            Session session = context.session();
            DetailsView view = resolveDetailsView(session, context.state(), privateMessageService);
            if (view == null) {
                if (ACTION_BACK.equals(actionId)) {
                    context.goBack();
                } else if (ACTION_CLOSE.equals(actionId)) {
                    context.close();
                }
                return;
            }

            switch (actionId) {
                case ACTION_REPLY -> context.openPrompt(new MenuPrompt(
                        PROMPT_REPLY,
                        session.locale().t("private-message-reply-title"),
                        session.locale().t("private-message-reply-message", args("pid", view.message.fromPid)),
                        menu.globalConfig.privateMessageMaxLength,
                        "",
                        false
                ));
                case ACTION_DELETE -> {
                    privateMessageService.softDelete(view.message.id, session.data.uuid);
                    if (session.hasRouteHistory()) {
                        context.goBack();
                    } else {
                        menu.inbox(menu.getUuid(session), context.state().returnPage);
                    }
                }
                case ACTION_TOGGLE_BLOCK -> {
                    if (view.blocked) {
                        privateMessageService.unblock(session, view.message.fromPid);
                    } else {
                        privateMessageService.block(session, view.message.fromPid);
                    }
                    context.render();
                }
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }

        @Override
        public void onPromptSubmit(MenuRenderContext<DetailsState> context, String promptId, String text) {
            if (!PROMPT_REPLY.equals(promptId)) {
                return;
            }

            Session session = context.session();
            DetailsView view = resolveDetailsView(session, context.state(), privateMessageService);
            if (view == null) {
                context.render();
                return;
            }

            privateMessageService.send(session, view.message.fromPid, text);
            context.render();
        }

        @Override
        public void onPromptCancel(MenuRenderContext<DetailsState> context, String promptId) {
            if (PROMPT_REPLY.equals(promptId)) {
                context.render();
            }
        }
    }

    static final class BlockedFlow implements RoutedMenuFlow<BlockedState> {
        private final MessageMenu menu;
        private final PrivateMessageService privateMessageService;

        BlockedFlow(MessageMenu menu, PrivateMessageService privateMessageService) {
            this.menu = menu;
            this.privateMessageService = privateMessageService;
        }

        @Override
        public String routeId() {
            return ROUTE_BLOCKED;
        }

        @Override
        public BlockedState createState(Session session, MenuRoute route, BlockedState currentState) {
            BlockedState state = currentState == null ? new BlockedState() : currentState;
            state.page = route.intParam("page", 1);
            return state;
        }

        @Override
        public Class<BlockedState> stateType() {
            return BlockedState.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<BlockedState> context) {
            Session session = context.session();
            int requestedPage = Math.max(1, context.state().page);

            List<PlayerData> blockedPlayers = privateMessageService.listBlocked(session);
            var pagination = CustomGatherers.calculatePagination(blockedPlayers.size(), menu.globalConfig.privateMessagesPerPage);
            int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(requestedPage);
            context.state().page = validPage;
            int start = (validPage - 1) * menu.globalConfig.privateMessagesPerPage;
            int end = Math.min(start + menu.globalConfig.privateMessagesPerPage, blockedPlayers.size());
            List<PlayerData> pageItems = start >= blockedPlayers.size() ? List.of() : blockedPlayers.subList(start, end);

            List<List<MenuButton>> rows = new ArrayList<>();

            List<MenuButton> pagingRow = new ArrayList<>();
            if (validPage > 1) {
                pagingRow.add(MenuButton.of(session.locale().t("previous"), ACTION_PREVIOUS));
            }
            if (validPage < Math.max(1, pagination.totalPages())) {
                pagingRow.add(MenuButton.of(session.locale().t("next"), ACTION_NEXT));
            }
            if (!pagingRow.isEmpty()) {
                rows.add(pagingRow);
            }

            for (PlayerData playerData : pageItems) {
                rows.add(List.of(MenuButton.of(
                        session.locale().t("private-message-blocked-entry", args("target", playerData.nickname, "pid", playerData.pid)),
                        ACTION_UNBLOCK_PREFIX + playerData.pid
                )));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack()) {
                navigation.add(MenuButton.of(session.locale().t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(session.locale().t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    session.locale().t("private-message-blocked-title"),
                    session.locale().t(blockedPlayers.isEmpty() ? "private-message-blocked-empty" : "private-message-blocked-content", args(
                            "page", validPage,
                            "total", Math.max(1, pagination.totalPages()),
                            "count", blockedPlayers.size()
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<BlockedState> context, String actionId) {
            Session session = context.session();
            int currentPage = Math.max(1, context.state().page);

            switch (actionId) {
                case ACTION_PREVIOUS -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_BLOCKED).withParam("page", String.valueOf(currentPage - 1)));
                case ACTION_NEXT -> session.menuService.renderRoute(session,
                        MenuRoute.of(ROUTE_BLOCKED).withParam("page", String.valueOf(currentPage + 1)));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                    if (actionId.startsWith(ACTION_UNBLOCK_PREFIX)) {
                        int pid = Integer.parseInt(actionId.substring(ACTION_UNBLOCK_PREFIX.length()));
                        privateMessageService.unblock(session, pid);
                        context.render();
                    }
                }
            }
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
                List.of(List.of(
                        MenuButton.of(session.locale().t("back"), ACTION_BACK),
                        MenuButton.of(session.locale().t("close"), ACTION_CLOSE)
                ))
        );
    }

    private record DetailsView(PrivateMessage message, boolean markedRead, boolean blocked) {
    }
}
