package org.xcore.plugin.service.map;

import arc.files.Fi;
import arc.struct.StringMap;
import mindustry.maps.Map;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.MapUploadRepository;
import org.xcore.plugin.model.MapDiscordUpload;
import org.xcore.plugin.service.MapService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class MapAttributionCacheTest {
    @Test
    void obsoleteReadCannotRestoreAttributionAfterReloadAndUiLookupIsInMemory() {
        var catalog = mock(MapIdentityCatalog.class);
        var repository = mock(MapUploadRepository.class);
        var before = new CompletableFuture<java.util.Map<String, MapDiscordUpload>>();
        var after = new CompletableFuture<java.util.Map<String, MapDiscordUpload>>();
        when(catalog.snapshot()).thenReturn(new MapIdentityCatalog.Snapshot(1, List.of(), java.util.Map.of()));
        when(repository.snapshotAsync(anyList())).thenReturn(before, after);
        var service = new MapService(null, null, null, new TomlXcoreConfig(), new TomlSecretsConfig(), null, null, null, null, catalog, repository);
        var map = new Map(new Fi("arena.msav"), 10, 10, StringMap.of("name", "Arena", "author", "Author"), true);
        var oldUpload = new MapDiscordUpload("123", "Old", 1, null, null);
        var currentUpload = new MapDiscordUpload("456", "Current", 2, null, null);
        service.refreshAttribution();
        service.clearAttribution();
        assertThat(service.discordUploadFor(map)).isNull();
        when(catalog.snapshot()).thenReturn(new MapIdentityCatalog.Snapshot(2, List.of(), java.util.Map.of()));
        service.refreshAttribution();
        after.complete(java.util.Map.of("arena.msav", currentUpload));
        before.complete(java.util.Map.of("arena.msav", oldUpload));
        clearInvocations(repository);
        assertThat(service.discordUploadFor(map)).isSameAs(currentUpload);
        assertThat(service.discordUploadFor(new Map(new Fi("Arena.msav"), 10, 10, StringMap.of("name", "Arena"), true))).isNull();
        verifyNoInteractions(repository);
    }
    @Test
    void refreshStartedDuringHashingCannotUseTheOldSnapshot() {
        var previousMaps = mindustry.Vars.maps;
        try {
            var map = new Map(new Fi("arena.msav"), 10, 10, StringMap.of("name", "Arena", "author", "Author"), true);
            mindustry.Vars.maps = mock(mindustry.maps.Maps.class);
            when(mindustry.Vars.maps.customMaps()).thenReturn(arc.struct.Seq.with(map));
            var catalog = mock(MapIdentityCatalog.class);
            var repository = mock(MapUploadRepository.class);
            var rebuild = new CompletableFuture<Boolean>();
            when(catalog.rebuild(anyList())).thenReturn(rebuild);
            when(catalog.snapshot()).thenReturn(new MapIdentityCatalog.Snapshot(1, List.of(), java.util.Map.of()));
            var upload = new MapDiscordUpload("123", "Old", 1, null, null);
            when(repository.snapshotAsync(anyList())).thenReturn(CompletableFuture.completedFuture(java.util.Map.of("arena.msav", upload)));
            var service = new MapService(null, null, null, new TomlXcoreConfig(), new TomlSecretsConfig(), null, null, null, null, catalog, repository);
            service.refreshAttribution();
            clearInvocations(repository);
            var ready = service.rebuildIdentityCatalogAsync();
            service.refreshAttribution();
            assertThat(service.discordUploadFor(map)).isNull();
            verifyNoInteractions(repository);
            when(catalog.snapshot()).thenReturn(new MapIdentityCatalog.Snapshot(2, List.of(), java.util.Map.of()));
            when(repository.snapshotAsync(anyList())).thenReturn(CompletableFuture.completedFuture(java.util.Map.of()));
            rebuild.complete(true);
            assertThat(ready.toCompletableFuture()).isCompleted();
            assertThat(service.discordUploadFor(map)).isNull();
            verify(repository).snapshotAsync(anyList());
        } finally { mindustry.Vars.maps = previousMaps; }
    }

}
