package org.xcore.plugin.config;

import java.util.Objects;

/**
 * Renders the server-local runtime config as a TOML-shaped view for
 * operator-facing inspection commands such as {@code xconfig}.
 */
public final class ServerLocalConfigTomlRenderer {

    public String render(TomlXcoreConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        config.normalize();
        return HumanReadableTomlWriter.write(config);
    }
}
