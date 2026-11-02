package org.xcore.plugin.config;

import arc.files.Fi;
import com.google.gson.Gson;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Named;
import java.util.Map;
import org.xcore.plugin.common.PLog;

import static mindustry.Vars.dataDirectory;

@Factory
public class ConfigFactory {

    @Bean
    public TomlXcoreConfig serverLocalConfig() {
        var result = ConfigTomlLoader.loadXcoreConfig(dataDirectory);
        logSource("Config", result.file, result.source);

        TomlXcoreConfig config = result.config;
        applyEnvironmentOverrides(config, System.getenv());
        config.normalize();
        return config;
    }

    @Bean
    public ServerLocalConfigTomlStore serverLocalConfigTomlStore() {
        return new ServerLocalConfigTomlStore(ConfigTomlLoader.resolveXcoreToml(dataDirectory));
    }

    @Bean
    public ServerLocalConfigPathEditor serverLocalConfigPathEditor(@Named("pretty") Gson gson) {
        return new ServerLocalConfigPathEditor(gson);
    }

    @Bean
    public ServerLocalConfigTomlRenderer serverLocalConfigTomlRenderer() {
        return new ServerLocalConfigTomlRenderer();
    }

    @Bean
    public TomlSecretsConfig tomlSecretsConfig(TomlXcoreConfig serverLocalConfig) {
        var result = ConfigTomlLoader.loadTomlSecretsConfig(serverLocalConfig.paths.globalConfigDirectory);
        logSource("GlobalConfig", result.file, result.source);

        TomlSecretsConfig tomlSecretsConfig = result.config;
        tomlSecretsConfig.normalize();
        tomlSecretsConfig.validate(result.file);
        return tomlSecretsConfig;
    }

    static void applyEnvironmentOverrides(TomlXcoreConfig config, Map<String, String> environment) {
        String name = environment.get("MINDUSTRY_SERVER_NAME");
        if (name != null && !name.isBlank()) config.server.name = name.trim();
        String gamemode = environment.get("MINDUSTRY_GAMEMODE");
        if (gamemode != null && !gamemode.isBlank()) config.server.autoStartGamemode = gamemode;
        String autoStart = environment.get("XCORE_AUTO_START");
        if (autoStart != null && !autoStart.isBlank()) {
            if (!autoStart.equalsIgnoreCase("true") && !autoStart.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("XCORE_AUTO_START must be true or false");
            }
            config.server.autoStart = Boolean.parseBoolean(autoStart);
        }
    }

    private static void logSource(String label, Fi file, ConfigTomlLoader.Source source) {
        switch (source) {
            case TOML -> PLog.infoTag("Config", "Loaded @ from @", label, file.name());
            case DEFAULT_TEMPLATE -> PLog.infoTag("Config", "Created default @ at @", label, file.name());
        }
    }
}
