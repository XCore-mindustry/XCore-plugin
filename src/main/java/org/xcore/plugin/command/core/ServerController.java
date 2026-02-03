package org.xcore.plugin.command.core;

public interface ServerController {
    /**
     * Priority for controller registration order.
     * Higher values are registered first, affecting command order.
     *
     * @return Priority value (default: 0)
     */
    default int priority() {
        return 0;
    }
}
