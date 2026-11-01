package org.xcore.plugin.startup;

/** Shutdown phases shared by the root scope and its plugins. Lower values run first. */
public final class ShutdownOrder {
    public static final int CHILD_SCOPES = 100;
    public static final int STOP_PRODUCERS = 200;
    public static final int DRAIN_STORAGE = 500;

    private ShutdownOrder() {
    }
}
