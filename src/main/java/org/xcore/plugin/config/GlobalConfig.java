package org.xcore.plugin.config;

import arc.files.Fi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
    public int privateMessagesPerPage = 10;

    public int maxHistory = 16;
    public int privateMessageMaxLength = 300;
    public int privateMessageCooldownSeconds = 10;
    public int privateMessageUnreadLimit = 30;
    public int privateMessageBlockedLimit = 100;

    public boolean isDataBaseReadOnly = false;
    public boolean isDataBaseMigration = false;
    public Map<String, TranslationProviderConfig> translationProviders = defaultTranslationProviders();
    public IpReputationProviderConfig ipReputationProvider = new IpReputationProviderConfig();

    public void normalize() {
        if (translationProviders == null || translationProviders.isEmpty()) {
            translationProviders = defaultTranslationProviders();
        }

        translationProviders.replaceAll((providerId, providerConfig) -> {
            TranslationProviderConfig normalized = providerConfig == null
                    ? new TranslationProviderConfig()
                    : providerConfig;
            normalized.normalize();
            return normalized;
        });

        if (ipReputationProvider == null) {
            ipReputationProvider = new IpReputationProviderConfig();
        }

        ipReputationProvider.normalize();
    }

    public void postInit(Fi globalConfigFile) {
        normalize();

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

    private static Map<String, TranslationProviderConfig> defaultTranslationProviders() {
        var providers = new LinkedHashMap<String, TranslationProviderConfig>();
        providers.put("google", new TranslationProviderConfig());
        return providers;
    }

    public static class TranslationProviderConfig {
        public String type = "google";
        public boolean enabled = true;
        public String apiKey = null;
        public String baseUrl = "https://api.openai.com/v1";
        public String model = "gpt-5.4";
        public String apiMode = null;
        public String organization = null;
        public String project = null;
        public int timeoutSeconds = 15;
        public int maxRetries = 1;
        public double temperature = 0.0;
        public Set<String> supportedLanguages = new LinkedHashSet<>();

        public void normalize() {
            if (type == null || type.isBlank()) {
                type = "google";
            }

            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.openai.com/v1";
            }

            if (model == null || model.isBlank()) {
                model = "gpt-5.4";
            }

            if (apiMode != null && !apiMode.isBlank()) {
                apiMode = apiMode.trim().toLowerCase();
            }

            if (timeoutSeconds <= 0) {
                timeoutSeconds = 15;
            }

            if (maxRetries < 0) {
                maxRetries = 1;
            }

            if (supportedLanguages == null) {
                supportedLanguages = new LinkedHashSet<>();
            }
        }
    }

    public static class IpReputationProviderConfig {
        public String baseUrl = "http://ip-api.com/json";
        public int timeoutSeconds = 10;
        public int maxRetries = 2;
        public int rateLimitPerMinute = 45;

        public void normalize() {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://ip-api.com/json";
            }

            if (timeoutSeconds <= 0) {
                timeoutSeconds = 10;
            }

            if (maxRetries < 0) {
                maxRetries = 2;
            }

            if (rateLimitPerMinute <= 0) {
                rateLimitPerMinute = 45;
            }
        }
    }
}
