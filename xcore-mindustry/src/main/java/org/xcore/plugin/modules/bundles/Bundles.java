package org.xcore.plugin.modules.bundles;

import org.xcore.plugin.XcorePlugin;

import static org.xcore.plugin.PluginVars.bundle;

public class Bundles {
    public static void init() {
        bundle.addSource(XcorePlugin.class);
        bundle.setDefaultValueFactory(new DVByDLocaleFactory());
    }
}
