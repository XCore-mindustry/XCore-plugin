package org.xcore.plugin.modules;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.serialization.Jval;

import static org.xcore.plugin.PluginVars.config;

public class ServersConfig {
    public static ObjectMap<String, Long> servers= new ObjectMap<>();
    public static Fi serversConfigFile = Fi.get(config.globalConfigDirectory == null ? System.getProperty("user.home") : config.globalConfigDirectory).child("servers.json");

    public static void init() {
        servers.clear();
        Jval.read(serversConfigFile.reader()).asObject().forEach(jval -> servers.put(jval.key, jval.value.asLong()));
    }
}
