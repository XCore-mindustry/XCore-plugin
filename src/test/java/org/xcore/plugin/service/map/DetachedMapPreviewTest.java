package org.xcore.plugin.service.map;

import arc.Events;
import arc.files.Fi;
import arc.func.Cons;
import arc.graphics.Pixmap;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.ContentLoader;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.io.TypeIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.service.map.inspect.DetachedMapReadLock;
import org.xcore.plugin.service.map.inspect.MsavFixture;
import org.xcore.plugin.ui.XcoreImageService;
import org.xcore.testkit.fixtures.HeadlessWorld;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DetachedMapPreviewTest {
    @TempDir Path directory;

    @AfterEach void resetThread() { GameThread.setGameThreadOverride(null); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void workerPreservesLiveMapperWorldAndEventsEvenWhenMapIsInvalid(boolean corrupt) throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        try (var engine = HeadlessWorld.builder().dimensions(8, 8).generating(true).build();
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            Path file = new MsavFixture().write(directory);
            if (corrupt) Files.write(file, new byte[]{1, 2, 3});
            var mapperField = ContentLoader.class.getDeclaredField("temporaryMapper");
            mapperField.setAccessible(true);
            var mapper = new MappableContent[ContentType.all.length][];
            mapper[ContentType.block.ordinal()] = new MappableContent[]{Blocks.sand, Blocks.router};
            var content = Vars.content;
            var world = Vars.world;
            var state = Vars.state;
            var floor = world.tile(2, 3).floor();
            content.setTemporaryMapper(mapper);
            var events = new AtomicInteger();
            Cons<EventType.WorldLoadEvent> loaded = ignored -> events.incrementAndGet();
            Events.on(EventType.WorldLoadEvent.class, loaded);
            try {
                var preview = DetachedMapPreview.capture();
                var result = workers.submit(() -> {
                    if (corrupt) {
                        assertThatThrownBy(() -> preview.read(new Fi(file.toFile()))).isInstanceOf(IOException.class);
                    } else {
                        var pixmap = preview.read(new Fi(file.toFile()));
                        try {
                            assertThat(pixmap.width).isEqualTo(5);
                            assertThat(pixmap.height).isEqualTo(5);
                            assertThat(pixmap.get(4, 2)).isEqualTo(Team.sharded.color.rgba());
                            assertThat(pixmap.get(0, 4)).isEqualTo(MapColorPalette.colorFor(Blocks.stone));
                        } finally { pixmap.dispose(); }
                    }
                    return null;
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                do {
                    assertThat(mapperField.get(content)).isSameAs(mapper);
                    assertThat(Vars.content.block(0)).isSameAs(Blocks.sand);
                    assertThat(Vars.content.copy().block(1)).isSameAs(Blocks.router);
                } while (!result.isDone() && System.nanoTime() < deadline);
                result.get(5, TimeUnit.SECONDS);
                assertThat(mapperField.get(content)).isSameAs(mapper);
                assertThat(Vars.content).isSameAs(content);
                assertThat(Vars.world).isSameAs(world);
                assertThat(Vars.state).isSameAs(state);
                assertThat(world.isGenerating()).isTrue();
                assertThat(world.tile(2, 3).floor()).isSameAs(floor);
                assertThat(events).hasValue(0);
            } finally {
                content.setTemporaryMapper(null);
                Events.remove(EventType.WorldLoadEvent.class, loaded);
            }
            assertConveyorRoundTrip();
        }
    }

    @Test
    void uncachedServiceGeneratesPngWithoutChangingMapMetadataOrBlockColors() throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        try (var engine = HeadlessWorld.builder().dimensions(8, 8).generating(true).build()) {
            var file = new MsavFixture().write(directory);
            var map = spy(new mindustry.maps.Map(new Fi(file.toFile()), 5, 5, new arc.struct.StringMap(), true));
            doReturn(new Fi(directory.resolve("preview.png").toFile())).when(map).previewFile();
            var imageService = mock(XcoreImageService.class);
            when(imageService.ensureDelivered(any(), any())).thenReturn("preview-region");
            var delivered = new AtomicReference<String>();
            int oldColor = Blocks.stone.mapColor.rgba();
            new MapPreviewService(null, imageService, new DetachedMapReadLock())
                    .requestPreview(engine.addPlayer("Preview", Team.sharded).player(), map, (region, error) -> {
                        assertThat(error).isNull();
                        delivered.set(region);
                    });
            assertThat(delivered).hasValue("preview-region");
            var png = org.mockito.ArgumentCaptor.forClass(byte[].class);
            verify(imageService).ensureDelivered(any(), png.capture());
            var pixmap = new Pixmap(png.getValue());
            try { assertThat(pixmap.width).isEqualTo(5); assertThat(pixmap.height).isEqualTo(5); }
            finally { pixmap.dispose(); }
            assertThat(Blocks.stone.mapColor.rgba()).isEqualTo(oldColor);
            assertThat(map.teams.size).isZero();
            assertThat(map.spawns).isZero();
            assertThat(Vars.world.isGenerating()).isTrue();
            assertConveyorRoundTrip();
        }
    }

    @Test
    void bundledMapCanBeReadFromAStreamWithoutAFilePath() throws Exception {
        GameThread.setGameThreadOverride(Thread.currentThread());
        try (var engine = HeadlessWorld.builder().dimensions(8, 8).build()) {
            var fixture = new MsavFixture();
            fixture.teams[14] = 200;
            byte[] bytes = fixture.compressed();
            var file = mock(Fi.class);
            var stream = spy(new ByteArrayInputStream(bytes));
            when(file.read()).thenReturn(stream);
            when(file.length()).thenReturn(0L); // Classpath resources need not have a known length.
            var pixmap = DetachedMapPreview.capture().read(file);
            try { assertThat(pixmap.get(4, 2)).isEqualTo(Team.get(200).color.rgba()); }
            finally { pixmap.dispose(); }
            verify(stream, atLeastOnce()).close();
            verify(file, never()).file();
            assertConveyorRoundTrip();
        }
    }

    private static void assertConveyorRoundTrip() {
        var plans = new TypeIO.ClientBuildPlans();
        plans.add(new BuildPlan(3, 4, 2, Blocks.conveyor));
        var bytes = new ByteArrayOutputStream();
        TypeIO.writeClientPlans(new Writes(new DataOutputStream(bytes)), plans);
        var decoded = TypeIO.readClientPlans(new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        assertThat(decoded.first().block).isSameAs(Blocks.conveyor);
        assertThat(decoded.first().rotation).isEqualTo(2);
    }
}
