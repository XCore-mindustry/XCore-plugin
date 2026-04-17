package org.xcore.plugin.startup;

import arc.util.Reflect;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import org.xcore.plugin.map.SmartMapSelector;

@Singleton
public class MapSelectorInstaller {

    private final SmartMapSelector mapSelector;

    @Inject
    public MapSelectorInstaller(SmartMapSelector mapSelector) {
        this.mapSelector = mapSelector;
    }

    public void install() {
        Reflect.set(Vars.maps, "shuffler", mapSelector);
    }
}
