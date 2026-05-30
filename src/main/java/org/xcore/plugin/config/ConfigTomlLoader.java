package org.xcore.plugin.config;

import arc.files.Fi;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Helper for locating, loading, and defaulting TOML configuration files.
 *
 * <p>Resolution order for each config:</p>
 * <ol>
 *   <li>If the TOML file exists, load it via Jackson TOML.</li>
 *   <li>Else if the legacy JSON file exists, migrate it to TOML, back up the JSON file, then reload from TOML.</li>
 *   <li>Else write a commented default TOML template, then load it.</li>
 * </ol>
 *
 * <p>This class is stateless and performs no logging. Callers are responsible
 * for reporting the {@link Source} to startup logs.</p>
 */
public final class ConfigTomlLoader {
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    private static final String XCORE_TOML = "xcore.toml";
    private static final String XCORE_JSON = "xcconfig.json";
    private static final String SECRETS_TOML = "secrets.toml";
    private static final String SECRETS_JSON = "secrets.json";
    private static Supplier<LocalDateTime> backupTimestampSupplier = LocalDateTime::now;

    private ConfigTomlLoader() {
    }

    /**
     * Identifies which source was used to produce a configuration object.
     */
    public enum Source {
        /** Loaded from an existing {@code xcore.toml} or {@code secrets.toml}. */
        TOML,
        /** Loaded from a legacy {@code xcconfig.json} or {@code secrets.json}. */
        LEGACY_JSON,
        /** Migrated from legacy JSON into TOML during this load. */
        MIGRATED,
        /** Created from a default template because no config file existed. */
        DEFAULT_TEMPLATE
    }

    /**
     * Result of a config load operation, carrying the resolved object, the
     * source that was used, and the file that was read or created.
     *
     * @param <T> the configuration type, such as {@link TomlXcoreConfig}, {@link TomlSecretsConfig}, or {@link GlobalConfig}
     */
    public static final class LoadResult<T> {
        public final T config;
        public final Source source;
        public final Fi file;
        public final Fi backupFile;

        LoadResult(T config, Source source, Fi file) {
            this(config, source, file, null);
        }

