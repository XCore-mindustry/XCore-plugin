package org.xcore.plugin.command.transport;

import org.xcore.plugin.config.Config;

public enum TransportMode {
    SOCK,
    DUAL,
    REDIS;

    public Config.TransportType toConfigType() {
        return switch (this) {
            case SOCK -> Config.TransportType.SOCK;
            case DUAL -> Config.TransportType.DUAL;
            case REDIS -> Config.TransportType.REDIS;
        };
    }
}
