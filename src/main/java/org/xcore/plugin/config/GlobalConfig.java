package org.xcore.plugin.config;

import arc.files.Fi;
import java.util.ArrayList;

import static org.xcore.plugin.common.PLog.err;

public class GlobalConfig {
    public String mongoConnectionString = null;
    public String databaseName = null;

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

        if (!errors.isEmpty()) {
            err("===========================================");
            err("  INVALID CONFIGURATION: @", globalConfigFile.name());
            err("  Missing or invalid required fields:");
            errors.forEach(key -> err("    - @", key));
            err("");
            err("  Example configuration:");
            err("  {");
            err("    \"mongo_connection_string\": \"mongodb://localhost:27017\",");
            err("    \"database_name\": \"xcore\"");
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
