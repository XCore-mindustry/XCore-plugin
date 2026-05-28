package org.xcore.plugin.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.util.Objects;

/**
 * Renders the server-local runtime config as a TOML-shaped view for
 * operator-facing inspection commands such as {@code xconfig}.
 */
public final class ServerLocalConfigTomlRenderer {

    public String render(TomlXcoreConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        config.normalize();
        try {
            return createTomlMapper().writeValueAsString(config).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render server-local config as TOML: " + e.getMessage(), e);
        }
    }

    private static TomlMapper createTomlMapper() {
        return TomlMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }
}
