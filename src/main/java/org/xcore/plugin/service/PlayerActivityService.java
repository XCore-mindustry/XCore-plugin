package org.xcore.plugin.service;

import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerActivityService {

    private final SessionService sessionService;
    private final FindService findService;
    private final GlobalConfig globalConfig;
    private final ServerDiscoveryService discoveryService;

    @Inject
    public PlayerActivityService(SessionService sessionService,
                                 FindService findService,
                                 GlobalConfig globalConfig,
                                 ServerDiscoveryService discoveryService) {
        this.sessionService = sessionService;
        this.findService = findService;
        this.globalConfig = globalConfig;
        this.discoveryService = discoveryService;
    }

    @PostConstruct
    public void start() {
        Timer.schedule(() -> {
            discoveryService.updateFooter();

            for (Session session : sessionService.getAllCachedSnapshot()) {
                if (session == null || session.data == null) continue;
                var player = findService.playerByUuid(session.data.uuid);
                if (player == null) continue;

                PlayerData data = session.data;
                Localization local = session.locale();

                sessionService.incrementPlayTime(session, 1);

                if (data.totalPlayTime == globalConfig.minPlayTimeForVotekick) {
                    local.send("notification-votekick-playtime",
                            args("votekickPlayTime", globalConfig.minPlayTimeForVotekick));
                } else if (data.totalPlayTime == globalConfig.minPlayTimeForGlobalChat) {
                    local.send("notification-global-chat-playtime",
                            args("globalChatPlayTime", globalConfig.minPlayTimeForGlobalChat));
                }

            }
        }, 0, 60);
    }
}
