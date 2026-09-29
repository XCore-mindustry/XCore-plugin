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
    private record MapIdentity(Map map, String plainName, String author) {
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
                snapshot.add(new MapIdentity(map, map.plainName(), map.author()));
            }

            async.supply(() -> {
                    List<MapData> persisted = new ArrayList<>(snapshot.size());
                    for (MapIdentity identity : snapshot) {
                        persisted.add(mapDataRepository
                                .find(identity.plainName(), identity.author(), currentGameMode)
                                .orElse(null));
                    }
                    return persisted;
                })
                .thenMain(persisted -> {
                    List<MapEntryV1> mapsList = new ArrayList<>(snapshot.size());
                    for (int i = 0; i < snapshot.size(); i++) {
                        MapIdentity identity = snapshot.get(i);
                        mapsList.add(MapsProtocolMapper.toMapEntry(
                                identity.map(), currentGameMode, persisted.get(i)));
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
            for (MapFileSourceV1 file : e.files()) {
                try {
                    MapFileNameSanitizer.requireSafeName(file.fileName());
                    accepted.add(file);
                } catch (IllegalArgumentException ex) {
                    err("[Maps] Rejected unsafe map file name '@' from @", file.fileName(), e.server());
                }
            }

            if (accepted.isEmpty()) {
                err("[Maps] No usable map files in load command from @", e.server());
                return;
            }

            AtomicInteger counter = new AtomicInteger();
            for (MapFileSourceV1 file : accepted) {
                String safeName = MapFileNameSanitizer.requireSafeName(file.fileName());
                Http.get(file.url())
                        .error(Log::err)
                        .submit(result -> {
                            // The download and the file write are I/O and belong off the game
                            // thread. The registry reload does not: maps.reload() rebuilds the
                            // engine's map index, and the counter fires on whichever Arc HTTP
                            // worker finished last, which is never the game thread.
                            customMapDirectory.child(safeName).writeBytes(result.getResult());

                            if (counter.incrementAndGet() == accepted.size()) {
                                async.main(() -> {
                                    maps.reload();
                                    onMapsReloaded();
                                    info("Loaded @ maps.", accepted.size());
                                });
                            }
                        });
            }
        });
    }

}
