package org.xcore.plugin.event.transport;

import arc.util.Http;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.maps.Map;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1;
import org.xcore.protocol.generated.shared.MapEntryV1;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.MapsProtocolMapper;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.customMapDirectory;
import static mindustry.Vars.maps;
import static mindustry.Vars.state;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class MapTransportHandler {

    private final NetworkService network;
    private final Config config;
    private final MapService mapService;
    private final MapDataRepository mapDataRepository;

    @Inject
    public MapTransportHandler(NetworkService network,
                               Config config,
                               MapService mapService,
                               MapDataRepository mapDataRepository) {
        this.network = network;
        this.config = config;
        this.mapService = mapService;
        this.mapDataRepository = mapDataRepository;
    }

    public void registerListeners() {
        network.subscribe(MapsListRequestV1.class, request -> {
            if (!request.server().equals(config.server)) return;

            var customMaps = maps.customMaps();
            String currentGameMode = state.rules.mode().name();
            var mapsList = new ArrayList<MapEntryV1>(customMaps.size);
            for (int i = 0; i < customMaps.size; i++) {
                Map map = customMaps.get(i);
                MapData persistedMap = mapDataRepository.find(map.plainName(), map.author(), currentGameMode)
                        .orElse(null);
                mapsList.add(MapsProtocolMapper.toMapEntry(map, currentGameMode, persistedMap));
            }

            network.respond(request, MapsProtocolMapper.toMapsListResponse(request.server(), mapsList));
        });

        network.subscribe(MapsRemoveRequestV1.class, request -> {
            if (!request.server().equals(config.server)) return;

            var map = mapService.findMapByFileName(request.fileName());
            if (map != null) {
                maps.removeMap(map);
                maps.reload();
            }

            String result = map == null
                    ? "Map file not found"
                    : "Successfully removed map " + map.plainName() + " (" + map.file.name() + ")";
            network.respond(request, MapsProtocolMapper.toMapsRemoveResponse(request.server(), result));

            if (map != null) info("Removed map @", map.plainName());
        });

        network.subscribe(TransportEvents.LoadMapsV2.class, e -> {
            if (!config.server.equals(e.server())) return;

            AtomicInteger counter = new AtomicInteger();
            for (TransportEvents.FileURL file : e.urls()) {
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
