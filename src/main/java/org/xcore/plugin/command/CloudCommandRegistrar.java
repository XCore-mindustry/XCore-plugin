package org.xcore.plugin.command;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.command.controller.client.EventController;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.List;

@Singleton
public class CloudCommandRegistrar {

    private final CloudService cloud;
    private final TomlXcoreConfig config;
    private final List<CloudClientController> clientControllers;
    private final List<CloudServerController> serverControllers;

    @Inject
    public CloudCommandRegistrar(CloudService cloud,
                                 TomlXcoreConfig config,
                                 List<CloudClientController> clientControllers,
                                 List<CloudServerController> serverControllers) {
        this.cloud = cloud;
        this.config = config;
        this.clientControllers = clientControllers;
        this.serverControllers = serverControllers;
    }

    @PostConstruct
    public void init() {
        clientControllers.stream()
                .filter(this::shouldRegister)
                .forEach(cloud::registerClient);

        serverControllers.forEach(cloud::registerServer);
    }

    private boolean shouldRegister(CloudClientController controller) {
        if (controller instanceof EventController) {
            return isEventServer();
        }

        return true;
    }

    private boolean isEventServer() {
        return "event".equals(config.server.name);
    }
}