        LoadResult(T config, Source source, Fi file, Fi backupFile) {
            this.config = config;
            this.source = source;
            this.file = file;
            this.backupFile = backupFile;
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
     * Returns the legacy {@code xcconfig.json} file handle inside {@code dataDirectory}.
     *
     * @param dataDirectory the Mindustry data directory
     * @return a {@link Fi} pointing to {@code <dataDirectory>/xcconfig.json}
     */
    public static Fi resolveLegacyXcoreJson(Fi dataDirectory) {
        return dataDirectory.child(XCORE_JSON);
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

    /**
     * Returns the legacy {@code secrets.json} file handle inside the resolved
     * global config directory.
     *
     * @param globalConfigDirectory explicit global directory, or {@code null} to use the user home
     * @return a {@link Fi} pointing to {@code <globalDir>/secrets.json}
     */
    public static Fi resolveLegacySecretsJson(String globalConfigDirectory) {
        return resolveGlobalDir(globalConfigDirectory).child(SECRETS_JSON);
    }

    static Fi backupLegacyFile(Fi sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");

        Fi backupFile = sourceFile.sibling(sourceFile.name() + ".bak-" + currentBackupTimestamp());

        try {
            Files.move(sourceFile.file().toPath(), backupFile.file().toPath(), StandardCopyOption.REPLACE_EXISTING);
            return backupFile;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to back up legacy config " + sourceFile.absolutePath() + " to " + backupFile.absolutePath(),
                    e
            );
        }
    }

    static void setBackupTimestampSupplier(Supplier<LocalDateTime> supplier) {
        backupTimestampSupplier = Objects.requireNonNull(supplier, "supplier must not be null");
    }

    static void resetBackupTimestampSupplier() {
        backupTimestampSupplier = LocalDateTime::now;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Loads the server-local configuration following the TOML-first resolution order.
     *
     * <p>The returned {@link TomlXcoreConfig} is fully normalized regardless of source.</p>
     *
     * @param dataDirectory the Mindustry data directory
     * @param gson          the Gson instance used for legacy JSON fallback
     * @return a {@link LoadResult} containing the resolved {@link TomlXcoreConfig}
     */
    public static LoadResult<TomlXcoreConfig> loadXcoreConfig(Fi dataDirectory, Gson gson) {
        Fi tomlFile = resolveXcoreToml(dataDirectory);
        if (tomlFile.exists()) {
            TomlXcoreConfig toml = readToml(tomlFile, TomlXcoreConfig.class);
            toml.normalize();
            return new LoadResult<>(toml, Source.TOML, tomlFile);
        }

        Fi jsonFile = resolveLegacyXcoreJson(dataDirectory);
        if (jsonFile.exists()) {
            return migrateLegacyXcoreConfig(jsonFile, tomlFile, gson);
        }

        ConfigTomlTemplateWriter.writeDefaultXcoreToml(tomlFile);
        TomlXcoreConfig toml = readToml(tomlFile, TomlXcoreConfig.class);
        toml.normalize();
        return new LoadResult<>(toml, Source.DEFAULT_TEMPLATE, tomlFile);
    }

    /**
     * Loads the shared global/secrets configuration following the TOML-first resolution order.
     *
     * <p>The returned {@link GlobalConfig} is fully normalized regardless of source.
     * Callers should still invoke {@link GlobalConfig#postInit(Fi)} for required-field validation.</p>
     *
     * @param globalConfigDirectory explicit global directory, or {@code null} to use the user home
     * @param gson                  the Gson instance used for legacy JSON fallback
     * @return a {@link LoadResult} containing the resolved {@link GlobalConfig}
     */
    public static LoadResult<GlobalConfig> loadGlobalConfig(String globalConfigDirectory, Gson gson) {
        LoadResult<TomlSecretsConfig> result = loadTomlSecretsConfig(globalConfigDirectory, gson);
        GlobalConfig global = ConfigTomlMapper.toGlobalConfig(result.config);
        return new LoadResult<>(global, result.source, result.file, result.backupFile);
    }

    /**
     * Loads the shared global/secrets configuration as the structured TOML DTO following the TOML-first resolution order.
     *
     * <p>The returned {@link TomlSecretsConfig} is fully normalized regardless of source.</p>
     *
     * @param globalConfigDirectory explicit global directory, or {@code null} to use the user home
     * @param gson                  the Gson instance used for legacy JSON fallback
     * @return a {@link LoadResult} containing the resolved {@link TomlSecretsConfig}
     */
    public static LoadResult<TomlSecretsConfig> loadTomlSecretsConfig(String globalConfigDirectory, Gson gson) {
        Fi tomlFile = resolveSecretsToml(globalConfigDirectory);
        if (tomlFile.exists()) {
            TomlSecretsConfig toml = readToml(tomlFile, TomlSecretsConfig.class);
            toml.normalize();
            return new LoadResult<>(toml, Source.TOML, tomlFile);
        }

        Fi jsonFile = resolveLegacySecretsJson(globalConfigDirectory);
        if (jsonFile.exists()) {
            return migrateLegacySecretsConfig(jsonFile, tomlFile, gson);
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

    private static LoadResult<TomlXcoreConfig> migrateLegacyXcoreConfig(Fi jsonFile, Fi tomlFile, Gson gson) {
        Config legacyConfig;
        try (var reader = jsonFile.reader()) {
            legacyConfig = gson.fromJson(reader, Config.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read legacy JSON from " + jsonFile.absolutePath() + ": " + e.getMessage(), e);
        }
        if (legacyConfig == null) {
            legacyConfig = new Config();
        }
        legacyConfig.normalize();

        TomlXcoreConfig migratedToml = ConfigTomlMapper.toTomlXcoreConfig(legacyConfig);
        writeToml(tomlFile, migratedToml);
        Fi backupFile = backupLegacyFile(jsonFile);

        TomlXcoreConfig reloadedToml = readToml(tomlFile, TomlXcoreConfig.class);
        reloadedToml.normalize();
        return new LoadResult<>(reloadedToml, Source.MIGRATED, tomlFile, backupFile);
    }

    private static LoadResult<TomlSecretsConfig> migrateLegacySecretsConfig(Fi jsonFile, Fi tomlFile, Gson gson) {
        GlobalConfig legacyGlobal;
        try (var reader = jsonFile.reader()) {
            legacyGlobal = gson.fromJson(reader, GlobalConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read legacy JSON from " + jsonFile.absolutePath() + ": " + e.getMessage(), e);
        }
        if (legacyGlobal == null) {
            legacyGlobal = new GlobalConfig();
        }
        legacyGlobal.normalize();

        TomlSecretsConfig migratedToml = ConfigTomlMapper.toTomlSecretsConfig(legacyGlobal);
        writeToml(tomlFile, migratedToml);
        Fi backupFile = backupLegacyFile(jsonFile);

        TomlSecretsConfig reloadedToml = readToml(tomlFile, TomlSecretsConfig.class);
        reloadedToml.normalize();
        return new LoadResult<>(reloadedToml, Source.MIGRATED, tomlFile, backupFile);
    }

    private static String currentBackupTimestamp() {
        return backupTimestampSupplier.get().format(BACKUP_TIMESTAMP_FORMAT);
    }

    private static void writeToml(Fi file, Object config) {
        try {
            var parent = file.file().toPath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String tomlString = HumanReadableTomlWriter.write(config);
            file.writeString(tomlString);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to write TOML to " + file.absolutePath() + ": " + e.getMessage(), e);
        }
    }

    private static <T> T readToml(Fi file, Class<T> type) {
        try {
            return createTomlMapper().readValue(file.file(), type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read TOML from " + file.absolutePath() + ": " + e.getMessage(), e);
        }
    }

    private static TomlMapper createTomlMapper() {
        return TomlMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }
}
