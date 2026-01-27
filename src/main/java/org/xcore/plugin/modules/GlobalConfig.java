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

    public String discordUrl = "https://discord.gg/RUMCCa9QAC";
    public String githubUrl = "https://github.com/XCore-mindustry/";
    public String donatelloUrl = "https://donatello.to/xcore";
    public String discordRedVSBlueUrl = "https://discord.gg/UdnuFetcNt";

    public int minPlayTimeForVotekick = 60;
    public int minPlayTimeForGlobalChat = 240; // 4 hours

    public int voteKickBanDurationMinutes = 30;
    public float voteDurationSeconds = 60.0f;
    public int mapSwitchDelaySeconds = 10;

    public void postInit(Fi globalConfigFile) {
        Jval.read(globalConfigFile.reader()).asObject().forEach(jval -> {
            if (jval.key.equals("servers")) {
                jval.value.asObject().forEach(j -> servers.put(j.key, j.value.asLong()));
            }
        });
    }
}