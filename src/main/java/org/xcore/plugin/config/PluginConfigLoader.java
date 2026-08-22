package org.xcore.plugin.config;

import arc.files.Fi;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import mindustry.Vars;
import org.xcore.plugin.common.PLog;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Minimal config framework for companion plugins.
 *
 * <p>Loads a plugin-owned TOML file from the Mindustry data directory with the
 * same binding policy as the core configs:</p>
 * <ul>
 *   <li>{@code snake_case} key mapping,</li>
 *   <li>lenient by design — unknown keys (stale/legacy sections) are ignored
 *       and missing keys keep their Java field defaults, so servers boot on
 *       any config file revision,</li>
 *   <li>a documented default template is written on first run; existing files
 *       are never rewritten, so admin comments survive,</li>
 *   <li>{@link SelfNormalizing} config classes are normalized automatically.</li>
 * </ul>
 *
 * <pre>{@code
 * SentinelConfig config = PluginConfigLoader.of(
 *         SentinelConfig.class, "sentinel.toml", () -> DEFAULT_TEMPLATE).load();
 * }</pre>
 *
 * @param <T> the configuration POJO type
 */
public final class PluginConfigLoader<T> {

    private final Fi file;
    private final Class<T> type;
    private final Supplier<String> defaultTemplate;

    /**
     * Resolves {@code fileName} relative to the Mindustry data directory.
     */
    public static <T> PluginConfigLoader<T> of(Class<T> type, String fileName, Supplier<String> defaultTemplate) {
        return new PluginConfigLoader<>(Vars.dataDirectory.child(fileName), type, defaultTemplate);
    }

    /**
     * Use {@link #of} for the standard data-directory location; this overload
     * exists mainly for tests and non-standard file placement.
     */
    public PluginConfigLoader(Fi file, Class<T> type, Supplier<String> defaultTemplate) {
        this.file = file;
        this.type = type;
        this.defaultTemplate = defaultTemplate;
    }

    public synchronized T load() {
        if (!file.exists()) {
            file.writeString(defaultTemplate.get());
            PLog.info("Created default config at @", file.absolutePath());
        }

        try {
            T config = lenientTomlMapper().readValue(file.file(), type);
            if (config instanceof SelfNormalizing normalizing) {
                normalizing.normalize();
            }
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file.absolutePath() + ": " + e.getMessage(), e);
        }
    }

    /**
     * The single place defining the shared TOML binding policy. Core configs
     * ({@link ConfigTomlLoader}) delegate here as well.
     */
    public static TomlMapper lenientTomlMapper() {
        return TomlMapper.builder()
                // FAIL_ON_UNKNOWN_PROPERTIES is on by default; stale or future
                // sections must never prevent a server from booting.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }
}
