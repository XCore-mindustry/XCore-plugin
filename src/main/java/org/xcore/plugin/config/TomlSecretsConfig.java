package org.xcore.plugin.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class TomlSecretsConfig {
    public int version = 1;

    public DatabaseConfig database = new DatabaseConfig();
    public ExternalLinksConfig externalLinks = new ExternalLinksConfig();
    public ModerationConfig moderation = new ModerationConfig();
    public ChatConfig chat = new ChatConfig();
    public MapsConfig maps = new MapsConfig();
    public PaginationConfig pagination = new PaginationConfig();
    public MessagesConfig messages = new MessagesConfig();
    public TranslationSection translation = new TranslationSection();
    public IpReputationSection ipReputation = new IpReputationSection();

    public void normalize() {
        if (database == null) {
            database = new DatabaseConfig();
        }
        if (externalLinks == null) {
            externalLinks = new ExternalLinksConfig();
        }
        if (moderation == null) {
            moderation = new ModerationConfig();
        }
        moderation.normalize();
        if (chat == null) {
            chat = new ChatConfig();
        }
        chat.normalize();
        if (maps == null) {
            maps = new MapsConfig();
        }
        maps.normalize();
        if (pagination == null) {
            pagination = new PaginationConfig();
        }
        if (messages == null) {
            messages = new MessagesConfig();
        }
        messages.normalize();
        if (translation == null) {
            translation = new TranslationSection();
        }
        translation.normalize();
        if (ipReputation == null) {
            ipReputation = new IpReputationSection();
        }
        ipReputation.normalize();
    }

    public static class DatabaseConfig {
        public String mongoConnectionString = "";
        public String name = "";
        public boolean readOnly = false;
        public boolean migrationEnabled = false;
    }

    public static class ExternalLinksConfig {
        public String discordUrl = "https://discord.gg/RUMCCa9QAC";
        public String githubUrl = "https://github.com/XCore-mindustry/";
        public String donatelloUrl = "https://donatello.to/xcore";
        public String weblateUrl = "https://xcore.eradication.fun/";
        @JsonProperty("discord_red_vs_blue_url")
        public String discordRedVSBlueUrl = "https://discord.gg/UdnuFetcNt";
    }

    public static class ModerationConfig {
        public VotekickConfig votekick = new VotekickConfig();

        public void normalize() {
            if (votekick == null) {
                votekick = new VotekickConfig();
            }
        }

        public static class VotekickConfig {
            public int minPlayTimeMinutes = 60;
            public int banDurationMinutes = 30;
            public float voteDurationSeconds = 60.0f;
        }
    }

    public static class ChatConfig {
        public GlobalConfig global = new GlobalConfig();

        public void normalize() {
            if (global == null) {
                global = new GlobalConfig();
            }
        }

        public static class GlobalConfig {
            public int minPlayTimeMinutes = 240;
        }
    }

    public static class MapsConfig {
        public VotingConfig voting = new VotingConfig();

        public void normalize() {
            if (voting == null) {
                voting = new VotingConfig();
            }
        }

        public static class VotingConfig {
            public int switchDelaySeconds = 10;
        }
    }

    public static class PaginationConfig {
        public int eventsPerPage = 10;
        public int mapsPerPage = 10;
        public int commandsPerPage = 6;
        public int privateMessagesPerPage = 10;
    }

    public static class MessagesConfig {
        public HistoryConfig history = new HistoryConfig();

        @JsonProperty("private")
        public PrivateConfig privateMessages = new PrivateConfig();

        public void normalize() {
            if (history == null) {
                history = new HistoryConfig();
            }
            if (privateMessages == null) {
                privateMessages = new PrivateConfig();
            }
        }

        public static class HistoryConfig {
            public int maxHistory = 16;
        }

        public static class PrivateConfig {
            public int maxLength = 300;
            public int cooldownSeconds = 10;
            public int unreadLimit = 30;
            public int blockedLimit = 100;
        }
    }

    public static class TranslationSection {
        public Map<String, ProviderConfig> providers = defaultProviders();

        public void normalize() {
            if (providers == null || providers.isEmpty()) {
                providers = defaultProviders();
            }
            providers.replaceAll((id, config) -> {
                ProviderConfig normalized = config == null ? new ProviderConfig() : config;
                normalized.normalize();
                return normalized;
            });
        }

        private static Map<String, ProviderConfig> defaultProviders() {
            var map = new LinkedHashMap<String, ProviderConfig>();
            map.put("google", new ProviderConfig());
            return map;
        }

        public static class ProviderConfig {
            public String type = "google";
            public boolean enabled = true;
            public String apiKey = "";
            public String baseUrl = "";
            public String model = "";
            public String apiMode = "";
            public String organization = "";
            public String project = "";
            public int timeoutSeconds = 15;
            public int maxRetries = 1;
            public double temperature = 0.0;
            public Set<String> supportedLanguages = new LinkedHashSet<>();

            public void normalize() {
                if (type == null || type.isBlank()) {
                    type = "google";
                }
                if ("openai".equalsIgnoreCase(type)) {
                    if (baseUrl == null || baseUrl.isBlank()) {
                        baseUrl = "https://api.openai.com/v1";
                    }
                    if (model == null || model.isBlank()) {
                        model = "gpt-5.4";
                    }
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
    }

    public static class IpReputationSection {
        public ProviderConfig provider = new ProviderConfig();

        public void normalize() {
            if (provider == null) {
                provider = new ProviderConfig();
            } else {
                provider.normalize();
            }
        }

        public static class ProviderConfig {
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
}
