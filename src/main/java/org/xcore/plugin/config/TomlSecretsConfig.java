package org.xcore.plugin.config;

import arc.files.Fi;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.xcore.plugin.common.PLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TomlSecretsConfig implements SelfNormalizing {
    public int version = 1;

    public DatabaseConfig database = new DatabaseConfig();
    public ExternalLinksConfig externalLinks = new ExternalLinksConfig();
    public ModerationConfig moderation = new ModerationConfig();
    public ChatConfig chat = new ChatConfig();
    public MapsConfig maps = new MapsConfig();
    public PaginationConfig pagination = new PaginationConfig();
    public MessagesConfig messages = new MessagesConfig();
    public TranslationSection translation = new TranslationSection();
    public RatingConfig rating = new RatingConfig();
    public CasesConfig cases = new CasesConfig();

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
        if (rating == null) {
            rating = new RatingConfig();
        }
        rating.normalize();
        if (cases == null) {
            cases = new CasesConfig();
        }
        cases.normalize();
    }

    public void validate(Fi secretsFile) {
        normalize();

        var errors = new ArrayList<String>();

        if (database.mongoConnectionString == null || database.mongoConnectionString.isBlank()) {
            errors.add("database.mongo_connection_string");
        }
        if (database.name == null || database.name.isBlank()) {
            errors.add("database.name");
        }

        if (!errors.isEmpty()) {
            String fileName = secretsFile != null ? secretsFile.name() : "secrets.toml";
            PLog.err("===========================================");
            PLog.err("  INVALID CONFIGURATION: @", fileName);
            PLog.err("  Missing or invalid required fields:");
            errors.forEach(key -> PLog.err("    - @", key));
            PLog.err("");
            PLog.err("  Example secrets.toml:");
            PLog.err("  [database]");
            PLog.err("  mongo_connection_string = \"mongodb://localhost:27017\"");
            PLog.err("  name = \"xcore\"");
            PLog.err("");
            PLog.err("  Fix @ and restart.", fileName);
            PLog.err("===========================================");
            throw new IllegalStateException(
                    "Missing required config in " + fileName + ": " + String.join(", ", errors)
            );
        }
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
        public ChecksConfig checks = new ChecksConfig();

        public void normalize() {
            if (voting == null) {
                voting = new VotingConfig();
            }
            if (checks == null) {
                checks = new ChecksConfig();
            }
            checks.normalize();
        }

        public static class VotingConfig {
            public int switchDelaySeconds = 10;
        }

        public static class ChecksConfig {
            public static final Set<String> FINDING_CODES = Set.of(
                    "missing-name", "missing-author", "size-out-of-range", "requires-mods", "data-patches", "unknown-content", "no-core",
                    "no-spawn", "pvp-single-team", "attack-no-enemy-core", "td-road-floor-mixed", "td-road-floor-unexpected",
                    "td-road-disconnected", "td-spawn-not-at-road-start", "td-border-wall-missing", "td-drop-zone-radius",
                    "td-waves-enabled");
            public int maxFileBytes = 4194304;
            public int maxTiles = 360000;
            /** Documented finding code to error/warning/info. Empty preserves the default severities. */
            public Map<String, String> severities = new LinkedHashMap<>();
            public Map<String, ProfileConfig> profiles = defaultProfiles();

            public void normalize() {
                for (String problem : repair()) {
                    PLog.warnTag("Config", "maps.checks: @", problem);
                }
            }

            /**
             * Repairs invalid values in place. An invalid severity override or dimension bound never stops
             * start-up: it is dropped or adjusted, and described in the returned list for the load-time warning.
             */
            List<String> repair() {
                var problems = new ArrayList<String>();
                ChecksConfig defaults = new ChecksConfig();
                if (maxFileBytes <= 0) {
                    maxFileBytes = defaults.maxFileBytes;
                }
                if (maxTiles <= 0) {
                    maxTiles = defaults.maxTiles;
                }
                var normalizedSeverities = new java.util.TreeMap<String, String>();
                if (severities != null) severities.forEach((code, value) -> {
                    String severity = normalizeSeverity(code, value);
                    if (severity != null) {
                        normalizedSeverities.put(code, severity);
                    } else if (code == null || !FINDING_CODES.contains(code)) {
                        problems.add("severities: unknown check id \"" + code + "\" ignored");
                    } else {
                        problems.add("severities." + code + ": unknown severity \"" + value
                                + "\" ignored (expected error, warning or info); the default severity applies");
                    }
                });
                severities = new LinkedHashMap<>(normalizedSeverities);
                if (profiles == null) {
                    profiles = defaultProfiles();
                }
                // A profiles table in the file replaces the whole map; bounds added for another profile
                // must not silently switch the Tower Defence policy off.
                defaultProfiles().forEach(profiles::putIfAbsent);
                profiles.replaceAll((id, config) -> {
                    ProfileConfig normalized = config == null ? new ProfileConfig() : config;
                    normalized.repair("profiles." + id, problems);
                    return normalized;
                });
                return problems;
            }

            public static String normalizeSeverity(String code, String value) {
                if (code == null || !FINDING_CODES.contains(code) || value == null) return null;
                String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
                return Set.of("error", "warning", "info").contains(normalized) ? normalized : null;
            }

            private static Map<String, ProfileConfig> defaultProfiles() {
                var map = new LinkedHashMap<String, ProfileConfig>();
                map.put("tower-defense", new ProfileConfig());
                return map;
            }

            public static class ProfileConfig {
                /** Empty means any single floor type is accepted for the road. */
                public String roadFloor = "metal-tiles-2";
                /** Empty turns the border check off. */
                public String borderWall = "metal-wall-2";
                public double borderTolerance = 0.05;
                public boolean requireDropZoneRadiusZero = true;
                /** Inclusive dimension limits. Zero disables that endpoint; parser safety caps always apply. */
                public int minWidth = 0;
                public int maxWidth = 0;
                public int minHeight = 0;
                public int maxHeight = 0;

                public void normalize() {
                    repair("profile", new ArrayList<>());
                }

                void repair(String path, List<String> problems) {
                    ProfileConfig defaults = new ProfileConfig();
                    if (roadFloor == null) {
                        roadFloor = "";
                    }
                    if (borderWall == null) {
                        borderWall = "";
                    }
                    if (!Double.isFinite(borderTolerance) || borderTolerance < 0 || borderTolerance > 1) {
                        borderTolerance = defaults.borderTolerance;
                    }
                    minWidth = bound(minWidth, path + ".min_width", problems);
                    maxWidth = bound(maxWidth, path + ".max_width", problems);
                    minHeight = bound(minHeight, path + ".min_height", problems);
                    maxHeight = bound(maxHeight, path + ".max_height", problems);
                    if (maxWidth > 0 && maxWidth < minWidth) {
                        problems.add(path + ": max_width = " + maxWidth + " is below min_width = " + minWidth
                                + "; using max_width = " + minWidth);
                        maxWidth = minWidth;
                    }
                    if (maxHeight > 0 && maxHeight < minHeight) {
                        problems.add(path + ": max_height = " + maxHeight + " is below min_height = " + minHeight
                                + "; using max_height = " + minHeight);
                        maxHeight = minHeight;
                    }
                }

                private static int bound(int value, String key, List<String> problems) {
                    if (value >= 0) {
                        return value;
                    }
                    problems.add(key + " = " + value + " is negative; that bound is off");
                    return 0;
                }
            }
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

    public static class RatingConfig {
        public SeasonsConfig seasons = new SeasonsConfig();

        public void normalize() {
            if (seasons == null) {
                seasons = new SeasonsConfig();
            }
            seasons.normalize();
        }
    }

    /** Shared by every server so that all of them agree on when a season ends. */
    public static class SeasonsConfig {
        public String length = "3mo";
        public String timezone = "UTC";
        public List<String> noticeThresholds = new ArrayList<>(List.of("7d", "3d", "24h", "1h"));
        public String settlementGrace = "5m";
        public int podiumSize = 10;
        public int podiumMinMatches = 10;
        public String reset = "soft";
        public double resetCarry = 0.5;

        public void normalize() {
            SeasonsConfig defaults = new SeasonsConfig();
            if (length == null || length.isBlank()) {
                length = defaults.length;
            }
            if (timezone == null || timezone.isBlank()) {
                timezone = defaults.timezone;
            }
            if (noticeThresholds == null) {
                noticeThresholds = defaults.noticeThresholds;
            }
            if (settlementGrace == null || settlementGrace.isBlank()) {
                settlementGrace = defaults.settlementGrace;
            }
            if (podiumSize <= 0) {
                podiumSize = defaults.podiumSize;
            }
            if (podiumMinMatches < 0) {
                podiumMinMatches = defaults.podiumMinMatches;
            }
            if (reset == null || reset.isBlank()) {
                reset = defaults.reset;
            }
        }
    }

    /** Player reports and the notices that answer them. Every feature here is off or inert by default. */
    public static class CasesConfig {
        public ReportsConfig reports = new ReportsConfig();
        public NoticesConfig notices = new NoticesConfig();

        public void normalize() {
            if (reports == null) {
                reports = new ReportsConfig();
            }
            reports.normalize();
            if (notices == null) {
                notices = new NoticesConfig();
            }
            notices.normalize();
        }

        public static class ReportsConfig {
            public boolean enabled = false;
            public int cooldownSeconds = 60;
            public int sameTargetCooldownMinutes = 30;
            public int dailyLimit = 10;
            public int networkBurstLimit = 6;
            public int minPlayTimeMinutes = 0;
            public int chatLines = 30;
            public int chatWindowMinutes = 10;
            public int blockWindowSeconds = 600;
            public int recentLeaverMinutes = 10;

            public void normalize() {
                ReportsConfig defaults = new ReportsConfig();
                if (cooldownSeconds < 0) {
                    cooldownSeconds = defaults.cooldownSeconds;
                }
                if (sameTargetCooldownMinutes < 0) {
                    sameTargetCooldownMinutes = defaults.sameTargetCooldownMinutes;
                }
                if (dailyLimit < 0) {
                    dailyLimit = defaults.dailyLimit;
                }
                if (networkBurstLimit < 0) {
                    networkBurstLimit = defaults.networkBurstLimit;
                }
                if (minPlayTimeMinutes < 0) {
                    minPlayTimeMinutes = defaults.minPlayTimeMinutes;
                }
                if (chatLines < 0) {
                    chatLines = defaults.chatLines;
                }
                if (chatWindowMinutes < 0) {
                    chatWindowMinutes = defaults.chatWindowMinutes;
                }
                if (blockWindowSeconds < 0) {
                    blockWindowSeconds = defaults.blockWindowSeconds;
                }
                if (recentLeaverMinutes < 0) {
                    recentLeaverMinutes = defaults.recentLeaverMinutes;
                }
            }
        }

        public static class NoticesConfig {
            public int maxPerJoin = 5;

            public void normalize() {
                if (maxPerJoin < 0) {
                    maxPerJoin = new NoticesConfig().maxPerJoin;
                }
            }
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
}
