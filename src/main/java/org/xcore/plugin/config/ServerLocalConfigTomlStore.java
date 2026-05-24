package org.xcore.plugin.config;

import arc.files.Fi;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Focused helper that persists the server-local {@link Config} runtime model
 * back to {@code xcore.toml}.
 *
 * <p>This helper keeps TOML write logic out of controllers and does not
 * touch global/secrets persistence. It uses the existing
 * {@link ConfigTomlMapper#toTomlXcoreConfig(Config)} mapping so that
 * written values match the startup TOML schema.</p>
 */
public final class ServerLocalConfigTomlStore {
    private final Fi tomlFile;

    public ServerLocalConfigTomlStore(Fi tomlFile) {
        this.tomlFile = Objects.requireNonNull(tomlFile, "tomlFile must not be null");
    }

    /**
     * Writes the given {@link Config} to the configured {@code xcore.toml} file.
     *
     * <p>The config is normalized and mapped to {@link TomlXcoreConfig} before
     * writing so the output matches the startup TOML schema.</p>
     *
     * @param config the runtime config to persist; must not be {@code null}
     * @throws NullPointerException if {@code config} is {@code null}
     * @throws IllegalStateException if writing fails
     */
    public void write(Config config) {
        Objects.requireNonNull(config, "config must not be null");

        TomlXcoreConfig toml = ConfigTomlMapper.toTomlXcoreConfig(config);
        writeToml(tomlFile, toml);
    }

    /**
     * Returns the target TOML file handle.
     *
     * @return the {@code xcore.toml} file this store writes to
     */
    public Fi file() {
        return tomlFile;
    }

    private static void writeToml(Fi file, Object config) {
        try {
            java.io.File javaFile = file.file();
            if (javaFile != null) {
                var parent = javaFile.toPath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            }
            String tomlString = createTomlMapper().writeValueAsString(config);
            file.writeString(tomlString);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to write TOML to " + file.absolutePath() + ": " + e.getMessage(), e);
        }
    }

    private static TomlMapper createTomlMapper() {
        return TomlMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }
}
