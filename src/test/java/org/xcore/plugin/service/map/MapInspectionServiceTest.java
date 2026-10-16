package org.xcore.plugin.service.map;

import arc.Events;
import arc.func.Cons;
import arc.files.Fi;
import arc.struct.StringMap;
import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.ContentLoader;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.service.map.inspect.*;
import org.xcore.plugin.ui.XcoreImageService;
import org.xcore.protocol.generated.shared.MapFileSourceV1;
import org.xcore.testkit.fixtures.HeadlessWorld;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapInspectionServiceTest {
    @TempDir Path directory;
    private final MapFileSourceV1 source = new MapFileSourceV1("https://example.invalid/map", "fixture.msav", null);

    @AfterEach void resetThread() { GameThread.setGameThreadOverride(null); }

    @Test
    void serviceDeletesTempOnSuccessInvalidMapAndAfterPureChecks() throws Exception {
        var downloader = mock(MapDownloader.class);
        var service = new MapInspectionService(new TomlSecretsConfig(), downloader, new DetachedMapReadLock());
        var context = new MapInspectionService.Context(MsavFixture.registry(), MsavFixture.settings());
        Path valid = new MsavFixture().write(directory);
        when(downloader.download(anyString(), anyInt())).thenReturn(new MapDownloader.Download(valid, "a".repeat(64), (int) Files.size(valid)));
        var result = service.inspect(source, "tower-defense", context);
        assertThat(result.status()).isEqualTo(MapInspectionResult.Status.OK); assertThat(result.findings()).isEmpty();
        assertThat(valid).doesNotExist();
        Path corrupt = directory.resolve("bad.msav"); Files.write(corrupt, new byte[]{1, 2, 3});
        when(downloader.download(anyString(), anyInt())).thenReturn(new MapDownloader.Download(corrupt, "b".repeat(64), 3));
        result = service.inspect(source, null, context);
        assertThat(result.status()).isEqualTo(MapInspectionResult.Status.INVALID_MAP); assertThat(corrupt).doesNotExist();
        when(downloader.download(anyString(), anyInt())).thenThrow(new IOException("Map download timed out"));
        assertThat(service.inspect(source, null, context).status()).isEqualTo(MapInspectionResult.Status.DOWNLOAD_FAILED);
    }

    @Test
    void registryCaptureAndInspectionLeaveLiveWorldRulesGroupsEventsAndTemporaryMapperUnchanged() throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        // An explicit disposable engine fixture supplies a running world. No save is ever loaded into it.
        try (var engine = HeadlessWorld.builder().dimensions(8, 8).generating(true).build();
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            engine.setFloor(2, 3, Blocks.sand);
            var player = engine.addPlayer("Live player", mindustry.game.Team.sharded);
            Vars.state.wave = 57; Vars.state.tick = 123; Vars.state.wavetime = 999;
            var world = Vars.world; var state = Vars.state; var content = Vars.content; var rules = state.rules;
            var tile = world.tile(2, 3); var floor = tile.floor(); var stats = state.stats; var teams = state.teams;
            int players = Groups.player.size(), builds = Groups.build.size(), all = Groups.all.size(), units = Groups.unit.size();
            var mapperField = ContentLoader.class.getDeclaredField("temporaryMapper"); mapperField.setAccessible(true);
            Object previousMapper = mapperField.get(content);
            var mapper = new MappableContent[ContentType.all.length][];
            mapper[ContentType.block.ordinal()] = new MappableContent[]{Blocks.sand, Blocks.router};
            content.setTemporaryMapper(mapper);
            var events = new AtomicInteger();
            Cons<EventType.SaveLoadEvent> load = ignored -> events.incrementAndGet();
            Cons<EventType.DataPatchLoadEvent> patch = ignored -> events.incrementAndGet();
            Cons<EventType.WorldLoadEvent> worldLoad = ignored -> events.incrementAndGet();
            Events.on(EventType.SaveLoadEvent.class, load); Events.on(EventType.DataPatchLoadEvent.class, patch); Events.on(EventType.WorldLoadEvent.class, worldLoad);
            try {
                var downloader = mock(MapDownloader.class);
                var service = new MapInspectionService(new TomlSecretsConfig(), downloader, new DetachedMapReadLock());
                var context = service.capture();
                assertThat(context.registry().blocks().get(0).name()).isEqualTo("air");
                assertThat(content.block(0)).isSameAs(Blocks.sand);
                Path map = new MsavFixture().write(directory);
                when(downloader.download(anyString(), anyInt())).thenReturn(new MapDownloader.Download(map, "c".repeat(64), (int) Files.size(map)));
                var began = new CountDownLatch(1);
                var result = workers.submit(() -> { began.countDown(); return service.inspect(source, null, context); });
                assertThat(began.await(2, TimeUnit.SECONDS)).isTrue();
                int reads = 0;
                do {
                    // Engine-like game-thread reads execute concurrently with detached parsing, including mapped IDs.
                    assertThat(Vars.content.block(0)).isSameAs(Blocks.sand);
                    assertThat(Vars.content.block(1)).isSameAs(Blocks.router);
                    assertThat(mapperField.get(content)).isSameAs(mapper);
                    assertThat(Vars.world).isSameAs(world); assertThat(Vars.state).isSameAs(state); assertThat(Vars.content).isSameAs(content);
                    assertThat(world.tile(2, 3)).isSameAs(tile); assertThat(tile.floor()).isSameAs(floor);
                    assertThat(world.isGenerating()).isTrue(); assertThat(state.rules).isSameAs(rules);
                    assertThat(state.wave).isEqualTo(57); assertThat(state.tick).isEqualTo(123); assertThat(state.wavetime).isEqualTo(999);
                    assertThat(state.stats).isSameAs(stats); assertThat(state.teams).isSameAs(teams);
                    assertThat(Groups.player.size()).isEqualTo(players); assertThat(Groups.build.size()).isEqualTo(builds);
                    assertThat(Groups.all.size()).isEqualTo(all); assertThat(Groups.unit.size()).isEqualTo(units);
                    assertThat(Groups.player.contains(p -> p == player.player())).isTrue();
                    reads++;
                } while (!result.isDone() && reads < 50000);
                assertThat(reads).isPositive();
                assertThat(result.get(2, TimeUnit.SECONDS).status()).isEqualTo(MapInspectionResult.Status.OK);
                assertThat(events).hasValue(0);
                assertThat(mapperField.get(content)).isSameAs(mapper);
            } finally {
                content.setTemporaryMapper((MappableContent[][]) previousMapper);
                Events.remove(EventType.SaveLoadEvent.class, load); Events.remove(EventType.DataPatchLoadEvent.class, patch); Events.remove(EventType.WorldLoadEvent.class, worldLoad);
            }
        }
    }

    @Test
    void inspectionWaitsForTheActualPreviewReadAndBothUseOneLock() throws Exception {
        var lock = spy(new DetachedMapReadLock());
        var previewInside = new CountDownLatch(1); var releasePreview = new CountDownLatch(1); var inspectionAttempt = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals("inspection-worker")) inspectionAttempt.countDown();
            return invocation.callRealMethod();
        }).when(lock).lock();
        var previewFile = mock(Fi.class); when(previewFile.exists()).thenReturn(false, true);
        when(previewFile.readBytes()).thenAnswer(ignored -> {
            previewInside.countDown();
            if (!releasePreview.await(3, TimeUnit.SECONDS)) throw new AssertionError("Preview not released");
            return new byte[]{1};
        });
        var previewMap = mock(mindustry.maps.Map.class); when(previewMap.previewFile()).thenReturn(previewFile);
        when(previewMap.plainName()).thenReturn("Fixture");
        var image = mock(XcoreImageService.class); when(image.ensureDelivered(any(), any())).thenReturn("region");
        var preview = new MapPreviewService(null, image, lock);
        var downloader = mock(MapDownloader.class);
        Path file = new MsavFixture().write(directory);
        when(downloader.download(anyString(), anyInt())).thenReturn(new MapDownloader.Download(file, "d".repeat(64), (int) Files.size(file)));
        var inspection = new MapInspectionService(new TomlSecretsConfig(), downloader, lock);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var previewDone = workers.submit(() -> preview.requestPreview(mock(mindustry.gen.Player.class), previewMap, (region, error) -> assertThat(error).isNull()));
            assertThat(previewInside.await(2, TimeUnit.SECONDS)).isTrue();
            var inspectionDone = workers.submit(() -> {
                Thread.currentThread().setName("inspection-worker");
                return inspection.inspect(source, null, new MapInspectionService.Context(MsavFixture.registry(), MsavFixture.settings()));
            });
            try {
                assertThat(inspectionAttempt.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(inspectionDone.isDone()).isFalse();
            } finally { releasePreview.countDown(); }
            previewDone.get(2, TimeUnit.SECONDS);
            assertThat(inspectionDone.get(2, TimeUnit.SECONDS).status()).isEqualTo(MapInspectionResult.Status.OK);
            verify(lock, times(2)).lock(); verify(lock, times(2)).unlock();
        } finally { releasePreview.countDown(); }
    }

    @Test
    void concurrentInspectionsSerializeAndDeleteEveryTemp() throws Exception {
        var lock = spy(new DetachedMapReadLock()); var active = new AtomicInteger(); var maximum = new AtomicInteger();
        doAnswer(invocation -> { invocation.callRealMethod(); maximum.accumulateAndGet(active.incrementAndGet(), Math::max); return null; }).when(lock).lock();
        doAnswer(invocation -> { active.decrementAndGet(); return invocation.callRealMethod(); }).when(lock).unlock();
        var downloader = mock(MapDownloader.class); var barrier = new CyclicBarrier(12);
        when(downloader.download(anyString(), anyInt())).thenAnswer(ignored -> {
            var file = new MsavFixture().write(directory); barrier.await(3, TimeUnit.SECONDS);
            return new MapDownloader.Download(file, "e".repeat(64), (int) Files.size(file));
        });
        var service = new MapInspectionService(new TomlSecretsConfig(), downloader, lock);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<Future<MapInspectionResult>>();
            for (int i = 0; i < 12; i++) futures.add(workers.submit(() -> service.inspect(source, null, new MapInspectionService.Context(MsavFixture.registry(), MsavFixture.settings()))));
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS).status()).isEqualTo(MapInspectionResult.Status.OK);
        }
        assertThat(maximum).hasValue(1); assertThat(active).hasValue(0);
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }

    @Test
    void bundledV160MapsCanBeInspectedWithoutLoadingThem() throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        try (var engine = HeadlessWorld.create(8, 8)) {
            var registry = ContentRegistrySnapshot.capture();
            for (String name : List.of("fork", "fortress", "caldera")) {
                Path file = directory.resolve(name + ".msav");
                try (InputStream input = getClass().getResourceAsStream("/maps/inspection/" + name + ".msav")) {
                    assertThat(input).isNotNull(); Files.copy(input, file);
                }
                var snapshot = new DetachedMsavReader().read(file, "f".repeat(64), registry, MsavFixture.settings());
                assertThat(snapshot.tilesAvailable()).isTrue(); assertThat(snapshot.tileCount()).isPositive();
                assertThat(snapshot.cores()).isNotEmpty(); assertThat(Vars.world.width()).isEqualTo(8);
            }
        }
    }

    @Test
    void gameThreadCaptureFreezesSeverityAndDimensionPolicyBeforeWorkerInspection() throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        try (var engine = HeadlessWorld.create(8, 8); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var config = new TomlSecretsConfig();
            config.maps.checks.severities.put("size-out-of-range", "warning");
            config.maps.checks.profiles.get("tower-defense").minWidth = 6;
            var downloader = mock(MapDownloader.class);
            Path map = new MsavFixture().write(directory);
            when(downloader.download(anyString(), anyInt())).thenReturn(new MapDownloader.Download(map, "c".repeat(64), (int) Files.size(map)));
            var service = new MapInspectionService(config, downloader, new DetachedMapReadLock());
            var context = service.capture();
            config.maps.checks.severities.put("size-out-of-range", "info");
            config.maps.checks.profiles.get("tower-defense").minWidth = 1;
            var result = workers.submit(() -> service.inspect(source, "tower-defense", context)).get(3, TimeUnit.SECONDS);
            assertThat(result.status()).isEqualTo(MapInspectionResult.Status.OK);
            assertThat(result.snapshot().tilesAvailable()).isTrue();
            assertThat(result.findings()).filteredOn(f -> f.code().equals("size-out-of-range")).singleElement().satisfies(f -> {
                assertThat(f.severity()).isEqualTo(MapFinding.Severity.WARNING);
                assertThat(f.detail()).isEqualTo("5 x 5 width < min_width=6");
            });
        }
    }

    @Test
    void malformedNativeBaseReturnsInvalidMapWithoutAnyCoreClaimsAndDeletesTheDownload() throws Exception {
        var fixture = new MsavFixture();
        fixture.buildingPayloads.put(14, java.util.Arrays.copyOf(MsavFixture.modernCoreBase(1), 7));
        var map = fixture.write(directory);
        var downloader = mock(MapDownloader.class);
        when(downloader.download(anyString(), anyInt())).thenReturn(new MapDownloader.Download(map, "c".repeat(64), (int) Files.size(map)));
        var service = new MapInspectionService(new TomlSecretsConfig(), downloader, new DetachedMapReadLock());
        var result = service.inspect(source, "pvp", new MapInspectionService.Context(MsavFixture.registry(), MsavFixture.settings()));
        assertThat(result.status()).isEqualTo(MapInspectionResult.Status.INVALID_MAP);
        assertThat(result.snapshot()).isNull(); assertThat(result.findings()).isEmpty();
        assertThat(map).doesNotExist();
    }

    @Test
    void generatedAvajeWiringSharesTheSameLockBetweenServices() throws Exception {
        var secrets = new TomlSecretsConfig();
        try (var scope = BeanScope.builder().bean(TomlSecretsConfig.class, secrets)
                .modules(new AvajeModule() {
                    @Override public Class<?>[] classes() { return new Class<?>[]{MapInspectionService.class, MapPreviewService.class, DetachedMapReadLock.class, MapDownloader.class}; }
                    @Override public void build(Builder builder) {
                        org.xcore.plugin.service.map.inspect.DetachedMapReadLock$DI.build(builder);
                        org.xcore.plugin.service.map.inspect.MapDownloader$DI.build(builder);
                        MapInspectionService$DI.build(builder); MapPreviewService$DI.build(builder);
                    }
                }).forTesting().mock(Async.class).mock(XcoreImageService.class).build()) {
            var lock = scope.get(DetachedMapReadLock.class);
            var inspectionField = MapInspectionService.class.getDeclaredField("readLock"); inspectionField.setAccessible(true);
            var previewField = MapPreviewService.class.getDeclaredField("previewGenerationLock"); previewField.setAccessible(true);
            assertThat(inspectionField.get(scope.get(MapInspectionService.class))).isSameAs(lock);
            assertThat(previewField.get(scope.get(MapPreviewService.class))).isSameAs(lock);
        }
    }
}
