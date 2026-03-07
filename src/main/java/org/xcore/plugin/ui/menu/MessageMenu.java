package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.bson.types.ObjectId;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class MessageMenu extends Menu {

    private final PrivateMessageService privateMessageService;

    @Inject
    public MessageMenu(Config config,
                       GlobalConfig globalConfig,
                       SessionService sessionService,
                       PrivateMessageService privateMessageService) {
        super(config, globalConfig, sessionService);
        this.privateMessageService = privateMessageService;
    }

    public void inbox(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        long total = privateMessageService.countInbox(session.data.uuid);
        var pagination = CustomGatherers.calculatePagination(total, globalConfig.privateMessagesPerPage);
        int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(page);
        List<PrivateMessage> messages = privateMessageService.inbox(session.data.uuid, validPage);

        var builder = session.builder()
                .title("private-message-menu-title")
                .content(total == 0
                                ? "private-message-menu-empty"
                                : "private-message-menu-content",
                        args(
                                "page", validPage,
                                "total", Math.max(1, pagination.totalPages()),
                                "unread", privateMessageService.countUnread(session.data.uuid)
                        ))
                .start()
                .ifAddLocal(validPage > 1, "previous", () -> inbox(uuid, validPage - 1))
                .ifAddLocal(validPage < Math.max(1, pagination.totalPages()), "next", () -> inbox(uuid, validPage + 1))
                .end();

        if (!messages.isEmpty()) {
            builder.addForEach(messages, (b, message) -> {
                String text = session.locale().t(
                        message.readAt > 0 ? "private-message-menu-entry-read" : "private-message-menu-entry-unread",
                        args(
                                "author", message.fromName,
                                "pid", message.fromPid,
                                "message", preview(message.message),
                                "time", formatTime(message.createdModelTime, session)
                        )
                );

                b.addRow(text, () -> {
                    session.pushHistory(() -> inbox(uuid, validPage));
                    details(uuid, message.id, validPage);
                });
            });
        }

        builder.start()
                .addLocal("private-message-compose", () -> promptCompose(uuid, () -> inbox(uuid, validPage)))
                .addLocal("private-message-blocked", () -> {
                    session.pushHistory(() -> inbox(uuid, validPage));
                    blocked(uuid, 1);
                })
                .end()
                .addNavigationRow()
                .show();
    }

    public void details(String uuid, ObjectId messageId, int returnPage) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        PrivateMessage message = privateMessageService.getMessage(messageId, session.data.uuid);
        if (message == null) {
            session.locale().send("error-private-message-not-found", args());
            Runnable previous = session.popHistory();
            if (previous != null) {
                previous.run();
            }
            return;
        }

        boolean markedRead = privateMessageService.markRead(messageId, session.data.uuid);
        boolean blocked = session.data.blockedPrivateUuids != null && session.data.blockedPrivateUuids.contains(message.fromUuid);

        session.builder()
                .title("private-message-details-title")
                .content("private-message-details-content", args(
                        "author", message.fromName,
                        "pid", message.fromPid,
                        "time", formatTime(message.createdModelTime, session),
                        "message", message.message,
                        "status", session.locale().t(markedRead || message.readAt > 0 ? "private-message-status-read" : "private-message-status-unread")
                ))
                .start()
                .addLocal("reply", () -> promptReply(uuid, message, returnPage))
                .addLocal("delete", () -> {
                    privateMessageService.softDelete(messageId, session.data.uuid);
                    inbox(uuid, returnPage);
                })
                .end()
                .addLocalRow(blocked ? "private-message-unblock" : "private-message-block", () -> {
                    if (blocked) {
                        privateMessageService.unblock(session, message.fromPid);
                    } else {
                        privateMessageService.block(session, message.fromPid);
                    }
                    details(uuid, messageId, returnPage);
                })
                .addNavigationRow()
                .show();
    }

    public void blocked(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        List<PlayerData> blockedPlayers = privateMessageService.listBlocked(session);
        var pagination = CustomGatherers.calculatePagination(blockedPlayers.size(), globalConfig.privateMessagesPerPage);
        int validPage = pagination.totalPages() <= 0 ? 1 : pagination.clampPage(page);
        int start = (validPage - 1) * globalConfig.privateMessagesPerPage;
        int end = Math.min(start + globalConfig.privateMessagesPerPage, blockedPlayers.size());
        List<PlayerData> pageItems = start >= blockedPlayers.size() ? List.of() : blockedPlayers.subList(start, end);

        var builder = session.builder()
                .title("private-message-blocked-title")
                .content(blockedPlayers.isEmpty()
                                ? "private-message-blocked-empty"
                                : "private-message-blocked-content",
                        args(
                                "page", validPage,
                                "total", Math.max(1, pagination.totalPages()),
                                "count", blockedPlayers.size()
                        ))
                .start()
                .ifAddLocal(validPage > 1, "previous", () -> blocked(uuid, validPage - 1))
                .ifAddLocal(validPage < Math.max(1, pagination.totalPages()), "next", () -> blocked(uuid, validPage + 1))
                .end();

        builder.addForEach(pageItems, (b, playerData) -> b.addRow(
                session.locale().t("private-message-blocked-entry", args("target", playerData.nickname, "pid", playerData.pid)),
                () -> {
                    privateMessageService.unblock(session, playerData.pid);
                    blocked(uuid, validPage);
                }
        ));

        builder.addNavigationRow().show();
    }

    private void promptReply(String uuid, PrivateMessage message, int returnPage) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;

        session.setTextHandler(text -> {
            privateMessageService.send(session, message.fromPid, text);
            details(uuid, message.id, returnPage);
        });

        Call.textInput(session.player.con,
                session.menuService.getTextId(),
                session.locale().t("private-message-reply-title"),
                session.locale().t("private-message-reply-message", args("pid", message.fromPid)),
                globalConfig.privateMessageMaxLength,
                "",
                false);
    }

    private void promptCompose(String uuid, Runnable onBack) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;

        session.setTextHandler(pidText -> {
            Integer targetPid = privateMessageService.parseMenuPid(pidText);
            if (targetPid == null) {
                session.locale().send("error-private-message-invalid-pid", args());
                onBack.run();
                return;
            }

            session.setTextHandler(message -> {
                privateMessageService.send(session, targetPid, message);
                onBack.run();
            });

            Call.textInput(session.player.con,
                    session.menuService.getTextId(),
                    session.locale().t("private-message-compose-body-title"),
                    session.locale().t("private-message-compose-body-message", args("pid", "#" + targetPid)),
                    globalConfig.privateMessageMaxLength,
                    "",
                    false);
        });

        Call.textInput(session.player.con,
                session.menuService.getTextId(),
                session.locale().t("private-message-compose-target-title"),
                session.locale().t("private-message-compose-target-message"),
                32,
                session.lastPrivateTargetPid == null ? "" : "#" + session.lastPrivateTargetPid,
                false);
    }

    private String preview(String message) {
        if (message == null) {
            return "";
        }

        return message.length() <= 40 ? message : message.substring(0, 37) + "...";
    }
}
