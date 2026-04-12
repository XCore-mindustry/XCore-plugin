package org.xcore.plugin.event.socket;

import arc.util.Http;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.maps.Map;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.NetworkService;

import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.state;

import static mindustry.Vars.customMapDirectory;
import static mindustry.Vars.maps;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class MapSocketHandler {

    private final NetworkService network;
    private final Config config;
    private final MapService mapService;
    private final MapDataRepository mapDataRepository;

    @Inject
    public MapSocketHandler(NetworkService network,
                            Config config,
                            MapService mapService,
                            MapDataRepository mapDataRepository) {
        this.network = network;
        this.config = config;
        this.mapService = mapService;
        this.mapDataRepository = mapDataRepository;
    }

    public void registerListeners() {
        network.subscribe(SocketEvents.MapsListRequest.class, request -> {
            if (!request.server.equals(config.server)) return;

            var customMaps = maps.customMaps();
            String currentGameMode = state.rules.mode().name();
            var mapsList = new SocketEvents.MapEntry[customMaps.size];
            for (int i = 0; i < customMaps.size; i++) {
                Map map = customMaps.get(i);
                String fileName = map.file == null ? "" : map.file.name();
                String rawAuthor = map.author();
                String author = rawAuthor == null ? "Unknown" : rawAuthor;
                MapData persistedMap = mapDataRepository.find(map.plainName(), rawAuthor, currentGameMode)
                        .orElse(null);
                SocketEvents.MapEntry entry = new SocketEvents.MapEntry();
                entry.name = map.plainName();
                entry.fileName = fileName;
                entry.author = author;
                entry.width = map.width;
                entry.height = map.height;
                entry.fileSizeBytes = map.file == null ? null : map.file.length();
                entry.like = persistedMap == null ? null : persistedMap.like;
                entry.dislike = persistedMap == null ? null : persistedMap.dislike;
                entry.reputation = persistedMap == null ? null : persistedMap.reputation;
                entry.popularity = persistedMap == null ? null : persistedMap.popularity;
                entry.interest = persistedMap == null ? null : persistedMap.interest;
                entry.gameMode = persistedMap == null ? currentGameMode : persistedMap.gameMode;
                mapsList[i] = entry;
            }

            SocketEvents.MapsListResponse response = new SocketEvents.MapsListResponse();
            response.maps = mapsList;
            network.respond(request, response);
        });

        network.subscribe(SocketEvents.MapRemoveRequest.class, request -> {
            if (!request.server.equals(config.server)) return;

            var map = mapService.findMapByFileName(request.fileName);
            if (map != null) {
                maps.removeMap(map);
                maps.reload();
            }

            SocketEvents.MapRemoveResponse response = new SocketEvents.MapRemoveResponse();
            response.result = map == null
                    ? "Map file not found"
                    : "Successfully removed map " + map.plainName() + " (" + map.file.name() + ")";
            network.respond(request, response);

            if (map != null) info("Removed map @", map.plainName());
        });

        network.subscribe(SocketEvents.LoadMapsV2.class, e -> {
            if (!config.server.equals(e.server())) return;

            AtomicInteger counter = new AtomicInteger();
            for (SocketEvents.FileURL file : e.urls()) {
                Http.get(file.url())
                        .error(Log::err)
                        .submit(result -> {
                            customMapDirectory.child(file.filename()).writeBytes(result.getResult());

                            if (counter.incrementAndGet() == e.urls().length) {
                                maps.reload();
                                info("Loaded @ maps.", e.urls().length);
                            }
                        });
            }
        });
    }

}
