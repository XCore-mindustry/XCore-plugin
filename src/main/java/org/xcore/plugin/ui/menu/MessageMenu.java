package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.types.ObjectId;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;

@Singleton
public class MessageMenu extends Menu {

    private final PrivateMessageService privateMessageService;
    private final MenuService menuService;

    @Inject
    public MessageMenu(Config config,
                       GlobalConfig globalConfig,
                       SessionService sessionService,
                       PrivateMessageService privateMessageService,
                       MenuService menuService) {
        super(config, globalConfig, sessionService);
        this.privateMessageService = privateMessageService;
        this.menuService = menuService;
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new MessageFlows.InboxFlow(this, privateMessageService));
        menuService.registerRoute(new MessageFlows.BlockedFlow(this, privateMessageService));
        menuService.registerRoute(new MessageFlows.DetailsFlow(this, privateMessageService));
    }

    public void inbox(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(MessageFlows.ROUTE_INBOX)
                .withParam("page", String.valueOf(page)));
    }

    public void blocked(String uuid, int page) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(MessageFlows.ROUTE_BLOCKED)
                .withParam("page", String.valueOf(page)));
    }

    public void details(String uuid, ObjectId messageId, int returnPage) {
        Session session = sessionService.get(uuid);
        if (session == null || session.data == null) return;
        session.clear();

        session.menuService.renderRoute(session, MenuRoute.of(MessageFlows.ROUTE_DETAILS)
                .withParam("messageId", messageId.toHexString())
                .withParam("returnPage", String.valueOf(returnPage)));
    }
}
