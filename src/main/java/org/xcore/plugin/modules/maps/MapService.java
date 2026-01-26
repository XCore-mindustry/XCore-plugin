package org.xcore.plugin.modules.maps;

import arc.struct.Seq;
import jakarta.inject.Singleton;
import mindustry.maps.Map;
import org.xcore.plugin.utils.Utils;

import static mindustry.Vars.maps;

@Singleton
public class MapService {

    public Seq<Map> getAvailableMaps() {
        return maps.customMaps().isEmpty() ? maps.defaultMaps() : maps.customMaps();
    }

    public Map findMap(String name) {
        return Utils.findInSeq(name, getAvailableMaps(), map -> Utils.deepEquals(map.name(), name));
    }
}