package org.xcore.plugin.service.map;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.service.GameStateService;
import org.xcore.plugin.service.MapService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Verifies catalog rebuild triggers without touching MongoDB or real files. */
class MapCatalogTriggerTest {
    private static final List<List<MapIdentityCatalog.Source>> CAPTURED = new CopyOnWriteArrayList<>();
    private Object originalMaps;

    @BeforeEach
    void setUp() throws Exception {
        Events.clear();
        CAPTURED.clear();
        var mapsField = Vars.class.getField("maps");
        originalMaps = mapsField.get(null);
        var maps = mock(mindustry.maps.Maps.class);
        var map = new mindustry.maps.Map(new arc.files.Fi("arena.msav"), 10, 20,
                arc.struct.StringMap.of("name", "Arena", "author", "Author"), true);
        when(maps.customMaps()).thenReturn(arc.struct.Seq.with(map));
        mapsField.set(null, maps);
    }

    @AfterEach
    void tearDown() throws Exception {
        Events.clear();
        var mapsField = Vars.class.getField("maps");
        mapsField.set(null, originalMaps);
    }

    private MapService serviceWithTracingCatalog() {
        var catalog = mock(MapIdentityCatalog.class);
        doAnswer(call -> {
            CAPTURED.add(List.copyOf(call.getArgument(0)));
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        }).when(catalog).rebuild(any());
        var service = new MapService(
                mock(org.xcore.plugin.database.repository.EventDataRepository.class),
                mock(org.xcore.plugin.database.repository.MapDataRepository.class),
                mock(org.xcore.plugin.session.SessionService.class),
                new org.xcore.plugin.config.TomlXcoreConfig(),
                new org.xcore.plugin.config.TomlSecretsConfig(),
                mock(org.xcore.plugin.vote.VoteService.class),
                mock(org.xcore.plugin.vote.VoteNewWaveFactory.class),
                mock(org.xcore.plugin.vote.VoteRtvFactory.class),
                new GameStateService()
        );
        service.attachIdentityCatalog(catalog);
        service.registerCatalogTriggers();
        return service;
    }

    @Test
    void serverLoadFiresCatalogRebuild() {
        serviceWithTracingCatalog();
        Events.fire(new EventType.ServerLoadEvent());
        assertThat(CAPTURED).hasSize(1);
        assertThat(CAPTURED.getFirst()).extracting(MapIdentityCatalog.Source::fileName).containsExactly("arena.msav");
    }

    @Test
    void clientUploadReloadFiresCatalogRebuild() {
        var transport = new org.xcore.plugin.event.transport.MapTransportHandler(
                mock(org.xcore.plugin.service.NetworkService.class),
                new org.xcore.plugin.config.TomlXcoreConfig(),
                serviceWithTracingCatalog(),
                mock(org.xcore.plugin.database.repository.MapDataRepository.class)
        );
        transport.registerListeners();
        Events.fire(new EventType.ServerLoadEvent());
        assertThat(CAPTURED).hasSize(1);
        CAPTURED.clear();
        transport.onMapsReloaded();
        assertThat(CAPTURED).hasSize(1);
        assertThat(CAPTURED.getFirst()).extracting(MapIdentityCatalog.Source::fileName).containsExactly("arena.msav");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsRemoveRequestViaNetworkTriggersCatalogRebuild() {
        var network = mock(org.xcore.plugin.service.NetworkService.class);
        var removeListeners = new java.util.ArrayList<arc.func.Cons<org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1>>();
        when(network.subscribe(eq(org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1.class), any())).thenAnswer(call -> {
            removeListeners.add(call.getArgument(1));
            return null;
        });

        var config = new org.xcore.plugin.config.TomlXcoreConfig();
        config.server.name = "test-server";
        var mapService = serviceWithTracingCatalog();
        var transport = new org.xcore.plugin.event.transport.MapTransportHandler(
                network,
                config,
                mapService,
                mock(org.xcore.plugin.database.repository.MapDataRepository.class)
        );
        transport.registerListeners();
        CAPTURED.clear();

        // Simulate network message arriving to remove arena.msav
        removeListeners.getFirst().get(new org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1("test-server", "arena.msav"));

        assertThat(CAPTURED).hasSize(1);
    }
}
