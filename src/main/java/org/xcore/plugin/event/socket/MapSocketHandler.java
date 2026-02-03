package org.xcore.plugin.event.socket;

import arc.util.Http;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.maps.Map;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.NetworkService;

import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.customMapDirectory;
import static mindustry.Vars.maps;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class MapSocketHandler {

    private final NetworkService network;
    private final Config config;
    private final MapService mapService;

    @Inject
    public MapSocketHandler(NetworkService network,
                            Config config,
                            MapService mapService) {
        this.network = network;
        this.config = config;
        this.mapService = mapService;
    }

    public void registerListeners() {
        network.subscribe(SocketEvents.MapsListRequest.class, request -> {
            if (!request.server.equals(config.server)) return;
            network.respond(request, new SocketEvents.MapsListResponse(
                    maps.customMaps().map(Map::plainName).toArray(String.class)));
        });

        network.subscribe(SocketEvents.MapRemoveRequest.class, request -> {
            if (!request.server.equals(config.server)) return;

            var map = mapService.findMap(request.map);
            if (map != null) {
                maps.removeMap(map);
                maps.reload();
            }

            network.respond(request, new SocketEvents.MapRemoveResponse(
                    map == null ? "Map not found" : "Successfully removed map " + map.plainName()));

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
