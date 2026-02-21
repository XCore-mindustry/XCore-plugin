package org.xcore.plugin.config;

import arc.files.Fi;
import java.util.ArrayList;
import org.xcore.plugin.common.BiMap;

import static org.xcore.plugin.common.PLog.err;

public class GlobalConfig {
    public BiMap<String, Long> servers = new BiMap<>();
    public String mongoConnectionString = null;
    public String databaseName = null;

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
    public String weblateUrl = "https://xcore.eradication.fun/";
    public String discordRedVSBlueUrl = "https://discord.gg/UdnuFetcNt";

    public int minPlayTimeForVotekick = 60;
    public int minPlayTimeForGlobalChat = 240;

    public int voteKickBanDurationMinutes = 30;
    public float voteDurationSeconds = 60.0f;
    public int mapSwitchDelaySeconds = 10;

    public int eventsPerPage = 10;
    public int mapsPerPage = 10;
    public int commandsPerPage = 6;

    public int maxHistory = 16;

    public boolean isDataBaseReadOnly = false;
    public boolean isDataBaseMigration = false;

    public void postInit(Fi globalConfigFile) {
        var errors = new ArrayList<String>();

        if (mongoConnectionString == null || mongoConnectionString.isBlank()) {
            errors.add("mongo_connection_string");
        }
        if (databaseName == null || databaseName.isBlank()) {
            errors.add("database_name");
        }
        if (servers == null || servers.isEmpty()) {
            errors.add("servers (must contain at least one entry, e.g. \"server-name\": 123456789)");
        }

        if (!errors.isEmpty()) {
            err("===========================================");
            err("  INVALID CONFIGURATION: @", globalConfigFile.name());
            err("  Missing or invalid required fields:");
            errors.forEach(key -> err("    - @", key));
            err("");
            err("  Example configuration:");
            err("  {");
            err("    \"mongo_connection_string\": \"mongodb://localhost:27017\",");
            err("    \"database_name\": \"xcore\",");
            err("    \"servers\": {");
            err("      \"my-server\": 1234567890");
            err("    }");
            err("  }");
            err("");
            err("  Fix @ and restart.", globalConfigFile.name());
            err("===========================================");
            throw new IllegalStateException(
                    "Missing required config in " + globalConfigFile.name() + ": " + String.join(", ", errors)
            );
        }
    }
}
