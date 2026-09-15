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
    private static final Fi legacyXcoreJsonFile = dataDirectory.child("xcconfig.json");

    @Bean
    @Named("xcConfigFile")
    public Fi legacyXcoreJsonFile() {
        return legacyXcoreJsonFile;
    }

    @Bean
    public TomlXcoreConfig serverLocalConfig(@Named("pretty") Gson gson) {
        var result = ConfigTomlLoader.loadXcoreConfig(dataDirectory, gson);
        logSource("Config", result.file, result.source);

        TomlXcoreConfig config = result.config;
        config.normalize();
        return config;
    }

    @Bean
    @Deprecated
    public Config config(TomlXcoreConfig serverLocalConfig) {
        Config config = ConfigTomlMapper.toConfig(serverLocalConfig);
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
    public TomlSecretsConfig tomlSecretsConfig(TomlXcoreConfig serverLocalConfig, @Named("pretty") Gson gson) {
        var result = ConfigTomlLoader.loadTomlSecretsConfig(serverLocalConfig.paths.globalConfigDirectory, gson);
        logSource("GlobalConfig", result.file, result.source);

        TomlSecretsConfig tomlSecretsConfig = result.config;
        tomlSecretsConfig.normalize();
        return tomlSecretsConfig;
    }

    @Deprecated
    public TomlSecretsConfig tomlSecretsConfig(Config config, @Named("pretty") Gson gson) {
        var result = ConfigTomlLoader.loadTomlSecretsConfig(config != null ? config.globalConfigDirectory : null, gson);
        logSource("GlobalConfig", result.file, result.source);

        TomlSecretsConfig tomlSecretsConfig = result.config;
        tomlSecretsConfig.normalize();
        return tomlSecretsConfig;
    }

    @Bean
    @Deprecated
    public GlobalConfig globalConfig(TomlXcoreConfig serverLocalConfig, TomlSecretsConfig tomlSecretsConfig) {
        GlobalConfig globalConfig = ConfigTomlMapper.toGlobalConfig(tomlSecretsConfig);
        globalConfig.normalize();
        globalConfig.postInit(ConfigTomlLoader.resolveSecretsToml(serverLocalConfig.paths.globalConfigDirectory));
        return globalConfig;
    }

    @Deprecated
    public GlobalConfig globalConfig(Config config, TomlSecretsConfig tomlSecretsConfig) {
        GlobalConfig globalConfig = ConfigTomlMapper.toGlobalConfig(tomlSecretsConfig);
        globalConfig.normalize();
        globalConfig.postInit(ConfigTomlLoader.resolveSecretsToml(config != null ? config.globalConfigDirectory : null));
        return globalConfig;
    }

    private static void logSource(String label, Fi file, ConfigTomlLoader.Source source) {
        switch (source) {
            case TOML -> PLog.infoTag("Config", "Loaded @ from @", label, file.name());
            case LEGACY_JSON -> PLog.warnTag("Config", "Loaded @ from legacy @ (consider migrating to TOML)", label, file.name());
            case MIGRATED -> PLog.infoTag("Config", "Migrated @ to @", label, file.name());
            case DEFAULT_TEMPLATE -> PLog.infoTag("Config", "Created default @ at @", label, file.name());
        }
    }
}
