package org.xcore.plugin.command.controller.client;

import jakarta.inject.Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.ui.menu.MapMenu;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MapControllerVoteNewWaveTest {

    @Test
    @DisplayName("vnw delegates to map service non-forced path")
    void vnwDelegatesToService() {
        MapService mapService = mock(MapService.class);
        MapController controller = new MapController(
                mock(MapDataRepository.class),
                mapService,
                provider()
        );

        XCoreSender sender = mock(XCoreSender.class);
        var player = mock(mindustry.gen.Player.class);
        org.mockito.Mockito.when(sender.player()).thenReturn(player);

        controller.vnw(sender);

        verify(mapService).startNewWaveSession(player, false);
    }

    @Test
    @DisplayName("avnw delegates to map service forced path")
    void avnwDelegatesToService() {
        MapService mapService = mock(MapService.class);
        MapController controller = new MapController(
                mock(MapDataRepository.class),
                mapService,
                provider()
        );

        XCoreSender sender = mock(XCoreSender.class);
        var player = mock(mindustry.gen.Player.class);
        org.mockito.Mockito.when(sender.player()).thenReturn(player);

        controller.avnw(sender);

        verify(mapService).startNewWaveSession(player, true);
    }

    @SuppressWarnings("unchecked")
    private static Provider<MapMenu> provider() {
        return mock(Provider.class);
    }
}
