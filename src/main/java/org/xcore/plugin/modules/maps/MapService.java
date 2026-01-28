package org.xcore.plugin.modules.maps;

import arc.func.Boolf;
import arc.struct.Seq;
import arc.util.Strings;
import jakarta.inject.Singleton;
import mindustry.maps.Map;
import org.xcore.plugin.utils.TextUtils;

import static mindustry.Vars.maps;

@Singleton
public class MapService {
    public Seq<Map> getAvailableMaps() {
        return maps.customMaps().isEmpty() ? maps.defaultMaps() : maps.customMaps();
    }

    public Map findMap(String nameOrIndex) {
        Seq<Map> available = getAvailableMaps();

        int index = Strings.parseInt(nameOrIndex, -1) - 1;
        if (index >= 0 && index < available.size) {
            return available.get(index);
        }

        return available.find(map -> TextUtils.deepEquals(map.name(), nameOrIndex));
    }

    public <T> T findInSeq(String nameOrIndex, Seq<T> values, Boolf<T> filter) {
        int index = Strings.parseInt(nameOrIndex, -1) - 1;
        if (index >= 0 && index < values.size) {
            return values.get(index);
        }
        return values.find(filter);
    }
}
