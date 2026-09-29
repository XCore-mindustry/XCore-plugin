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
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.concurrent.StorageExecutor;
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


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MapTransportHandlerTest {

    private Maps originalMaps;
    private mindustry.core.GameState originalState;

    /**
     * Runs the marshalled game-thread work inline so the existing behavioural assertions
     * stay synchronous. The marshalling itself is asserted separately, below.
     */
    private Async async;

    @BeforeEach
    void setUp() {
        originalMaps = Vars.maps;
        originalState = Vars.state;
        async = new Async(new StorageExecutor(4), Runnable::run);
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

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository, async);

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

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository,
                // The list path reads the engine on the game thread, queries Mongo on the
                // storage executor, then builds the response back on the game thread. Running
                // the executor inline is the only way to observe the end of that chain from
                // a test without sleeping.
                new Async(InlineStorageExecutor.create(), Runnable::run));

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, MapsListRequestV1.class)
                .get(new MapsListRequestV1("mini-pvp"));

        verify(network).respond(any(), any());
    }

    @Test
    @DisplayName("a failed map lookup still answers the peer")
    void mapsListRequest_respondsEvenWhenTheRepositoryFails() {
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        MapService mapService = mock(MapService.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);
        // thenMain completes exceptionally when the storage stage throws, and nothing
        // observes that future. Swallowing it would leave the peer waiting out its timeout
        // with no answer and nothing in the log.
        when(mapDataRepository.find(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("mongodb is unreachable"));

        Maps maps = mock(Maps.class);
        Vars.maps = maps;
        // A real map, not an empty list: with no maps the repository is never consulted, so
        // the test would pass whether or not the failure path works. Built into a local
        // first, because realMap stubs a mock and nesting that inside an unfinished when()
        // corrupts the stubbing.
        mindustry.maps.Map alpha = realMap("Alpha");
        when(maps.customMaps()).thenReturn(Seq.with(alpha));

        Vars.state = new mindustry.core.GameState();
        Vars.state.rules = mock(Rules.class);
        when(Vars.state.rules.mode()).thenReturn(Gamemode.pvp);

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository,
                new Async(InlineStorageExecutor.create(), Runnable::run));

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);
        handler.registerListeners();

        listener(listeners, MapsListRequestV1.class).get(new MapsListRequestV1("mini-pvp"));

        verify(mapDataRepository).find(anyString(), anyString(), anyString());
        verify(network).respond(any(), any());
    }

    @Test
    @DisplayName("one unreachable map does not cost the peer the rest of the list")
    void mapsListRequest_isolatesAFailingLookupPerMap() {
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        MapService mapService = mock(MapService.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);
        when(mapDataRepository.find(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("mongodb is unreachable"));

        mindustry.maps.Map first = realMap("Alpha");
        mindustry.maps.Map second = realMap("Beta");
        Maps maps = mock(Maps.class);
        Vars.maps = maps;
        Seq<mindustry.maps.Map> bothMaps = Seq.with(first, second);
        when(maps.customMaps()).thenReturn(bothMaps);

        Vars.state = new mindustry.core.GameState();
        Vars.state.rules = mock(Rules.class);
        when(Vars.state.rules.mode()).thenReturn(Gamemode.pvp);

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository,
                new Async(InlineStorageExecutor.create(), Runnable::run));

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);
        handler.registerListeners();

        listener(listeners, MapsListRequestV1.class).get(new MapsListRequestV1("mini-pvp"));

        ArgumentCaptor<Object> response = ArgumentCaptor.forClass(Object.class);
        verify(network).respond(any(), response.capture());
        // Both maps still appear, without metadata. A map with no persisted record already
        // serialises this way, so a failed lookup is the same answer a missing one gives.
        assertThat(response.getValue().toString()).contains("Alpha", "Beta");
    }

    /**
     * A real Map, not a mock: {@code file} is a final field, so Mockito cannot populate it
     * and the mapper would dereference null while reading the file name.
     */
    private static mindustry.maps.Map realMap(String name) {
        StringMap tags = new StringMap();
        tags.put("name", name);
        tags.put("author", "somebody");
        tags.put("description", "a test map");

        Fi file = mock(Fi.class);
        when(file.name()).thenReturn(name.toLowerCase(java.util.Locale.ROOT) + ".msav");
        // java.util.Map is imported in this file, so the constructor needs qualifying too.
        return new mindustry.maps.Map(file, 10, 10, tags, true);
    }

    @Test
    @DisplayName("maps remove request is ignored for other servers")
    void mapsRemoveRequest_isIgnoredForOtherServers() {
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        MapService mapService = mock(MapService.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository, async);

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

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository, async);

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
    @DisplayName("map registry mutations are deferred off the subscriber thread until the game thread runs them")
    void mapRegistryMutations_areMarshalledToMainThread() {
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

        // Stands in for a game thread that has not come back around yet.
        java.util.List<Runnable> marshalled = new java.util.ArrayList<>();
        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository,
                new Async(new StorageExecutor(4), marshalled::add));

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, MapsRemoveRequestV1.class)
                .get(new MapsRemoveRequestV1("mini-pvp", "test.msav"));

        // maps.reload() rebuilds the engine's map index; applying it from the Redis
        // subscriber thread races the tick loop reading the same registry.
        verify(maps, never()).removeMap(any());
        verify(maps, never()).reload();
        verify(mapService, never()).findMapByFileName(any());
        assertThat(marshalled).hasSize(1);

        marshalled.forEach(Runnable::run);

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

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository, async);

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

    private static void captureListeners(NetworkService network, Map<Class<?>, Cons<?>> listeners) {        doAnswer(invocation -> {
            listeners.put(invocation.getArgument(0), invocation.getArgument(1));
            return mock(RedisNetworkBackend.Subscription.class);
        }).when(network).subscribe(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static <T> Cons<T> listener(Map<Class<?>, Cons<?>> listeners, Class<T> type) {
        return (Cons<T>) listeners.get(type);
    }
}
