package org.xcore.plugin.infra.di;

import arc.files.Fi;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;

import static mindustry.Vars.dataDirectory;
import static org.xcore.plugin.PluginVars.prettyGson;

@Factory
public class ConfigFactory {

    private static final Fi configFile = dataDirectory.child("xcconfig.json");

    @Bean
    public Config config() {
        if (configFile.exists()) {
            return prettyGson.fromJson(configFile.reader(), Config.class);
        }
        Config config = new Config();
        configFile.writeString(prettyGson.toJson(config));
        return config;
    }

    @Bean
    public GlobalConfig globalConfig(Config config) {
        Fi globalConfigFile = Fi.get(config.globalConfigDirectory == null
                ? System.getProperty("user.home")
                : config.globalConfigDirectory).child("servers.json");

        GlobalConfig globalConfig;
        if (globalConfigFile.exists()) {
            globalConfig = prettyGson.fromJson(globalConfigFile.reader(), GlobalConfig.class);
        } else {
            globalConfig = new GlobalConfig();
            globalConfigFile.writeString(prettyGson.toJson(globalConfig));
        }

        globalConfig.postInit(globalConfigFile);
        return globalConfig;
    }
}
