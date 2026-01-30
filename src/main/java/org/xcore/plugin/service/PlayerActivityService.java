package org.xcore.plugin.service;

import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.DatabaseService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerActivityService {

    private final DatabaseService database;
    private final BundleService bundleService;
    private final FindService findService;
    private final GlobalConfig globalConfig;
    private final ServerDiscoveryService discoveryService;

    @Inject
    public PlayerActivityService(DatabaseService database, BundleService bundleService,
                                 FindService findService, GlobalConfig globalConfig,
                                 ServerDiscoveryService discoveryService) {
        this.database = database;
        this.bundleService = bundleService;
        this.findService = findService;
        this.globalConfig = globalConfig;
        this.discoveryService = discoveryService;
    }

    @PostConstruct
    public void start() {
        Timer.schedule(() -> {
            discoveryService.updateFooter();

            database.cachedPlayerData.each((uuid, data) -> {
                var player = findService.playerByUuid(uuid);
                if (player == null) return;

                data.totalPlayTime++;

                if (data.totalPlayTime == globalConfig.minPlayTimeForVotekick) {
                    bundleService.send(player, "notification-votekick-playtime",
                            args("votekickPlayTime", globalConfig.minPlayTimeForVotekick));
                } else if (data.totalPlayTime == globalConfig.minPlayTimeForGlobalChat) {
                    bundleService.send(player, "notification-global-chat-playtime",
                            args("globalChatPlayTime", globalConfig.minPlayTimeForGlobalChat));
                }

                database.getPlayerDataRepository().save(data);
            });
        }, 0, 60);
    }
}
