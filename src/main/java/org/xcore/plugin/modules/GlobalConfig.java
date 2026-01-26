package org.xcore.plugin.modules;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.serialization.Jval;

public class GlobalConfig {
    public ObjectMap<String, Long> servers = new ObjectMap<>();
    public String mongoConnectionString = "";
    public String discordBotToken = "";
    public String discordCommandPrefix = "x!";

    public int sockServerPort = 2000;

    public long discordAdminRoleId = 0L;
    public long discordGeneralAdminRoleId = 0L;
    public long discordMapReviewerRoleId = 0L;

    public long discordBansChannelId = 0L;
    public long discordPrivateChannelId = 0L;

    public void postInit(Fi globalConfigFile) {
        Jval.read(globalConfigFile.reader()).asObject().forEach(jval -> {
            if (jval.key.equals("servers")) {
                jval.value.asObject().forEach(j -> servers.put(j.key, j.value.asLong()));
            }
        });
    }
}
