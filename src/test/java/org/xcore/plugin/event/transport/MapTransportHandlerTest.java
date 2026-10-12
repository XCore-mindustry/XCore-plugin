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

    private final org.xcore.plugin.database.repository.MapUploadRepository uploadRepository = mock(org.xcore.plugin.database.repository.MapUploadRepository.class);

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

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository, async, uploadRepository);

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
                new Async(InlineStorageExecutor.create(), Runnable::run), uploadRepository);

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
        when(mapService.discordUploadFor(alpha)).thenReturn(new org.xcore.plugin.model.MapDiscordUpload("123", "Reviewer", 1, null, null));

        Vars.state = new mindustry.core.GameState();
        Vars.state.rules = mock(Rules.class);
        when(Vars.state.rules.mode()).thenReturn(Gamemode.pvp);

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository,
                new Async(InlineStorageExecutor.create(), Runnable::run), uploadRepository);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);
        handler.registerListeners();

        listener(listeners, MapsListRequestV1.class).get(new MapsListRequestV1("mini-pvp"));

        verify(mapDataRepository).find(anyString(), anyString(), anyString());
        var response = ArgumentCaptor.forClass(org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1.class);
        verify(network).respond(any(), response.capture());
        assertThat(response.getValue().maps().getFirst().discordUpload().uploadedBy().discordId()).isEqualTo("123");
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
                new Async(InlineStorageExecutor.create(), Runnable::run), uploadRepository);

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

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository, async, uploadRepository);

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

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository, async, uploadRepository);

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
                new Async(InlineStorageExecutor.create(), marshalled::add), uploadRepository);

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

        while (!marshalled.isEmpty()) marshalled.removeFirst().run();

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

        MapTransportHandler handler = new MapTransportHandler(network, config, mapService, mapDataRepository, async, uploadRepository);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, MapsLoadCommandV1.class)
                .get(new MapsLoadCommandV1("other-server", List.of(
                        new MapFileSourceV1("https://example/maps/a.msav", "a.msav", null)
                )));

        verifyNoInteractions(mapService);
        verifyNoInteractions(mapDataRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyDownloadedMapsGetAttributedAfterTheRegistryReload(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var originalDirectory = Vars.customMapDirectory;
        Vars.customMapDirectory = new Fi(directory.toFile());
        var network = mock(NetworkService.class);
        var config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        var service = mock(MapService.class);
        var repository = mock(MapDataRepository.class);
        when(uploadRepository.save(anyString(), anyString(), any()))
                .thenReturn(true);
        var registry = mock(Maps.class);
        Vars.maps = registry;
        Vars.state = new mindustry.core.GameState();
        Vars.state.rules = mock(Rules.class);
        when(Vars.state.rules.mode()).thenReturn(Gamemode.pvp);
        var installed = realMap("Arena");
        when(service.findMapByFileName("arena.msav")).thenReturn(installed);
        var success = new HashMap<String, arc.func.ConsT<arc.util.Http.HttpResponse, Exception>>();
        var errors = new HashMap<String, Cons<Throwable>>();
        try (var http = org.mockito.Mockito.mockStatic(arc.util.Http.class)) {
            for (String url : List.of("https://example/arena.msav", "https://example/fail.msav")) {
                var request = mock(arc.util.Http.HttpRequest.class);
                http.when(() -> arc.util.Http.get(url)).thenReturn(request);
                when(request.timeout(org.mockito.ArgumentMatchers.anyInt())).thenReturn(request);
                when(request.error(any())).thenAnswer(call -> {
                    errors.put(url, call.getArgument(0));
                    return request;
                });
                doAnswer(call -> {
                    success.put(url, call.getArgument(0));
                    return null;
                }).when(request).submit(any());
            }
            var handler = new MapTransportHandler(network, config, service, repository,
                    new Async(InlineStorageExecutor.create(), Runnable::run), uploadRepository);
            Map<Class<?>, Cons<?>> listeners = new HashMap<>();
            captureListeners(network, listeners);
            handler.registerListeners();
            var upload = new org.xcore.protocol.generated.shared.MapDiscordUploadV1(
                    new org.xcore.protocol.generated.shared.DiscordIdentityRefV1("123456789012345678", "Reviewer"),
                    1791806400000L,
                    new org.xcore.protocol.generated.shared.DiscordIdentityRefV1("234567890123456789", "Creator"), null, null);
            listener(listeners, MapsLoadCommandV1.class).get(new MapsLoadCommandV1("mini-pvp", List.of(
                    new MapFileSourceV1("https://example/arena.msav", "arena.msav", upload),
                    new MapFileSourceV1("https://example/fail.msav", "fail.msav", upload))));
            var response = mock(arc.util.Http.HttpResponse.class);
            byte[] bytes = {97, 98, 99};
            when(response.getResult()).thenReturn(bytes);
            success.get("https://example/arena.msav").get(response);
            verify(registry, never()).reload();
            errors.get("https://example/fail.msav").get(new java.io.IOException("404"));
            verify(registry).reload();
            var metadata = ArgumentCaptor.forClass(org.xcore.plugin.model.MapDiscordUpload.class);
            verify(uploadRepository).save(org.mockito.ArgumentMatchers.eq("arena.msav"), org.mockito.ArgumentMatchers.eq(
                            org.xcore.plugin.map.domain.MapContentHash.digest(new java.io.ByteArrayInputStream(bytes)).asHex()),
                    metadata.capture());
            assertThat(metadata.getValue().creatorName).isEqualTo("Creator");
            verify(uploadRepository, never()).save(org.mockito.ArgumentMatchers.eq("fail.msav"), anyString(), any());
            assertThat(Vars.customMapDirectory.child("arena.msav").readBytes()).containsExactly(bytes);
        } finally {
            Vars.customMapDirectory = originalDirectory;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void lateBatchCannotRestoreOlderAttributionEvenWhenTheBytesAreIdentical(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var originalDirectory = Vars.customMapDirectory;
        Vars.customMapDirectory = new Fi(directory.toFile());
        var network = mock(NetworkService.class);
        var config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        var service = mock(MapService.class);
        var repository = mock(MapDataRepository.class);
        when(uploadRepository.save(anyString(), anyString(), any())).thenReturn(true);
        var registry = mock(Maps.class);
        Vars.maps = registry;
        Vars.state = new mindustry.core.GameState();
        Vars.state.rules = mock(Rules.class);
        when(Vars.state.rules.mode()).thenReturn(Gamemode.pvp);
        var arena = realMap("Arena");
        when(service.findMapByFileName("arena.msav")).thenReturn(arena);
        var success = new HashMap<String, arc.func.ConsT<arc.util.Http.HttpResponse, Exception>>();
        var errors = new HashMap<String, Cons<Throwable>>();
        try (var http = org.mockito.Mockito.mockStatic(arc.util.Http.class)) {
            for (String url : List.of("https://example/old.msav", "https://example/new.msav", "https://example/delayed.msav")) {
                var request = mock(arc.util.Http.HttpRequest.class);
                http.when(() -> arc.util.Http.get(url)).thenReturn(request);
                when(request.timeout(org.mockito.ArgumentMatchers.anyInt())).thenReturn(request);
                when(request.error(any())).thenAnswer(call -> { errors.put(url, call.getArgument(0)); return request; });
                doAnswer(call -> { success.put(url, call.getArgument(0)); return null; }).when(request).submit(any());
            }
            var handler = new MapTransportHandler(network, config, service, repository, new Async(InlineStorageExecutor.create(), Runnable::run), uploadRepository);
            Map<Class<?>, Cons<?>> listeners = new HashMap<>();
            captureListeners(network, listeners);
            handler.registerListeners();
            var old = new org.xcore.protocol.generated.shared.MapDiscordUploadV1(
                    new org.xcore.protocol.generated.shared.DiscordIdentityRefV1("123", "Old Sender"), 1,
                    new org.xcore.protocol.generated.shared.DiscordIdentityRefV1("789", "Old Creator"), null, null);
            var newer = new org.xcore.protocol.generated.shared.MapDiscordUploadV1(
                    new org.xcore.protocol.generated.shared.DiscordIdentityRefV1("456", "New Sender"), 2, null, null, null);
            listener(listeners, MapsLoadCommandV1.class).get(new MapsLoadCommandV1("mini-pvp", List.of(
                    new MapFileSourceV1("https://example/old.msav", "arena.msav", old),
                    new MapFileSourceV1("https://example/delayed.msav", "delayed.msav", old))));
            var response = mock(arc.util.Http.HttpResponse.class);
            when(response.getResult()).thenReturn(new byte[]{97, 98, 99});
            success.get("https://example/old.msav").get(response);
            listener(listeners, MapsLoadCommandV1.class).get(new MapsLoadCommandV1("mini-pvp", List.of(
                    new MapFileSourceV1("https://example/new.msav", "arena.msav", newer))));
            success.get("https://example/new.msav").get(response);
            errors.get("https://example/delayed.msav").get(new java.io.IOException("404"));
            var attribution = ArgumentCaptor.forClass(org.xcore.plugin.model.MapDiscordUpload.class);
            verify(uploadRepository).save(anyString(), anyString(), attribution.capture());
            assertThat(attribution.getValue().uploadedByName).isEqualTo("New Sender");
            assertThat(attribution.getValue().creatorDiscordId).isNull();
        } finally {
            Vars.customMapDirectory = originalDirectory;
        }
    }

    @Test
    void metadataWritesForOneFileAreOrderedAndOtherFilesRemainIndependent() {
        var storageTasks = new java.util.ArrayDeque<Runnable>();
        var storage = mock(StorageExecutor.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        doAnswer(call -> { storageTasks.add(call.getArgument(0)); return null; }).when(storage).execute(any());
        var repository = mock(MapDataRepository.class);
        var writes = new java.util.ArrayList<String>();
        when(uploadRepository.save(anyString(), anyString(), any()))
                .thenAnswer(call -> { writes.add(call.getArgument(1)); return true; });
        var handler = new MapTransportHandler(mock(NetworkService.class), new TomlXcoreConfig(), mock(MapService.class),
                repository, new Async(storage, Runnable::run), uploadRepository);
        var older = handler.enqueueDiscordUpload("Arena", "arena.msav", "Author", "pvp", "old", null, () -> true);
        var newer = handler.enqueueDiscordUpload("Arena", "arena.msav", "Author", "pvp", "new", null, () -> true);
        var other = handler.enqueueDiscordUpload("Other", "other.msav", "Author", "pvp", "other", null, () -> true);
        assertThat(storageTasks).hasSize(2); // the second arena write has not started
        storageTasks.remove().run();
        assertThat(older).isCompleted();
        assertThat(newer).isNotDone();
        storageTasks.remove().run();
        assertThat(other).isCompleted();
        storageTasks.remove().run();
        assertThat(newer).isCompleted();
        assertThat(writes).containsExactly("old", "other", "new");
    }

    @Test
    void staleInstallationIsSkippedAndAStorageFailureDoesNotBlockItsSuccessor() {
        var storageTasks = new java.util.ArrayDeque<Runnable>();
        var storage = mock(StorageExecutor.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        doAnswer(call -> { storageTasks.add(call.getArgument(0)); return null; }).when(storage).execute(any());
        var repository = mock(MapDataRepository.class);
        when(uploadRepository.save(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("storage unavailable")).thenReturn(true);
        var handler = new MapTransportHandler(mock(NetworkService.class), new TomlXcoreConfig(), mock(MapService.class),
                repository, new Async(storage, Runnable::run), uploadRepository);
        handler.enqueueDiscordUpload("Arena", "arena.msav", "Author", "pvp", "stale", null, () -> false);
        handler.enqueueDiscordUpload("Arena", "arena.msav", "Author", "pvp", "failed", null, () -> true);
        var newest = handler.enqueueDiscordUpload("Arena", "arena.msav", "Author", "pvp", "current", null, () -> true);
        storageTasks.remove().run();
        verifyNoInteractions(uploadRepository);
        storageTasks.remove().run();
        storageTasks.remove().run();
        assertThat(newest).isCompleted();
        verify(uploadRepository, never()).save(anyString(), org.mockito.ArgumentMatchers.eq("stale"), any());
        verify(uploadRepository).save(anyString(), org.mockito.ArgumentMatchers.eq("current"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadRpcConfirmsPartialResultsAndWaitsForCatalog(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var originalDirectory = Vars.customMapDirectory;
        Vars.customMapDirectory = new Fi(directory.toFile());
        var network = mock(NetworkService.class);
        var config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        var service = mock(MapService.class);
        var repository = mock(MapDataRepository.class);
        var registry = mock(Maps.class);
        Vars.maps = registry;
        Vars.state = new mindustry.core.GameState();
        var ready = new java.util.concurrent.CompletableFuture<Void>();
        when(service.rebuildIdentityCatalogAsync()).thenReturn(ready);
        when(service.refreshAttribution()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        var installed = realMap("Arena");
        when(service.findMapByFileName("arena.msav")).thenReturn(installed);
        when(uploadRepository.save(anyString(), anyString(), any())).thenReturn(true);
        var success = new HashMap<String, arc.func.ConsT<arc.util.Http.HttpResponse, Exception>>();
        var errors = new HashMap<String, Cons<Throwable>>();
        try (var http = org.mockito.Mockito.mockStatic(arc.util.Http.class)) {
            for (String url : List.of("https://example/arena", "https://example/fail", "https://example/bad")) {
                var download = mock(arc.util.Http.HttpRequest.class);
                http.when(() -> arc.util.Http.get(url)).thenReturn(download);
                when(download.timeout(org.mockito.ArgumentMatchers.anyInt())).thenReturn(download);
                when(download.error(any())).thenAnswer(call -> { errors.put(url, call.getArgument(0)); return download; });
                doAnswer(call -> { success.put(url, call.getArgument(0)); return null; }).when(download).submit(any());
            }
            var handler = new MapTransportHandler(network, config, service, repository,
                    new Async(InlineStorageExecutor.create(), Runnable::run), uploadRepository);
            Map<Class<?>, Cons<?>> listeners = new HashMap<>();
            captureListeners(network, listeners);
            handler.registerListeners();
            var request = new org.xcore.protocol.generated.messages.maps.MapsMessages.MapsUploadRequestV1("mini-pvp", List.of(
                    new MapFileSourceV1("https://example/arena", "arena.msav", null),
                    new MapFileSourceV1("https://example/fail", "fail.msav", null),
                    new MapFileSourceV1("https://example/bad", "bad.msav", null)));
            listener(listeners, org.xcore.protocol.generated.messages.maps.MapsMessages.MapsUploadRequestV1.class).get(request);
            var bytes = mock(arc.util.Http.HttpResponse.class);
            when(bytes.getResult()).thenReturn(new byte[] {1, 2, 3});
            success.get("https://example/arena").get(bytes);
            errors.get("https://example/fail").get(new java.io.IOException("404"));
            success.get("https://example/bad").get(bytes);
            verify(network, never()).respond(any(), any());
            ready.complete(null);
            var result = ArgumentCaptor.forClass(org.xcore.protocol.generated.messages.maps.MapsMessages.MapsUploadResponseV1.class);
            verify(network).respond(org.mockito.ArgumentMatchers.eq(request), result.capture());
            assertThat(result.getValue().results()).extracting(org.xcore.protocol.generated.shared.MapUploadResultV1::status)
                    .containsExactly(org.xcore.protocol.generated.shared.MapUploadResultV1Status.INSTALLED,
                            org.xcore.protocol.generated.shared.MapUploadResultV1Status.DOWNLOAD_FAILED,
                            org.xcore.protocol.generated.shared.MapUploadResultV1Status.INVALID_MAP);
            verify(repository).findOrCreate(org.mockito.ArgumentMatchers.eq("Arena"), org.mockito.ArgumentMatchers.eq("arena.msav"),
                    org.mockito.ArgumentMatchers.eq("somebody"), anyString());
        } finally { Vars.customMapDirectory = originalDirectory; }
    }

    @Test
    @SuppressWarnings("unchecked")
    void removalTombstoneSurvivesADownloadWritingDuringTheDelete(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var previousDirectory = Vars.customMapDirectory;
        Vars.customMapDirectory = new Fi(directory.toFile());
        try {
            var main = new java.util.ArrayDeque<Runnable>();
            var storageTasks = new java.util.ArrayDeque<Runnable>();
            var storage = mock(StorageExecutor.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            doAnswer(call -> { storageTasks.add(call.getArgument(0)); return null; }).when(storage).execute(any());
            var network = mock(NetworkService.class);
            var service = mock(MapService.class);
            var config = new TomlXcoreConfig();
            config.server.name = "mini-pvp";
            Vars.maps = mock(Maps.class);
            Vars.state = new mindustry.core.GameState();
            var installed = realMap("Arena");
            var removed = new java.util.concurrent.atomic.AtomicBoolean();
            when(service.findMapByFileName("arena.msav")).thenAnswer(call -> removed.get() ? null : installed);
            var success = new java.util.concurrent.atomic.AtomicReference<arc.func.ConsT<arc.util.Http.HttpResponse, Exception>>();
            try (var http = org.mockito.Mockito.mockStatic(arc.util.Http.class)) {
                var download = mock(arc.util.Http.HttpRequest.class);
                http.when(() -> arc.util.Http.get("https://example/arena")).thenReturn(download);
                when(download.timeout(org.mockito.ArgumentMatchers.anyInt())).thenReturn(download);
                when(download.error(any())).thenReturn(download);
                doAnswer(call -> { success.set(call.getArgument(0)); return null; }).when(download).submit(any());
                var response = mock(arc.util.Http.HttpResponse.class);
                when(response.getResult()).thenReturn(new byte[] {1, 2, 3});
                doAnswer(call -> {
                    success.get().get(response); // HTTP writes and publishes a newer token inside the removal window.
                    Vars.customMapDirectory.child("arena.msav").delete();
                    removed.set(true);
                    return null;
                }).when(Vars.maps).removeMap(installed);
                when(uploadRepository.save(org.mockito.ArgumentMatchers.eq("arena.msav"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull())).thenReturn(true);
                var handler = new MapTransportHandler(network, config, service, mock(MapDataRepository.class), new Async(storage, main::add), uploadRepository);
                Map<Class<?>, Cons<?>> listeners = new HashMap<>();
                captureListeners(network, listeners);
                handler.registerListeners();
                listener(listeners, MapsLoadCommandV1.class).get(new MapsLoadCommandV1("mini-pvp", List.of(new MapFileSourceV1("https://example/arena", "arena.msav", null))));
                listener(listeners, MapsRemoveRequestV1.class).get(new MapsRemoveRequestV1("mini-pvp", "arena.msav"));
                while (!main.isEmpty()) main.remove().run();
                while (!storageTasks.isEmpty()) storageTasks.remove().run();
                while (!main.isEmpty()) main.remove().run();
                verify(uploadRepository).save(org.mockito.ArgumentMatchers.eq("arena.msav"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
                assertThat(Vars.customMapDirectory.child("arena.msav").exists()).isFalse();
            }
        } finally { Vars.customMapDirectory = previousDirectory; }
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
