package org.xcore.plugin.common;

import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import mindustry.Vars;
import org.xcore.plugin.XcorePlugin;

@Singleton
public class BuildInfo {
    @Getter
    @Setter
    private String version = "Unknown";

    @PostConstruct
    public void init() {
        try {
            var myMod = Vars.mods.getMod(XcorePlugin.class);
            if (myMod != null && myMod.meta != null) {
                this.version = myMod.meta.version;
            }
        } catch (Exception e) {
            Log.err("Failed to load plugin version", e);
        }
    }
}
