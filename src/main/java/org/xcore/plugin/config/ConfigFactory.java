package org.xcore.plugin.config;

import arc.files.Fi;
import com.google.gson.Gson;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import jakarta.inject.Named;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;

import static mindustry.Vars.dataDirectory;

@Factory
public class ConfigFactory {
    private static final Fi configFile = dataDirectory.child("xcconfig.json");

    @Bean
    @Named("xcConfigFile")
    public Fi xcConfigFile() {
        return configFile;
    }

    @Bean
    public Config config(@Named("pretty") Gson gson) {
        if (configFile.exists()) {
            return gson.fromJson(configFile.reader(), Config.class);
        }
        Config config = new Config();
        configFile.writeString(gson.toJson(config));
        return config;
    }

    @Bean
    public GlobalConfig globalConfig(Config config, @Named("pretty") Gson gson) {
        Fi globalConfigFile = Fi.get(config.globalConfigDirectory == null
                ? System.getProperty("user.home")
                : config.globalConfigDirectory).child("secrets.json");

        GlobalConfig globalConfig;
        if (globalConfigFile.exists()) {
            globalConfig = gson.fromJson(globalConfigFile.reader(), GlobalConfig.class);
        } else {
            globalConfig = new GlobalConfig();
            globalConfigFile.writeString(gson.toJson(globalConfig));
        }

        globalConfig.postInit(globalConfigFile);
        return globalConfig;
    }
}