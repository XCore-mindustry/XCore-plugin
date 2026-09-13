package org.xcore.plugin.service.map;

import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.graphics.PixmapIO;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.ui.XcoreImageService;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Multi-tiered, non-blocking map preview resolution and streaming service.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Main tick thread is NEVER blocked on disk I/O, Pixmap generation, or PNG compression.</li>
 *   <li>Off-heap native Pixmap allocations are strictly disposed in {@code finally} blocks.</li>
 *   <li>Map generation concurrency is serialized with a lock to prevent tile corruption.</li>
 *   <li>Pre-rendered textures are cached in memory (L1 LRU) and streamed via {@link XcoreImageService}.</li>
 * </ul>
 */
@Singleton
public class MapPreviewService {

    private static final int MAX_MEMORY_CACHE_ENTRIES = 32;

    private final Async async;
    private final XcoreImageService imageService;
    private final ReentrantLock previewGenerationLock = new ReentrantLock();

    // Map file name -> Streamed region name ("net-xcore_<hash16>")
    private final java.util.Map<String, String> fileNameToRegion = new ConcurrentHashMap<>();

    // Bounded L1 in-memory LRU cache for PNG byte arrays
    private final java.util.Map<String, byte[]> lruBytesCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, byte[]> eldest) {
            return size() > MAX_MEMORY_CACHE_ENTRIES;
        }
    };

    @Inject
    public MapPreviewService(Async async, XcoreImageService imageService) {
        this.async = async;
        this.imageService = imageService;
    }

    /**
     * Checks if a preview region name is already known and registered in memory.
     */
    public String getCachedRegionName(Map map) {
        if (map == null || map.file == null) return null;
        return fileNameToRegion.get(map.file.name());
    }

    /**
     * Resolves map preview PNG bytes asynchronously, registers the texture in
     * {@link XcoreImageService}, streams it to the player, and executes {@code onComplete}
     * on the Mindustry main thread.
     */
    public void requestPreview(Player player, Map map, BiConsumer<String, Throwable> onComplete) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(onComplete, "onComplete");

        if (map == null) {
            onComplete.accept(null, new IllegalArgumentException("Map cannot be null"));
            return;
        }

        String fileName = map.file != null ? map.file.name() : map.plainName();
        String cachedRegion = fileNameToRegion.get(fileName);

        // Fast Path: already loaded in memory
        if (cachedRegion != null) {
            byte[] cachedBytes;
            synchronized (lruBytesCache) {
                cachedBytes = lruBytesCache.get(cachedRegion);
            }
            if (cachedBytes != null) {
                imageService.ensureDeliveredName(player, cachedRegion);
                onComplete.accept(cachedRegion, null);
                return;
            }
        }

        // Slow Path: off-thread resolution via Virtual Threads
        if (async != null) {
            async.forPlayer(player, () -> loadOrGeneratePngBytes(map), (p, result) -> {
                if (result.error != null || result.bytes == null) {
                    onComplete.accept(null, result.error);
                    return;
                }

                // Main Thread: register in TextureRegistry and stream to player connection
                String regionName = imageService.ensureDelivered(p, result.bytes);
                fileNameToRegion.put(fileName, regionName);

                synchronized (lruBytesCache) {
                    lruBytesCache.put(regionName, result.bytes);
                }

                onComplete.accept(regionName, null);
            });
        } else {
            // Fallback for headless tests without Async dispatcher
            PngResult result = loadOrGeneratePngBytes(map);
            if (result.bytes != null) {
                String regionName = imageService.ensureDelivered(player, result.bytes);
                fileNameToRegion.put(fileName, regionName);
                onComplete.accept(regionName, null);
            } else {
                onComplete.accept(null, result.error);
            }
        }
    }

    private record PngResult(byte[] bytes, Throwable error) {}

    private PngResult loadOrGeneratePngBytes(Map map) {
        try {
            Fi previewFi = map.previewFile();

            // 1. Check if Mindustry on-disk preview exists
            if (previewFi != null && previewFi.exists()) {
                return new PngResult(previewFi.readBytes(), null);
            }

            // 2. Generate preview under mutex lock to protect global world/content state
            previewGenerationLock.lock();
            Pixmap pixmap = null;
            try {
                // Re-check disk after acquiring lock
                if (previewFi != null && previewFi.exists()) {
                    return new PngResult(previewFi.readBytes(), null);
                }

                MapColorPalette.ensureInitialized();
                pixmap = MapIO.generatePreview(map);
                if (pixmap == null) {
                    return new PngResult(null, new IllegalStateException("MapIO returned null preview"));
                }

                byte[] bytes = PixmapIO.writePngBytes(pixmap);

                // Persist to disk for future server runs
                if (previewFi != null) {
                    try {
                        previewFi.writePng(pixmap);
                    } catch (Throwable writeError) {
                        Log.warn("Failed to persist map preview to disk: @", writeError.getMessage());
                    }
                }

                return new PngResult(bytes, null);
            } finally {
                if (pixmap != null) {
                    pixmap.dispose(); // Critical: prevent off-heap native memory leak
                }
                previewGenerationLock.unlock();
            }
        } catch (Throwable e) {
            Log.err("Error loading preview for map @: @", map.plainName(), e.getMessage());
            return new PngResult(null, e);
        }
    }
}
