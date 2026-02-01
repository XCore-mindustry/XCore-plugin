package org.xcore.plugin.service;

import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerActivityService {

    private final PlayerSessionService playerSessionService;
    private final PlayerDataRepository playerDataRepository;
    private final BundleService bundleService;
    private final FindService findService;
    private final GlobalConfig globalConfig;
    private final ServerDiscoveryService discoveryService;

    @Inject
    public PlayerActivityService(PlayerSessionService playerSessionService,
                                 PlayerDataRepository playerDataRepository,
                                 BundleService bundleService,
                                 FindService findService,
                                 GlobalConfig globalConfig,
                                 ServerDiscoveryService discoveryService) {
        this.playerSessionService = playerSessionService;
        this.playerDataRepository = playerDataRepository;
        this.bundleService = bundleService;
        this.findService = findService;
        this.globalConfig = globalConfig;
        this.discoveryService = discoveryService;
    }

    @PostConstruct
    public void start() {
        Timer.schedule(() -> {
            discoveryService.updateFooter();

            for (var data : playerSessionService.getAllCached()) {
                var player = findService.playerByUuid(data.uuid);
                if (player == null) continue;

                data.totalPlayTime++;

                if (data.totalPlayTime == globalConfig.minPlayTimeForVotekick) {
                    bundleService.send(player, "notification-votekick-playtime",
                            args("votekickPlayTime", globalConfig.minPlayTimeForVotekick));
                } else if (data.totalPlayTime == globalConfig.minPlayTimeForGlobalChat) {
                    bundleService.send(player, "notification-global-chat-playtime",
                            args("globalChatPlayTime", globalConfig.minPlayTimeForGlobalChat));
                }

                playerDataRepository.save(data);
            }
        }, 0, 60);
    }
}
