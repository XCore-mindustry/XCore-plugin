package org.xcore.plugin.command.core;

import arc.util.CommandHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.command.controller.client.HexedController;
import org.xcore.plugin.command.core.interceptor.AdminInterceptor;
import org.xcore.plugin.command.core.interceptor.MuteInterceptor;
import org.xcore.plugin.command.core.interceptor.PlayTimeInterceptor;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.service.BundleService;

import java.util.Comparator;
import java.util.List;


@Singleton
public class CommandRegistrar {
    private final BundleService bundleService;
    private final Config config;

    private final AdminInterceptor adminInterceptor;
    private final MuteInterceptor muteInterceptor;
    private final PlayTimeInterceptor playTimeInterceptor;

    private final List<ClientController> clientControllers;
    private final List<ServerController> serverControllers;

    @Inject
    public CommandRegistrar(
            BundleService bundleService,
            Config config,
            AdminInterceptor adminInterceptor,
            MuteInterceptor muteInterceptor,
            PlayTimeInterceptor playTimeInterceptor,
            List<ClientController> clientControllers,
            List<ServerController> serverControllers
    ) {
        this.bundleService = bundleService;
        this.config = config;
        this.adminInterceptor = adminInterceptor;
        this.muteInterceptor = muteInterceptor;
        this.playTimeInterceptor = playTimeInterceptor;
        this.clientControllers = clientControllers;
        this.serverControllers = serverControllers;
    }

    /**
     * Register client commands with automatic controller discovery.
     * Controllers are sorted by priority (higher first) and filtered based on configuration.
     * Filters out HexedController if mini-hexed mode is disabled.
     */
    public void registerClient(CommandHandler handler) {
        CommandBus bus = new CommandBus(handler, bundleService);

        bus.addInterceptor(adminInterceptor);
        bus.addInterceptor(muteInterceptor);
        bus.addInterceptor(playTimeInterceptor);

        clientControllers.stream()
                .sorted(Comparator.comparingInt(ClientController::priority).reversed())
                .filter(this::shouldRegisterController)
                .forEach(controller -> {
                    controller.setup(handler);
                    bus.register(controller);
                });
    }

    public void registerServer(CommandHandler handler) {
        CommandBus bus = new CommandBus(handler, bundleService);

        serverControllers.stream()
                .sorted(Comparator.comparingInt(ServerController::priority).reversed())
                .forEach(bus::register);
    }

    private boolean shouldRegisterController(ClientController controller) {
        return !(controller instanceof HexedController) || config.isMiniHexed();
    }
}
