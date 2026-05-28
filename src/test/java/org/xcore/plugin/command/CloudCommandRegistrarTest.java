package org.xcore.plugin.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.command.controller.client.EventController;
import org.xcore.plugin.command.controller.client.HexedController;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CloudCommandRegistrarTest {

    @Test
    @DisplayName("init registers hexed controller only on mini-hexed server")
    void init_registersHexedControllerOnlyOnMiniHexedServer() {
        // Arrange
        CloudService cloudService = mock(CloudService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-hexed";
        HexedController hexedController = mock(HexedController.class);
        CloudClientController genericController = mock(CloudClientController.class);
        CloudServerController serverController = mock(CloudServerController.class);

        CloudCommandRegistrar registrar = new CloudCommandRegistrar(
                cloudService,
                config,
                List.of(hexedController, genericController),
                List.of(serverController)
        );

        // Act
        registrar.init();

        // Assert
        verify(cloudService).registerClient(hexedController);
        verify(cloudService).registerClient(genericController);
        verify(cloudService).registerServer(serverController);
    }

    @Test
    @DisplayName("init skips hexed controller outside mini-hexed server")
    void init_skipsHexedControllerOutsideMiniHexedServer() {
        // Arrange
        CloudService cloudService = mock(CloudService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        HexedController hexedController = mock(HexedController.class);
        CloudClientController genericController = mock(CloudClientController.class);

        CloudCommandRegistrar registrar = new CloudCommandRegistrar(
                cloudService,
                config,
                List.of(hexedController, genericController),
                List.of()
        );

        // Act
        registrar.init();

        // Assert
        verify(cloudService, never()).registerClient(hexedController);
        verify(cloudService).registerClient(genericController);
    }

    @Test
    @DisplayName("init registers event controller only on event server")
    void init_registersEventControllerOnlyOnEventServer() {
        // Arrange
        CloudService cloudService = mock(CloudService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "event";
        EventController eventController = mock(EventController.class);
        CloudClientController genericController = mock(CloudClientController.class);

        CloudCommandRegistrar registrar = new CloudCommandRegistrar(
                cloudService,
                config,
                List.of(eventController, genericController),
                List.of()
        );

        // Act
        registrar.init();

        // Assert
        verify(cloudService).registerClient(eventController);
        verify(cloudService).registerClient(genericController);
    }

    @Test
    @DisplayName("init skips event controller outside event server")
    void init_skipsEventControllerOutsideEventServer() {
        // Arrange
        CloudService cloudService = mock(CloudService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        EventController eventController = mock(EventController.class);
        CloudClientController genericController = mock(CloudClientController.class);

        CloudCommandRegistrar registrar = new CloudCommandRegistrar(
                cloudService,
                config,
                List.of(eventController, genericController),
                List.of()
        );

        // Act
        registrar.init();

        // Assert
        verify(cloudService, never()).registerClient(eventController);
        verify(cloudService).registerClient(genericController);
    }
}
