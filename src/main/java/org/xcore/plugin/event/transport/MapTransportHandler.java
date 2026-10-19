package org.xcore.plugin.event.transport;

import arc.util.Http;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.maps.Map;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsLoadCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1;
import org.xcore.protocol.generated.shared.MapFileSourceV1;
import org.xcore.protocol.generated.shared.MapEntryV1;
import org.xcore.protocol.generated.shared.MapUploadResultV1;
import org.xcore.protocol.generated.shared.MapUploadResultV1Status;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsUploadRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsUploadResponseV1;
import org.xcore.plugin.database.repository.MapUploadRepository;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.MapDiscordUpload;
import org.xcore.plugin.map.domain.MapContentHash;
import org.xcore.plugin.security.MapFileNameSanitizer;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.MapsProtocolMapper;
import org.xcore.plugin.service.map.MapInspectionService;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsInspectRequestV1;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.CompletionStage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.customMapDirectory;
import static mindustry.Vars.maps;
import static mindustry.Vars.state;
import static org.xcore.plugin.common.PLog.err;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class MapTransportHandler {

    private final NetworkService network;
    private final TomlXcoreConfig config;
    private final MapService mapService;
    private final MapDataRepository mapDataRepository;
    private final Async async;
    private final MapUploadRepository uploadRepository;
    private final MapInspectionService inspection;
    private final AtomicInteger inspectionsInFlight = new AtomicInteger();
    private static final int MAX_INSPECTIONS = 4;
    private final Object[] fileLocks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();
    private final ConcurrentHashMap<String, DownloadedMap> installations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> activeUploads = new ConcurrentHashMap<>();
    private final AtomicLong installationSequence = new AtomicLong();
    /** Accessed only on the game thread; storage writes for one file never overtake one another. */
    private final java.util.Map<String, CompletableFuture<Boolean>> pendingUploads = new java.util.HashMap<>();

    @Inject
    public MapTransportHandler(NetworkService network,
                               TomlXcoreConfig config,
                               MapService mapService,
                               MapDataRepository mapDataRepository,
                               Async async, MapUploadRepository uploadRepository, MapInspectionService inspection) {
        this.network = network;
        this.config = config;
        this.mapService = mapService;
        this.mapDataRepository = mapDataRepository;
        this.async = async;
        this.uploadRepository = uploadRepository;
        this.inspection = inspection;
    }

    /** A map plus the identity fields read from the engine, captured on the game thread. */
    private record MapIdentity(Map map, String fileName, String plainName, String author, MapDiscordUpload upload) {
    }

    private record DownloadedMap(String hash, long sequence) {}

    CompletableFuture<Boolean> enqueueDiscordUpload(
            String name, String fileName, String author, String mode, String hash, MapDiscordUpload upload,
            BooleanSupplier stillInstalled) {
        var previous = pendingUploads.getOrDefault(fileName, CompletableFuture.completedFuture(true));
        var write = previous.handle((ignored, error) -> null).thenCompose(ignored ->
                async.supply(() -> {
                    if (!stillInstalled.getAsBoolean() || !uploadRepository.save(fileName, hash, upload)) return false;
                    // Preserve the previous availability of votes for a newly installed map.
                    // Existing aliases/statistics are reused, never overwritten by attribution.
                    if (name != null && !name.isBlank()) {
                        try { mapDataRepository.findOrCreate(name, fileName, author, mode); }
                        catch (RuntimeException failure) { Log.err("[Maps] Could not initialize statistics for " + fileName, failure); }
                    }
                    return true;
                }).future());
        pendingUploads.put(fileName, write);
        write.whenComplete((saved, error) -> async.main(() -> {
            pendingUploads.remove(fileName, write);
            var token = installations.get(fileName);
            if (token != null && token.hash() == null && !activeUploads.containsKey(fileName)) installations.remove(fileName, token);
            if (error != null) Log.err("[Maps] Could not persist Discord metadata for " + fileName, error);
            mapService.refreshAttribution();
        }));
        return write;
    }

    /** Re-captures map identity after any engine maps.reload() (upload/remove paths). */
    public void onMapsReloaded() {
        mapService.rebuildIdentityCatalog();
    }

    public void registerListeners() {
        network.subscribe(MapsInspectRequestV1.class, this::inspect);
        // The map registry, the active ruleset and the engine's Map objects are all game
        // state, and NetworkService.subscribe invokes this on a redis-sub-* virtual thread.
        // The per-map repository lookups are MongoDB round trips, so only those run off
        // the game thread: the engine reads are snapshotted first and the protocol entries
        // are built last, both on the game thread.
        network.subscribe(MapsListRequestV1.class, request -> async.main(() -> {
            if (!request.server().equals(config.server.name)) return;

            String currentGameMode = state.rules.mode().name();
            var customMaps = maps.customMaps();
            List<MapIdentity> snapshot = new ArrayList<>(customMaps.size);
            for (int i = 0; i < customMaps.size; i++) {
                Map map = customMaps.get(i);
                snapshot.add(new MapIdentity(map, map.file.name(), map.plainName(), map.author(), mapService.discordUploadFor(map)));
            }

            async.supply(() -> {
                    List<MapData> persisted = new ArrayList<>(snapshot.size());
                    for (MapIdentity identity : snapshot) {
                        // One unreachable map must not cost the peer the whole list. A map
                        // with no persisted record already serialises as a null MapData, so
                        // a failed lookup produces the same answer a missing one does.
                        try {
                            MapData data = mapDataRepository
                                    .findByFileName(identity.fileName(), currentGameMode)
                                    .or(() -> mapDataRepository.find(identity.plainName(), identity.author(), currentGameMode))
                                    .orElse(null);
                            persisted.add(data);
                        } catch (RuntimeException lookupFailure) {
                            Log.err("[Maps] Lookup failed for map '@'; replying without its metadata",
                                    identity.plainName(), lookupFailure);
                            persisted.add(null);
                        }
                    }
                    return persisted;
                })
                .thenMain((persisted, error) -> {
                    // thenMain completes exceptionally on a storage failure or a dispatch
                    // failure, and nobody observes that future. Swallowing it here would
                    // leave the peer waiting out its timeout with no answer and nothing in
                    // the log, which is the failure mode this handler is supposed to avoid.
                    // Fall back to entries built from the engine snapshot alone.
                    if (error != null) {
                        Log.err("[Maps] Map list lookup failed; replying with engine data only", error);
                        persisted = java.util.Collections.nCopies(snapshot.size(), (MapData) null);
                    }
                    List<MapEntryV1> mapsList = new ArrayList<>(snapshot.size());
                    for (int i = 0; i < snapshot.size(); i++) {
                        MapIdentity identity = snapshot.get(i);
                        mapsList.add(MapsProtocolMapper.toMapEntry(identity.map(), currentGameMode,
                                persisted.get(i), identity.upload()));
                    }
                    network.respond(request,
                            MapsProtocolMapper.toMapsListResponse(request.server(), mapsList));
                });
        }));

        network.subscribe(MapsRemoveRequestV1.class, request -> async.main(() -> {
            if (!request.server().equals(config.server.name)) return;

            // findMapByFileName reads the engine map registry and removeMap/reload write
            // it, so the whole decision belongs on the game thread. There is no I/O here.
            var map = mapService.findMapByFileName(request.fileName());
            if (map != null) {
                String removedName = map.file.name();
                installations.put(removedName, new DownloadedMap(null, installationSequence.incrementAndGet()));
                enqueueDiscordUpload("", removedName, "", "", null, null,
                        () -> true); // Queue order, rather than a download token, defines this removal.
                mapService.clearAttribution();
                maps.removeMap(map);
                maps.reload();
                onMapsReloaded();
            }

            String result = map == null
                    ? "Map file not found"
                    : "Successfully removed map " + map.plainName() + " (" + map.file.name() + ")";
            network.respond(request, MapsProtocolMapper.toMapsRemoveResponse(request.server(), result));

            if (map != null) info("Removed map @", map.plainName());
        }));

        network.subscribe(MapsUploadRequestV1.class, request -> {
            if (!config.server.name.equals(request.server())) return;
            uploadFiles(request.files(), results -> network.respond(request, new MapsUploadResponseV1(request.server(), results)));
        });
        network.subscribe(MapsLoadCommandV1.class, command -> {
            if (!config.server.name.equals(command.server())) return;
            uploadFiles(command.files(), results -> info("Map upload results: @", results));
        });
    }

    /** Redis callbacks are workers; registry capture is the only game-thread step. */
    void inspect(MapsInspectRequestV1 request) {
        if (!config.server.name.equals(request.server())) return;
        if (inspectionsInFlight.incrementAndGet() > MAX_INSPECTIONS) {
            inspectionsInFlight.decrementAndGet();
            network.respondError(request, "REJECTED", "Map inspection is busy");
            return;
        }
        var started = new AtomicBoolean();
        async.supply(() -> {
            started.set(true);
            try {
                var captured = new CompletableFuture<MapInspectionService.Context>();
                async.main(() -> {
                    if (captured.isDone()) return;
                    try { captured.complete(inspection.capture()); }
                    catch (RuntimeException error) { captured.completeExceptionally(error); }
                });
                MapInspectionService.Context context;
                try { context = captured.get(5, TimeUnit.SECONDS); }
                catch (Exception error) {
                    captured.cancel(false);
                    if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                    network.respondError(request, "FAILED", "Map inspection registry could not be captured");
                    return null;
                }
                var result = inspection.inspect(request.file(), request.profile(), context);
                network.respond(request, MapsProtocolMapper.toInspectResponse(request.server(), result));
            } catch (RuntimeException error) {
                Log.warn("Map inspection failed: @", error.toString());
                try { network.respondError(request, "FAILED", "Map inspection failed"); }
                catch (RuntimeException publishError) { Log.warn("Could not publish map inspection error: @", publishError.toString()); }
            } finally { inspectionsInFlight.decrementAndGet(); }
            return null;
        }).future().whenComplete((ignored, error) -> {
            // A rejected task never entered the worker. This continuation stays on its Redis caller.
            if (error != null && !started.get()) {
                inspectionsInFlight.decrementAndGet();
                network.respondError(request, "REJECTED", "Map inspection storage is busy");
            }
        });
    }

    private void uploadFiles(List<MapFileSourceV1> files, Consumer<List<MapUploadResultV1>> reply) {
        var results = new ConcurrentHashMap<String, MapUploadResultV1>();
        List<MapFileSourceV1> accepted = new ArrayList<>();
        var names = new java.util.HashSet<String>();
        for (var file : files) {
            try {
                MapFileNameSanitizer.requireSafeName(file.fileName());
                if (names.add(file.fileName())) accepted.add(file);
                else results.put(file.fileName(), outcome(file.fileName(), MapUploadResultV1Status.REJECTED, "Duplicate filename"));
            } catch (IllegalArgumentException unsafe) {
                results.put(file.fileName(), outcome(file.fileName(), MapUploadResultV1Status.REJECTED, "Unsafe filename"));
            }
        }
        accepted.removeIf(file -> results.containsKey(file.fileName()));
        for (var file : accepted) activeUploads.compute(file.fileName(), (key, count) -> count == null ? 1 : count + 1);
        Runnable respond = () -> {
            var ordered = files.stream().map(file -> results.get(file.fileName())).toList();
            try { reply.accept(ordered); }
            finally {
                for (var file : accepted) activeUploads.compute(file.fileName(), (key, count) -> {
                    if (count == null || count <= 1) { installations.remove(key); return null; }
                    return count - 1;
                });
            }
        };
        if (accepted.isEmpty()) { async.main(respond); return; }
        var downloaded = new ConcurrentHashMap<String, DownloadedMap>();
        AtomicInteger completed = new AtomicInteger();
        Runnable finishIfDone = () -> {
            if (completed.incrementAndGet() != accepted.size()) return;
            async.main(() -> {
                CompletionStage<Void> catalogReady = CompletableFuture.completedFuture(null);
                if (!downloaded.isEmpty()) {
                    try {
                        maps.reload();
                        catalogReady = mapService.rebuildIdentityCatalogAsync();
                    } catch (RuntimeException reloadFailure) {
                        Log.err("[Maps] Map registry reload failed", reloadFailure);
                        for (var source : accepted) if (downloaded.containsKey(source.fileName()))
                            results.put(source.fileName(), outcome(source.fileName(), MapUploadResultV1Status.INVALID_MAP, "Map registry reload failed"));
                        respond.run();
                        return;
                    }
                    if (catalogReady == null) catalogReady = CompletableFuture.completedFuture(null);
                }
                final var ready = catalogReady;
                var writes = new ArrayList<CompletableFuture<?>>();
                String mode = state.rules.mode().name();
                for (var source : accepted) {
                    DownloadedMap download = downloaded.get(source.fileName());
                    if (download == null) continue;
                    if (!download.equals(installations.get(source.fileName()))) {
                        results.put(source.fileName(), outcome(source.fileName(), MapUploadResultV1Status.SUPERSEDED, "Replaced by a newer upload"));
                        continue;
                    }
                    Map installed = mapService.findMapByFileName(source.fileName());
                    if (installed == null || installed.file == null || !source.fileName().equals(installed.file.name())) {
                        results.put(source.fileName(), outcome(source.fileName(), MapUploadResultV1Status.INVALID_MAP, "The server could not read this map"));
                        continue;
                    }
                    var write = enqueueDiscordUpload(installed.plainName(), source.fileName(), installed.author(), mode,
                            download.hash(), MapDiscordUpload.fromProtocol(source.discordUpload()),
                            () -> download.equals(installations.get(source.fileName())))
                            .handle((saved, error) -> {
                                var status = !download.equals(installations.get(source.fileName())) ? MapUploadResultV1Status.SUPERSEDED
                                        : error != null || !Boolean.TRUE.equals(saved) ? MapUploadResultV1Status.METADATA_FAILED
                                        : MapUploadResultV1Status.INSTALLED;
                                results.put(source.fileName(), outcome(source.fileName(), status,
                                        status == MapUploadResultV1Status.METADATA_FAILED ? "Map installed, but attribution could not be saved" : null));
                                return (Void) null;
                            });
                    writes.add(write);
                }
                CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                        .thenCompose(ignored -> ready)
                        .thenCompose(ignored -> {
                            var refresh = mapService.refreshAttribution();
                            return refresh == null ? CompletableFuture.completedFuture(null) : refresh;
                        }).whenComplete((ignored, error) -> async.main(respond));
            });
        };
        for (var file : accepted) {
            var finished = new AtomicBoolean();
            Runnable finishOne = () -> { if (finished.compareAndSet(false, true)) finishIfDone.run(); };
            try {
                Http.get(file.url()).timeout(30000)
                    .error(error -> {
                        Log.err("Failed to download map @", file.fileName(), error);
                        results.put(file.fileName(), outcome(file.fileName(), MapUploadResultV1Status.DOWNLOAD_FAILED, "Download failed"));
                        finishOne.run();
                    }).submit(response -> {
                        try {
                            byte[] bytes = response.getResult();
                            String hash = MapContentHash.digest(new java.io.ByteArrayInputStream(bytes)).asHex();
                            // Fixed stripes bound memory and keep disk I/O outside ConcurrentHashMap.compute.
                            Object lock = fileLocks[(file.fileName().hashCode() & 0x7fffffff) % fileLocks.length];
                            synchronized (lock) {
                                customMapDirectory.child(file.fileName()).writeBytes(bytes, false);
                                var token = new DownloadedMap(hash, installationSequence.incrementAndGet());
                                installations.put(file.fileName(), token);
                                downloaded.put(file.fileName(), token);
                            }
                        } catch (Exception failure) {
                            Log.err("Failed to save map @", file.fileName(), failure);
                            results.put(file.fileName(), outcome(file.fileName(), MapUploadResultV1Status.DOWNLOAD_FAILED, "Download or file write failed"));
                        } finally { finishOne.run(); }
                    });
            } catch (RuntimeException requestFailure) {
                results.put(file.fileName(), outcome(file.fileName(), MapUploadResultV1Status.DOWNLOAD_FAILED, "Could not start download"));
                finishOne.run();
            }
        }
    }

    private static MapUploadResultV1 outcome(String fileName, MapUploadResultV1Status status, String detail) {
        return new MapUploadResultV1(fileName, status, detail);
    }
}
