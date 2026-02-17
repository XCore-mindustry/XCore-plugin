package org.xcore.plugin.map;

import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.Maps.MapProvider;
import org.bson.types.ObjectId;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.EventService;

@Singleton
public class SmartMapSelector implements MapProvider {

    private final Seq<Map> recentMaps = new Seq<>();
    private static final int HISTORY_SIZE = 5;

    private final Config config;

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;

    private final EventService eventService;

    @Inject
    public SmartMapSelector(Config config, EventDataRepository eventDataRepository, MapDataRepository mapDataRepository, EventService eventService) {
        this.config = config;
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.eventService = eventService;
    }

    @Override
    public Map next(Gamemode mode, Map previous) {
        if (config.isEvent()) {
            eventService.checkTimedEvents();

            var activeEventOpt = eventDataRepository.findActive();
            if (activeEventOpt.isPresent()) {
                Map map = findMindustryMap(activeEventOpt.get().map);
                if (map != null) return map;
            }

            var nextEventOpt = eventDataRepository.findNextScheduled();
            if (nextEventOpt.isPresent()) {
                EventData nextEvent = nextEventOpt.get();
                eventDataRepository.activateEvent(nextEvent);

                Map map = findMindustryMap(nextEvent.map);
                if (map != null) return map;
            }

            if (config.isEventHubMap && config.eventHubMapID != null && !config.eventHubMapID.isEmpty()) {
                if (ObjectId.isValid(config.eventHubMapID)) {
                    Map map = findMindustryMap(new ObjectId(config.eventHubMapID));
                    if (map != null) return map;
                } else {
                    Log.err("Invalid eventHubMapID");
                }
            }
        }

        if (previous != null) {
            recentMaps.add(previous);
            if (recentMaps.size > HISTORY_SIZE) {
                recentMaps.remove(0);
            }
        }

        Seq<Map> candidates = Vars.maps.customMaps().select(m -> !recentMaps.contains(m));

        if (candidates.isEmpty()) candidates = Vars.maps.customMaps();
        if (candidates.isEmpty()) return null;

        ObjectMap<String, MapData> statsMap = mapDataRepository.findAllAsMap();

        arc.func.Func<Map, MapData> getStat = map -> {
            String key = MapDataRepository.genKey(map.plainName(), map.author(), mode.name());
            return statsMap.get(key, new MapData(map.plainName(), map.file.name(), map.author(), mode.name()));
        };

        Seq<Map> poolA = candidates.copy().sort(m -> {
            MapData s = getStat.get(m);
            return (float) -((double)s.reputation - s.popularity);
        });
        poolA.truncate(Math.min(poolA.size, 10));

        Seq<Map> poolB = candidates.copy().sort(m -> {
            MapData s = getStat.get(m);
            return (float) -s.interest;
        });
        poolB.truncate(Math.min(poolB.size, 10));

        Seq<Map> poolC = candidates.copy().sort(m -> {
            MapData s = getStat.get(m);
            return s.playedTimesYear;
        });
        poolC.truncate(Math.min(poolC.size, 5));

        Seq<Map> poolD = candidates;

        float chance = Mathf.random(1f);
        Map selectedMap;

        if (chance < 0.4f && !poolA.isEmpty()) {
            selectedMap = poolA.random();
        } else if (chance < 0.7f && !poolB.isEmpty()) {
            selectedMap = poolB.random();
        } else if (chance < 0.9f && !poolC.isEmpty()) {
            selectedMap = poolC.random();
        } else {
            selectedMap = poolD.random();
        }

        return selectedMap;
    }

    private Map findMindustryMap(ObjectId mapId) {
        if (mapId == null) return null;

        MapData data = mapDataRepository.findById(mapId);
        if (data == null) return null;

        return Vars.maps.all().find(m ->
                m.plainName().equals(data.name) &&
                m.author().equals(data.author)
        );
    }
}
