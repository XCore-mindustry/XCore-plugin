package org.xcore.plugin.config;

import arc.files.Fi;
import com.google.gson.Gson;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Named;
import org.xcore.plugin.common.PLog;

import static mindustry.Vars.dataDirectory;

@Factory
public class ConfigFactory {

    @Bean
    public TomlXcoreConfig serverLocalConfig() {
        var result = ConfigTomlLoader.loadXcoreConfig(dataDirectory);
        logSource("Config", result.file, result.source);

        TomlXcoreConfig config = result.config;
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

    private static void logSource(String label, Fi file, ConfigTomlLoader.Source source) {
        switch (source) {
            case TOML -> PLog.infoTag("Config", "Loaded @ from @", label, file.name());
            case DEFAULT_TEMPLATE -> PLog.infoTag("Config", "Created default @ at @", label, file.name());
        }
    }
}
