package org.xcore.plugin.config;

import arc.files.Fi;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Focused helper that persists the server-local TOML runtime owner back to
 * {@code xcore.toml}.
 *
 * <p>This helper keeps TOML write logic out of controllers and does not touch
 * global/secrets persistence. {@link TomlXcoreConfig} is the direct helper
 * boundary.</p>
 */
public final class ServerLocalConfigTomlStore {
    private final Fi tomlFile;

    public ServerLocalConfigTomlStore(Fi tomlFile) {
        this.tomlFile = Objects.requireNonNull(tomlFile, "tomlFile must not be null");
    }

    /**
     * Writes the given {@link TomlXcoreConfig} to the configured
     * {@code xcore.toml} file.
     *
     * <p>The config is normalized before writing so the output matches the
     * startup TOML schema.</p>
     *
     * @param config the structured runtime config to persist; must not be {@code null}
     * @throws NullPointerException if {@code config} is {@code null}
     * @throws IllegalStateException if writing fails
     */
    public void write(TomlXcoreConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        config.normalize();
        writeToml(tomlFile, config);
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
            String tomlString = HumanReadableTomlWriter.write(config);
            file.writeString(tomlString);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to write TOML to " + file.absolutePath() + ": " + e.getMessage(), e);
        }
    }
}
