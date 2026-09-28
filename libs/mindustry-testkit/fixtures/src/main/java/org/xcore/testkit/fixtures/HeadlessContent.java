package org.xcore.testkit.fixtures;

import arc.Core;
import arc.Settings;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.ContentLoader;

/**
 * Thread-safe, cached base content initializer.
 * Ensures base vanilla content (Blocks, Items, Liquids, Units, Planets) is loaded once per JVM process
 * to achieve sub-millisecond headless test setup.
 */
public final class HeadlessContent {
    private static volatile boolean loaded = false;
    private static final Object LOCK = new Object();

    public static void ensureLoaded() {
        if (loaded && Blocks.air != null) return;
        synchronized (LOCK) {
            if (loaded && Blocks.air != null) return;

            if (Core.settings == null) {
                Core.settings = new Settings();
            }

            Vars.headless = true;
            if (Vars.content == null) {
                Vars.content = new ContentLoader();
            }
            if (Blocks.air == null) {
                Vars.content.createBaseContent();
                Vars.content.init();
            }

            loaded = true;
        }
    }

    private HeadlessContent() {}
}
