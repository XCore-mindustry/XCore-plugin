package org.xcore.plugin.modules;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.serialization.Jval;
import org.xcore.plugin.XcorePlugin;

import static org.xcore.plugin.PluginVars.*;

public class GlobalConfig {
    public static Fi globalConfigFile = Fi.get(config.globalConfigDirectory == null ? System.getProperty("user.home") : config.globalConfigDirectory).child("servers.json");
    public ObjectMap<String, Long> servers = new ObjectMap<>();
    public String mongoConnectionString = "";
    public String discordBotToken = "";
    public long discordAdminRoleId = 0L;
    public long discordBansChannelId = 0L;

    public static void init() {
        if (globalConfigFile.exists()) {
            globalConfig = gson.fromJson(globalConfigFile.reader(), GlobalConfig.class);
            XcorePlugin.info("Global Config loaded.");
        } else {
            globalConfigFile.writeString(gson.toJson(globalConfig = new GlobalConfig()));
            XcorePlugin.info("Global Config generated.");
        }
        globalConfig.postInit();
    }

    public void postInit() {
        Jval.read(globalConfigFile.reader()).asObject().forEach(jval -> {
            if (jval.key.equals("servers")) {
                jval.value.asObject().forEach(j -> servers.put(j.key, j.value.asLong()));
            }
        });
    }
}
