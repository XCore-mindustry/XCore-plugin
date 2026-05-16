package org.xcore.plugin;

import io.avaje.inject.BeanScope;
import mindustry.mod.Plugin;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.startup.PluginStartupCoordinator;

public class XcorePlugin extends Plugin {
    public static BeanScope container; // for dependend plugins

    @Override
    public void init() {
        container = BeanScope.builder()
                .classLoader(getClass().getClassLoader())
                .build();

        var startupCoordinator = container.get(PluginStartupCoordinator.class);
        if (!startupCoordinator.start()) {
            PLog.err("CRITICAL: Database migrations failed! Plugin initialization stopped.");
            return;
        }

        PLog.info("Plugin initialized.");
    }
}
