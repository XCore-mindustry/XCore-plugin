package org.xcore.plugin.map;

import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.Maps.MapProvider;
import org.xcore.plugin.database.DatabaseService;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;

@Singleton
public class SmartMapSelector implements MapProvider {

    private final Seq<Map> recentMaps = new Seq<>();
    private static final int HISTORY_SIZE = 5;

    private final DatabaseService database;

    @Inject
    public SmartMapSelector(DatabaseService database) {
        this.database = database;
    }

    @Override
    public Map next(Gamemode mode, Map previous) {
        if (previous != null) {
            recentMaps.add(previous);
            if (recentMaps.size > HISTORY_SIZE) {
                recentMaps.remove(0);
            }
        }

        Seq<Map> candidates = Vars.maps.customMaps().select(m -> !recentMaps.contains(m));

        if (candidates.isEmpty()) candidates = Vars.maps.customMaps();
        if (candidates.isEmpty()) return null;

        ObjectMap<String, MapData> statsMap = database.getMapDataRepository().findAllAsMap();

        arc.func.Func<Map, MapData> getStat = map -> {
            String key = MapDataRepository.genKey(map.plainName(), map.author(), mode.name());
            return statsMap.get(key, new MapData(map.plainName(), map.author(), mode.name()));
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
}
