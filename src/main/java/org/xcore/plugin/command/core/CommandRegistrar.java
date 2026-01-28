package org.xcore.plugin.command.core;

import arc.util.CommandHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.command.controller.client.*;
import org.xcore.plugin.command.controller.server.DataController;
import org.xcore.plugin.command.controller.server.MaintainController;
import org.xcore.plugin.command.controller.server.ServerInformationController;
import org.xcore.plugin.command.controller.server.ServerModerationController;
import org.xcore.plugin.command.core.interceptor.AdminInterceptor;
import org.xcore.plugin.command.core.interceptor.MuteInterceptor;
import org.xcore.plugin.command.core.interceptor.PlayTimeInterceptor;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.service.BundleService;

@Singleton
public class CommandRegistrar {
    private final BundleService bundleService;
    private final Config config;

    // Interceptors
    private final AdminInterceptor adminInterceptor;
    private final MuteInterceptor muteInterceptor;
    private final PlayTimeInterceptor playTimeInterceptor;

    // Client Controllers
    private final InformationController informationController;
    private final SocialController socialController;
    private final VoteController voteController;
    private final MapController mapController;
    private final StatsController statsController;
    private final AuthController authController;
    private final ModerationController moderationController;
    private final HexedController hexedController;

    // Server Controllers
    private final ServerInformationController serverInformationController;
    private final DataController dataController;
    private final ServerModerationController serverModerationController;
    private final MaintainController maintainController;

    @Inject
    public CommandRegistrar(
            BundleService bundleService,
            Config config,
            AdminInterceptor adminInterceptor,
            MuteInterceptor muteInterceptor,
            PlayTimeInterceptor playTimeInterceptor,
            // Client
            InformationController informationController,
            SocialController socialController,
            VoteController voteController,
            MapController mapController,
            StatsController statsController,
            AuthController authController,
            ModerationController moderationController,
            HexedController hexedController,
            // Server
            ServerInformationController serverInformationController,
            DataController dataController,
            ServerModerationController serverModerationController,
            MaintainController maintainController
    ) {
        this.bundleService = bundleService;
        this.config = config;
        this.adminInterceptor = adminInterceptor;
        this.muteInterceptor = muteInterceptor;
        this.playTimeInterceptor = playTimeInterceptor;

        this.informationController = informationController;
        this.socialController = socialController;
        this.voteController = voteController;
        this.mapController = mapController;
        this.statsController = statsController;
        this.authController = authController;
        this.moderationController = moderationController;
        this.hexedController = hexedController;

        this.serverInformationController = serverInformationController;
        this.dataController = dataController;
        this.serverModerationController = serverModerationController;
        this.maintainController = maintainController;
    }

    public void registerClient(CommandHandler handler) {
        CommandBus bus = new CommandBus(handler, bundleService);

        bus.addInterceptor(adminInterceptor);
        bus.addInterceptor(muteInterceptor);
        bus.addInterceptor(playTimeInterceptor);

        informationController.setHandler(handler);

        bus.register(
                informationController,
                socialController,
                voteController,
                mapController,
                statsController,
                authController,
                moderationController
        );

        if (config.isMiniHexed()) {
            bus.register(hexedController);
        }
    }

    public void registerServer(CommandHandler handler) {
        CommandBus bus = new CommandBus(handler, bundleService);

        bus.register(
                serverInformationController,
                dataController,
                serverModerationController,
                maintainController
        );
    }
}
