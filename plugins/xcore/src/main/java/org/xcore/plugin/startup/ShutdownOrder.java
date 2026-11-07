package org.xcore.plugin.startup;

/** Shutdown phases shared by the root scope and its plugins. Lower values run first. */
public final class ShutdownOrder {
    public static final int CHILD_SCOPES = 100;
    public static final int STOP_PRODUCERS = 200;
    /** Writes already accepted (a match waiting for a retry) finish while storage is still open. */
    public static final int DRAIN_WRITES = 400;
    public static final int DRAIN_STORAGE = 500;

    private ShutdownOrder() {
    }
}
