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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final java.util.concurrent.ConcurrentHashMap<String, DownloadedMap> installations = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong installationSequence = new java.util.concurrent.atomic.AtomicLong();
    /** Accessed only on the game thread; storage writes for one file never overtake one another. */
    private final java.util.Map<String, java.util.concurrent.CompletableFuture<Void>> pendingUploads = new java.util.HashMap<>();

    @Inject
    public MapTransportHandler(NetworkService network,
                               TomlXcoreConfig config,
                               MapService mapService,
                               MapDataRepository mapDataRepository,
                               Async async) {
        this.network = network;
        this.config = config;
        this.mapService = mapService;
        this.mapDataRepository = mapDataRepository;
        this.async = async;
    }

    /** A map plus the identity fields read from the engine, captured on the game thread. */
    private record MapIdentity(Map map, String fileName, String plainName, String author) {
    }

    private record MapMetadata(MapData data, MapDiscordUpload upload) {}
    private record DownloadedMap(String hash, long sequence) {}

    java.util.concurrent.CompletableFuture<Void> enqueueDiscordUpload(
            String name, String fileName, String author, String mode, String hash, MapDiscordUpload upload,
            java.util.function.BooleanSupplier stillInstalled) {
        var previous = pendingUploads.getOrDefault(fileName, java.util.concurrent.CompletableFuture.completedFuture(null));
        var write = previous.handle((ignored, error) -> null).thenCompose(ignored ->
                async.supply(() -> !stillInstalled.getAsBoolean()
                        || mapDataRepository.recordDiscordUpload(name, fileName, author, mode, hash, upload))
                        .thenMain((saved, failure) -> {
                            if (failure != null) Log.err("[Maps] Could not persist Discord metadata for " + fileName, failure);
                            else if (!Boolean.TRUE.equals(saved)) Log.warn("[Maps] Discord metadata was not persisted for @", fileName);
                        }));
        pendingUploads.put(fileName, write);
        write.whenComplete((ignored, error) -> async.main(() -> pendingUploads.remove(fileName, write)));
        return write;
    }

    /** Re-captures map identity after any engine maps.reload() (upload/remove paths). */
    public void onMapsReloaded() {
        mapService.rebuildIdentityCatalog();
    }

    public void registerListeners() {
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
                snapshot.add(new MapIdentity(map, map.file.name(), map.plainName(), map.author()));
            }

            async.supply(() -> {
                    List<MapMetadata> persisted = new ArrayList<>(snapshot.size());
                    for (MapIdentity identity : snapshot) {
                        // One unreachable map must not cost the peer the whole list. A map
                        // with no persisted record already serialises as a null MapData, so
                        // a failed lookup produces the same answer a missing one does.
                        try {
                            MapData data = mapDataRepository
                                    .findByFileName(identity.fileName(), currentGameMode)
                                    .or(() -> mapDataRepository.find(identity.plainName(), identity.author(), currentGameMode))
                                    .orElse(null);
                            MapDiscordUpload upload = data != null && identity.fileName().equals(data.fileName)
                                    ? data.discordUpload : null;
                            if (upload == null) upload = mapDataRepository.findDiscordUpload(identity.fileName());
                            persisted.add(new MapMetadata(data, upload));
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
                        persisted = java.util.Collections.nCopies(snapshot.size(), (MapMetadata) null);
                    }
                    List<MapEntryV1> mapsList = new ArrayList<>(snapshot.size());
                    for (int i = 0; i < snapshot.size(); i++) {
                        MapIdentity identity = snapshot.get(i);
                        MapMetadata metadata = persisted.get(i);
                        mapsList.add(MapsProtocolMapper.toMapEntry(identity.map(), currentGameMode,
                                metadata == null ? null : metadata.data(), metadata == null ? null : metadata.upload()));
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

        network.subscribe(MapsLoadCommandV1.class, e -> {
            if (!config.server.name.equals(e.server())) return;

            // The peer chooses the name, and Fi.child() does not stop "../" from escaping
            // customMapDirectory. Validate before the name reaches the filesystem.
            List<MapFileSourceV1> accepted = new ArrayList<>();
            var acceptedNames = new java.util.HashSet<String>();
            for (MapFileSourceV1 file : e.files()) {
                try {
                    MapFileNameSanitizer.requireSafeName(file.fileName());
                    if (acceptedNames.add(file.fileName())) accepted.add(file);
                } catch (IllegalArgumentException ex) {
                    err("[Maps] Rejected unsafe map file name '@' from @", file.fileName(), e.server());
                }
            }

            if (accepted.isEmpty()) {
                err("[Maps] No usable map files in load command from @", e.server());
                return;
            }

            var downloaded = new java.util.concurrent.ConcurrentHashMap<String, DownloadedMap>();
            AtomicInteger completed = new AtomicInteger();
            AtomicInteger written = new AtomicInteger();
            // Both the success and the error handler have to count, or a single 404 leaves
            // the counter short and the reload never happens: the maps that did download
            // would sit on disk unindexed until some later command or a restart.
            Runnable finishIfDone = () -> {
                if (completed.incrementAndGet() != accepted.size()) {
                    return;
                }
                int loaded = written.get();
                if (loaded == 0) {
                    return;
                }
                async.main(() -> {
                    maps.reload();
                    onMapsReloaded();
                    String gameMode = state.rules.mode().name();
                    for (MapFileSourceV1 source : accepted) {
                        DownloadedMap download = downloaded.get(source.fileName());
                        if (download == null || !download.equals(installations.get(source.fileName()))) continue;
                        Map installed = mapService.findMapByFileName(source.fileName());
                        if (installed == null || installed.file == null || !source.fileName().equals(installed.file.name())) continue;
                        // Snapshot engine fields; the installation guard is safe to check on the worker.
                        String name = installed.plainName();
                        String author = installed.author();
                        MapDiscordUpload upload = MapDiscordUpload.fromProtocol(source.discordUpload());
                        enqueueDiscordUpload(name, source.fileName(), author, gameMode, download.hash(), upload,
                                () -> download.equals(installations.get(source.fileName())));
                    }
                    if (loaded == accepted.size()) {
                        info("Loaded @ maps.", loaded);
                    } else {
                        info("Loaded @ of @ maps; the rest failed to download.", loaded, accepted.size());
                    }
                });
            };

            for (MapFileSourceV1 file : accepted) {
                String safeName = MapFileNameSanitizer.requireSafeName(file.fileName());
                Http.get(file.url())
                        .error(error -> {
                            Log.err("Failed to download map @", file.fileName(), error);
                            finishIfDone.run();
                        })
                        .submit(result -> {
                            // The download and the file write are I/O and belong off the game
                            // thread. The registry reload does not: maps.reload() rebuilds the
                            // engine's map index, and the counter fires on whichever Arc HTTP
                            // worker finished last, which is never the game thread.
                            try {
                                byte[] bytes = result.getResult();
                                String hash = MapContentHash.digest(new java.io.ByteArrayInputStream(bytes)).asHex();
                                // Serialize writes of the same physical file on HTTP workers. The token
                                // also rejects late metadata from an older batch, even for identical bytes.
                                installations.compute(safeName, (key, previous) -> {
                                    customMapDirectory.child(safeName).writeBytes(bytes);
                                    var download = new DownloadedMap(hash, installationSequence.incrementAndGet());
                                    downloaded.put(safeName, download);
                                    return download;
                                });
                                written.incrementAndGet();
                            } catch (Exception failure) {
                                Log.err("Failed to save map @", file.fileName(), failure);
                            } finally {
                                finishIfDone.run();
                            }
                        });
            }
        });
    }

}
