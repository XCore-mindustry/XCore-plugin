package org.xcore.plugin.service.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.model.MapData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MapContentHashServiceTest {

    private static MapContentHashService.PathLike pathLike(Path path) {
        return new MapContentHashService.PathLike() {
            @Override public java.io.InputStream read() throws Exception { return Files.newInputStream(path); }
            @Override public String name() { return path.getFileName().toString(); }
        };
    }

    private static final String ABC_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir Path directory;


    @Test
    void computesHashFromMapFileAndStoresOnMapData() throws Exception {
        Path file = directory.resolve("map.msav");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        MapData data = new MapData("Test", "map.msav", "author", "survival");
        MapContentHashService service = MapContentHashService.withWorker(Runnable::run);
        service.hashAndStore(data, pathLike(file));

        assertEquals(ABC_HASH, data.contentHash);
    }

    @Test
    void runAsyncStoresHashOnMainThreadAndKeepsTickFree() throws Exception {
        Path file = directory.resolve("map.msav");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);

        MapData data = new MapData("Test", "map.msav", "author", "survival");
        MapContentHashService service = MapContentHashService.withWorker(Runnable::run);
        CountDownLatch stored = new CountDownLatch(1);
        AtomicReference<String> seenThread = new AtomicReference<>();
        service.hashAndStoreAsync(data, pathLike(file), () -> stored.countDown());

        assertTrue(stored.await(5, TimeUnit.SECONDS));
        assertEquals(ABC_HASH, data.contentHash);
        assertNull(seenThread.get());
    }

    @Test
    void unreadableFileLeavesHashNullInsteadOfCrashing() {
        MapData data = new MapData("Test", "missing.msav", "author", "survival");
        MapContentHashService service = MapContentHashService.withWorker(Runnable::run);
        service.hashAndStore(data, pathLike(directory.resolve("missing.msav")));
        assertNull(data.contentHash);
    }
}
