package org.xcore.plugin.command;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.command.controller.client.HexedController;
import org.xcore.plugin.config.Config;

import java.util.List;

@Singleton
public class CloudCommandRegistrar {

    private final Provider<CloudService> cloud;
    private final Provider<Config> config;
    private final List<CloudClientController> clientControllers;
    private final List<CloudServerController> serverControllers;

    @Inject
    public CloudCommandRegistrar(Provider<CloudService> cloud,
                                 Provider<Config> config,
                                 List<CloudClientController> clientControllers,
                                 List<CloudServerController> serverControllers) {
        this.cloud = cloud;
        this.config = config;
        this.clientControllers = clientControllers;
        this.serverControllers = serverControllers;
    }

    @PostConstruct
    public void init() {
        var c = cloud.get();
        clientControllers.stream()
                .filter(this::shouldRegister)
                .forEach(c::registerClient);

        serverControllers.forEach(c::registerServer);
    }

    private boolean shouldRegister(CloudClientController controller) {
        return !(controller instanceof HexedController) || config.get().isMiniHexed();
    }
}
