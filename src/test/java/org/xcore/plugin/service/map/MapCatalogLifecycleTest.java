package org.xcore.plugin.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.map.MapIdentityCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class MapCatalogLifecycleTest {
    private mindustry.core.GameState originalState;
    private Object originalMaps;

    @BeforeEach
    void setUp() throws Exception {
        var mapsField = mindustry.Vars.class.getField("maps");
        originalMaps = mapsField.get(null);
        var maps = org.mockito.Mockito.mock(mindustry.maps.Maps.class);
        var map = new mindustry.maps.Map(new arc.files.Fi("arena.msav"), 10, 20,
                arc.struct.StringMap.of("name", "Arena", "author", "Author"), true);
        org.mockito.Mockito.when(maps.customMaps()).thenReturn(arc.struct.Seq.with(map));
        mapsField.set(null, maps);
    }

    @AfterEach
    void tearDown() throws Exception {
        var mapsField = mindustry.Vars.class.getField("maps");
        mapsField.set(null, originalMaps);
    }

    @Test
    void serverLoadAndMapReloadCaptureMetadataOnMainThread() {
        var captured = new java.util.ArrayDeque<List<MapIdentityCatalog.Source>>();
        var catalog = org.mockito.Mockito.mock(MapIdentityCatalog.class);
        org.mockito.Mockito.doAnswer(call -> {
            captured.add(call.getArgument(0));
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }).when(catalog).rebuild(any());
        var service = new MapService(
                mock(org.xcore.plugin.database.repository.EventDataRepository.class),
                mock(org.xcore.plugin.database.repository.MapDataRepository.class),
                mock(org.xcore.plugin.session.SessionService.class),
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                mock(org.xcore.plugin.vote.VoteService.class),
                mock(org.xcore.plugin.vote.VoteNewWaveFactory.class),
                mock(org.xcore.plugin.vote.VoteRtvFactory.class),
                mock(GameStateService.class)
        );
        service.attachIdentityCatalog(catalog);

        service.rebuildIdentityCatalog();

        assertThat(captured).hasSize(1);
        var sources = captured.getFirst();
        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().fileName()).isEqualTo("arena.msav");
        assertThat(sources.getFirst().name()).isEqualTo("Arena");
        assertThat(sources.getFirst().open()).isNotNull();
    }

    @Test
    void postConstructRegistersCatalogTriggers() {
        arc.Events.clear();
        var captured = new java.util.ArrayDeque<List<MapIdentityCatalog.Source>>();
        var catalog = org.mockito.Mockito.mock(MapIdentityCatalog.class);
        org.mockito.Mockito.doAnswer(call -> {
            captured.add(call.getArgument(0));
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }).when(catalog).rebuild(any());

        var service = new MapService(
                mock(org.xcore.plugin.database.repository.EventDataRepository.class),
                mock(org.xcore.plugin.database.repository.MapDataRepository.class),
                mock(org.xcore.plugin.session.SessionService.class),
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                mock(org.xcore.plugin.vote.VoteService.class),
                mock(org.xcore.plugin.vote.VoteNewWaveFactory.class),
                mock(org.xcore.plugin.vote.VoteRtvFactory.class),
                mock(GameStateService.class),
                catalog
        );
        service.init();

        arc.Events.fire(new mindustry.game.EventType.ServerLoadEvent());

        assertThat(captured).hasSize(1);
    }
}
