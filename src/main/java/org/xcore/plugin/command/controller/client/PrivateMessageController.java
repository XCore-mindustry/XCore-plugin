package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.RequiresMuteCheck;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.MessageMenu;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PrivateMessageController implements CloudClientController {

    private final SessionService sessionService;
    private final PrivateMessageService privateMessageService;
    private final MessageMenu messageMenu;

    @Inject
    public PrivateMessageController(SessionService sessionService,
                                    PrivateMessageService privateMessageService,
                                    MessageMenu messageMenu) {
        this.sessionService = sessionService;
        this.privateMessageService = privateMessageService;
        this.messageMenu = messageMenu;
    }

    @RequiresMuteCheck
    @Command("msg <id> <message>")
    public void msg(XCoreSender sender,
                    @Argument("id") int id,
                    @Argument("message") @Greedy String message) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) {
            return;
        }

        privateMessageService.send(session, id, message);
    }

    @RequiresMuteCheck
    @Command("reply <message>")
    public void reply(XCoreSender sender, @Argument("message") @Greedy String message) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) {
            return;
        }

        privateMessageService.reply(session, message);
    }

    @Command("inbox")
    public void inbox(XCoreSender sender) {
        messageMenu.sender(sender);
        messageMenu.inbox(sender.player().uuid(), 1);
    }

    @Command("inbox unread")
    public void unread(XCoreSender sender) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) {
            return;
        }

        long unread = privateMessageService.countUnread(session.data.uuid);
        session.locale().send("private-message-unread-count", args("count", unread));
    }

    @Command("inbox blocked")
    public void blocked(XCoreSender sender) {
        messageMenu.sender(sender);
        messageMenu.blocked(sender.player().uuid(), 1);
    }

    @Command("inbox block <id>")
    public void block(XCoreSender sender, @Argument("id") int id) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) {
            return;
        }

        if (privateMessageService.block(session, id)) {
            messageMenu.sender(sender);
            messageMenu.blocked(sender.player().uuid(), 1);
        }
    }

    @Command("inbox unblock <id>")
    public void unblock(XCoreSender sender, @Argument("id") int id) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) {
            return;
        }

        if (privateMessageService.unblock(session, id)) {
            messageMenu.sender(sender);
            messageMenu.blocked(sender.player().uuid(), 1);
        }
    }
}
