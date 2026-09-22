package org.xcore.plugin.config;

import arc.files.Fi;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Helper for locating, loading, and defaulting TOML configuration files.
 *
 * <p>Resolution order for each config:</p>
 * <ol>
 *   <li>If the TOML file exists, load it via Jackson TOML.</li>
 *   <li>Else write a commented default TOML template, then load it.</li>
 * </ol>
 *
 * <p>This class is stateless and performs no logging. Callers are responsible
 * for reporting the {@link Source} to startup logs.</p>
 */
public final class ConfigTomlLoader {
    private static final String XCORE_TOML = "xcore.toml";
    private static final String SECRETS_TOML = "secrets.toml";

    private ConfigTomlLoader() {
    }

    /**
     * Identifies which source was used to produce a configuration object.
     */
    public enum Source {
        /** Loaded from an existing {@code xcore.toml} or {@code secrets.toml}. */
        TOML,
        /** Created from a default template because no config file existed. */
        DEFAULT_TEMPLATE
    }

    /**
     * Result of a config load operation, carrying the resolved object, the
     * source that was used, and the file that was read or created.
     *
     * @param <T> the configuration type, such as {@link TomlXcoreConfig} or {@link TomlSecretsConfig}
     */
    public static final class LoadResult<T> {
        public final T config;
        public final Source source;
        public final Fi file;

        LoadResult(T config, Source source, Fi file) {
            this.config = config;
            this.source = source;
            this.file = file;
        }
    }

    // ------------------------------------------------------------------
    // File resolution
    // ------------------------------------------------------------------

    /**
     * Returns the {@code xcore.toml} file handle inside {@code dataDirectory}.
     *
     * @param dataDirectory the Mindustry data directory
     * @return a {@link Fi} pointing to {@code <dataDirectory>/xcore.toml}
     */
    public static Fi resolveXcoreToml(Fi dataDirectory) {
        return dataDirectory.child(XCORE_TOML);
    }

    /**
     * Returns the {@code secrets.toml} file handle inside the resolved global
     * config directory.
     *
     * @param globalConfigDirectory explicit global directory, or {@code null} to use the user home
     * @return a {@link Fi} pointing to {@code <globalDir>/secrets.toml}
     */
    public static Fi resolveSecretsToml(String globalConfigDirectory) {
        return resolveGlobalDir(globalConfigDirectory).child(SECRETS_TOML);
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Loads the server-local configuration following the TOML resolution order.
     *
     * <p>The returned {@link TomlXcoreConfig} is fully normalized.</p>
     *
     * @param dataDirectory the Mindustry data directory
     * @return a {@link LoadResult} containing the resolved {@link TomlXcoreConfig}
     */
    public static LoadResult<TomlXcoreConfig> loadXcoreConfig(Fi dataDirectory) {
        Fi tomlFile = resolveXcoreToml(dataDirectory);
        if (tomlFile.exists()) {
            TomlXcoreConfig toml = readToml(tomlFile, TomlXcoreConfig.class);
            toml.normalize();
            return new LoadResult<>(toml, Source.TOML, tomlFile);
        }

        ConfigTomlTemplateWriter.writeDefaultXcoreToml(tomlFile);
        TomlXcoreConfig toml = readToml(tomlFile, TomlXcoreConfig.class);
        toml.normalize();
        return new LoadResult<>(toml, Source.DEFAULT_TEMPLATE, tomlFile);
    }

    /**
     * Loads the shared global/secrets configuration as the structured TOML DTO following the TOML resolution order.
     *
     * <p>The returned {@link TomlSecretsConfig} is fully normalized.</p>
     *
     * @param globalConfigDirectory explicit global directory, or {@code null} to use the user home
     * @return a {@link LoadResult} containing the resolved {@link TomlSecretsConfig}
     */
    public static LoadResult<TomlSecretsConfig> loadTomlSecretsConfig(String globalConfigDirectory) {
        Fi tomlFile = resolveSecretsToml(globalConfigDirectory);
        if (tomlFile.exists()) {
            TomlSecretsConfig toml = readToml(tomlFile, TomlSecretsConfig.class);
            toml.normalize();
            return new LoadResult<>(toml, Source.TOML, tomlFile);
        }

        ConfigTomlTemplateWriter.writeDefaultSecretsToml(tomlFile);
        TomlSecretsConfig toml = readToml(tomlFile, TomlSecretsConfig.class);
        toml.normalize();
        return new LoadResult<>(toml, Source.DEFAULT_TEMPLATE, tomlFile);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static Fi resolveGlobalDir(String globalConfigDirectory) {
        return Fi.get(globalConfigDirectory == null
                ? System.getProperty("user.home")
                : globalConfigDirectory);
    }

    private static <T> T readToml(Fi file, Class<T> type) {
        try {
            TomlMapper mapper = createTomlMapper();
            String content = Files.readString(file.file().toPath());
            if (type == TomlXcoreConfig.class) {
                content = quoteBareDiscordChannelId(content);
            }
            return mapper.readValue(content, type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read TOML from " + file.absolutePath() + ": " + e.getMessage(), e);
        }
    }

    private static String quoteBareDiscordChannelId(String content) {
        StringBuilder output = new StringBuilder(content.length() + 2);
        boolean discordSection = false;
        int index = 0;
        while (index < content.length()) {
            int lineStart = index;
            while (index < content.length() && content.charAt(index) != '\n' && content.charAt(index) != '\r') {
                index++;
            }

            String line = content.substring(lineStart, index);
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                discordSection = "[discord]".equals(trimmed);
            }
            output.append(discordSection ? quoteBareChannelIdLine(line) : line);

            if (index < content.length()) {
                char current = content.charAt(index++);
                output.append(current);
                if (current == '\r' && index < content.length() && content.charAt(index) == '\n') {
                    output.append(content.charAt(index++));
                }
            }
        }
        return output.toString();
    }

    private static String quoteBareChannelIdLine(String line) {
        int keyStart = firstNonWhitespace(line, 0);
        String key = "channel_id";
        if (!line.startsWith(key, keyStart)) {
            return line;
        }

        int cursor = firstNonWhitespace(line, keyStart + key.length());
        if (cursor >= line.length() || line.charAt(cursor) != '=') {
            return line;
        }

        int valueStart = firstNonWhitespace(line, cursor + 1);
        if (valueStart >= line.length()) {
            return line;
        }
        char firstValue = line.charAt(valueStart);
        if (firstValue == '"' || firstValue == '\'') {
            return line;
        }

        int valueEnd = valueStart;
        while (valueEnd < line.length()) {
            char ch = line.charAt(valueEnd);
            if (ch < '0' || ch > '9') {
                break;
            }
            valueEnd++;
        }
        if (valueEnd == valueStart) {
            return line;
        }

        int suffixStart = firstNonWhitespace(line, valueEnd);
        if (suffixStart < line.length() && line.charAt(suffixStart) != '#') {
            return line;
        }

        return line.substring(0, valueStart)
                + '"'
                + line.substring(valueStart, valueEnd)
                + '"'
                + line.substring(valueEnd);
    }

    private static int firstNonWhitespace(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static TomlMapper createTomlMapper() {
        return PluginConfigLoader.lenientTomlMapper();
    }
}
