package org.xcore.plugin.event.transport;

import arc.func.Cons;
import arc.files.Fi;
import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.game.Rules;
import mindustry.maps.Maps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.RedisNetworkBackend;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsLoadCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1;
import org.xcore.protocol.generated.shared.MapFileSourceV1;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MapTransportHandlerTest {

    private Maps originalMaps;
    private mindustry.core.GameState originalState;

    @BeforeEach
    void setUp() {
        originalMaps = Vars.maps;
        originalState = Vars.state;
    }

    @AfterEach
    void tearDown() {
        Vars.maps = originalMaps;
        Vars.state = originalState;
    }

    @Test
    @DisplayName("maps list request is ignored for other servers")
    void mapsListRequest_isIgnoredForOtherServers() {
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        MapService mapService = mock(MapService.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, MapsListRequestV1.class)
                .get(new MapsListRequestV1("other-server"));

        verifyNoInteractions(mapService);
        verifyNoInteractions(mapDataRepository);
        verify(network, never()).respond(any(), any());
    }

    @Test
    @DisplayName("maps list request is handled for same server")
    void mapsListRequest_isHandledForSameServer() {
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        MapService mapService = mock(MapService.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);

        Maps maps = mock(Maps.class);
        Vars.maps = maps;
        when(maps.customMaps()).thenReturn(Seq.with());

        Vars.state = new mindustry.core.GameState();
        Vars.state.rules = mock(Rules.class);
        when(Vars.state.rules.mode()).thenReturn(Gamemode.pvp);

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, MapsListRequestV1.class)
                .get(new MapsListRequestV1("mini-pvp"));

        verify(network).respond(any(), any());
    }

    @Test
    @DisplayName("maps remove request is ignored for other servers")
    void mapsRemoveRequest_isIgnoredForOtherServers() {
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        MapService mapService = mock(MapService.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, MapsRemoveRequestV1.class)
                .get(new MapsRemoveRequestV1("other-server", "test.msav"));

        verifyNoInteractions(mapService);
        verify(network, never()).respond(any(), any());
    }

    @Test
    @DisplayName("maps remove request is handled for same server")
    void mapsRemoveRequest_isHandledForSameServer() {
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        MapService mapService = mock(MapService.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);

        mindustry.maps.Map map = new mindustry.maps.Map(
                new Fi("test.msav"),
                100,
                100,
                StringMap.of("name", "Test", "author", "author"),
                true
        );
        when(mapService.findMapByFileName("test.msav")).thenReturn(map);

        Maps maps = mock(Maps.class);
        Vars.maps = maps;

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, MapsRemoveRequestV1.class)
                .get(new MapsRemoveRequestV1("mini-pvp", "test.msav"));

        verify(maps).removeMap(map);
        verify(maps).reload();
        verify(network).respond(any(), any());
    }

    @Test
    @DisplayName("maps load command is ignored for other servers")
    void mapsLoadCommand_isIgnoredForOtherServers() {
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        MapService mapService = mock(MapService.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, MapsLoadCommandV1.class)
                .get(new MapsLoadCommandV1("other-server", List.of(
                        new MapFileSourceV1("https://example/maps/a.msav", "a.msav")
                )));

        verifyNoInteractions(mapService);
        verifyNoInteractions(mapDataRepository);
    }

    private static void captureListeners(NetworkService network, Map<Class<?>, Cons<?>> listeners) {
        doAnswer(invocation -> {
            listeners.put(invocation.getArgument(0), invocation.getArgument(1));
            return mock(RedisNetworkBackend.Subscription.class);
        }).when(network).subscribe(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static <T> Cons<T> listener(Map<Class<?>, Cons<?>> listeners, Class<T> type) {
        return (Cons<T>) listeners.get(type);
    }
}
