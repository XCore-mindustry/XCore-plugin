package org.xcore.plugin.command.core;

import arc.util.CommandHandler;

public interface ClientController {
    /**
     * Optional initialization logic called before controller registration.
     * Use this for setup tasks like menu registration or handler configuration.
     *
     * @param handler The command handler for this controller
     */
    default void setup(CommandHandler handler) {
    }

    /**
     * Priority for controller registration order.
     * Higher values are registered first, affecting command order in /help.
     *
     * @return Priority value (default: 0)
     */
    default int priority() {
        return 0;
    }
}
