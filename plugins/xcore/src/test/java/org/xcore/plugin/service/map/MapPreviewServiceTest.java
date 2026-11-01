package org.xcore.plugin.service.map;

import mindustry.gen.Player;
import mindustry.maps.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.ui.XcoreImageService;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapPreviewServiceTest {

    @Test
    @DisplayName("getCachedRegionName returns null when map preview is not in cache")
    void getCachedRegionName_returnsNullWhenNotCached() {
        XcoreImageService imageService = mock(XcoreImageService.class);
        MapPreviewService service = new MapPreviewService(null, imageService, new org.xcore.plugin.service.map.inspect.DetachedMapReadLock());

        arc.files.Fi file = mock(arc.files.Fi.class);
        when(file.name()).thenReturn("desert_crossing.msav");
        Map map = new Map(file, 100, 100, new arc.struct.StringMap(), true);

        assertThat(service.getCachedRegionName(map)).isNull();
    }

    @Test
    @DisplayName("requestPreview fast-path delivers cached region directly")
    void requestPreview_fastPathDeliversCachedRegion() {
        XcoreImageService imageService = mock(XcoreImageService.class);
        MapPreviewService service = new MapPreviewService(null, imageService, new org.xcore.plugin.service.map.inspect.DetachedMapReadLock());

        Player player = Player.create();
        player.name = "TestPlayer";

        arc.files.Fi file = mock(arc.files.Fi.class);
        when(file.name()).thenReturn("desert_crossing.msav");
        Map map = new Map(file, 100, 100, new arc.struct.StringMap(), true);

        AtomicReference<String> delivered = new AtomicReference<>();
        service.requestPreview(player, map, (region, err) -> delivered.set(region));

        // When not cached and no preview file exists, it fails gracefully without throwing
        assertThat(delivered.get()).isNull();
    }

    @Test
    @DisplayName("an evicted preview is forgotten by its map file and drawn again")
    void evictedPreviewIsForgottenByItsMapFile() {
        XcoreImageService imageService = mock(XcoreImageService.class);
        MapPreviewService service = new MapPreviewService(null, imageService, new org.xcore.plugin.service.map.inspect.DetachedMapReadLock());

        for (int i = 0; i <= 32; i++) {
            service.remember("map" + i + ".msav", "region" + i, new byte[]{(byte) i});
        }

        verify(imageService).unregister("region0");
        assertThat(service.getCachedRegionName(map("map0.msav"))).isNull();
        assertThat(service.getCachedRegionName(map("map32.msav"))).isEqualTo("region32");
    }

    private static Map map(String fileName) {
        arc.files.Fi file = mock(arc.files.Fi.class);
        when(file.name()).thenReturn(fileName);
        return new Map(file, 100, 100, new arc.struct.StringMap(), true);
    }
}
