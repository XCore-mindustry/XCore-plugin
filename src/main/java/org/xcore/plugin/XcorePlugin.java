package org.xcore.plugin;

import arc.Events;
import io.avaje.inject.BeanScope;
import mindustry.game.EventType;
import mindustry.mod.Plugin;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.startup.PluginStartupCoordinator;

import java.util.concurrent.atomic.AtomicBoolean;

public class XcorePlugin extends Plugin {
    public static BeanScope container; // for dependent plugins

    private static final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public void init() {
        closed.set(false);
        container = BeanScope.builder()
                .classLoader(getClass().getClassLoader())
                .build();

        var startupCoordinator = container.get(PluginStartupCoordinator.class);
        if (!startupCoordinator.start()) {
            PLog.err("CRITICAL: Database migrations failed! Plugin initialization stopped.");
            teardown();
            return;
        }

        // Hook Mindustry's DisposeEvent for client/desktop host environments where Renderer disposes
        Events.on(EventType.DisposeEvent.class, e -> teardown());

        // Unconditional JVM shutdown hook for headless server teardown (SIGINT, SIGTERM, normal exit)
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(XcorePlugin::teardown, "xcore-shutdown-hook"));
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
            teardown();
            return;
        }

        PLog.info("Plugin initialized.");
    }

    /**
     * Idempotently closes the Avaje BeanScope, triggering all {@link io.avaje.inject.PreDestroy}
     * hooks on registered singletons (closing Mongo/Lettuce connections, draining thread pools).
     */
    public static void teardown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            if (container != null) {
                container.close();
                PLog.info("Plugin scope closed successfully.");
            }
        } catch (Throwable t) {
            PLog.err("Error during plugin shutdown", t);
        } finally {
            container = null;
        }
    }

    static void resetForTesting() {
        closed.set(false);
        container = null;
    }
}

