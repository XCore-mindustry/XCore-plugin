package org.xcore.plugin;

import io.avaje.inject.BeanScope;
import mindustry.game.EventType;
import mindustry.mod.Plugin;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.event.EventSubscriptions;
import org.xcore.plugin.startup.PluginStartupCoordinator;

import java.util.concurrent.atomic.AtomicBoolean;

public class XcorePlugin extends Plugin {
    public static BeanScope container; // for dependent plugins

    private static final AtomicBoolean closed = new AtomicBoolean(false);
    private static EventSubscriptions lifecycleEvents;
    private static Thread shutdownHook;

    @Override
    public void init() {
        if (container != null) return;
        closed.set(false);
        try {
            container = createScope();
            if (!container.get(PluginStartupCoordinator.class).start()) {
                PLog.err("CRITICAL: Database migrations failed! Plugin initialization stopped.");
                teardown();
                return;
            }

            lifecycleEvents = new EventSubscriptions();
            lifecycleEvents.on(EventType.DisposeEvent.class, e -> teardown());
            shutdownHook = new Thread(XcorePlugin::teardown, "xcore-shutdown-hook");
            try {
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                teardown();
                return;
            }
            PLog.info("Plugin initialized.");
        } catch (RuntimeException | Error failure) {
            teardown();
            throw failure;
        }
    }

    protected BeanScope createScope() {
        return BeanScope.builder()
                .classLoader(getClass().getClassLoader())
                .build();
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
            if (lifecycleEvents != null) lifecycleEvents.close();
            if (shutdownHook != null && shutdownHook != Thread.currentThread()) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // The JVM is already shutting down.
                }
            }
        } catch (Throwable t) {
            PLog.err("Error removing plugin lifecycle hooks", t);
        }

        try {
            if (container != null) {
                container.close();
                PLog.info("Plugin scope closed successfully.");
            }
        } catch (Throwable t) {
            PLog.err("Error during plugin shutdown", t);
        } finally {
            GameThread.setViolationListener(null);
            lifecycleEvents = null;
            shutdownHook = null;
            container = null;
        }
    }

    static void resetForTesting() {
        closed.set(false);
        container = null;
    }
}

