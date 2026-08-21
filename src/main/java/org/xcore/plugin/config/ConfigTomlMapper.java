package org.xcore.plugin.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure mapping helpers that convert TOML DTOs into the existing legacy runtime
 * {@link Config} and {@link GlobalConfig} models.
 *
 * <p>This class is side-effect free and performs no file I/O. It normalizes
 * input DTOs at the boundary and repairs null collections or blank optional
 * strings so that the resulting legacy objects match current runtime
 * expectations.</p>
 */
public final class ConfigTomlMapper {
    private ConfigTomlMapper() {
    }

    /**
     * Maps a normalized {@link TomlXcoreConfig} into a legacy {@link Config}.
     *
     * <p>The returned instance is fully initialized and normalized so that
     * consumers do not need to call {@link Config#normalize()} again,
     * although doing so is harmless.</p>
     *
     * @param toml the TOML DTO to map; must not be {@code null}
     * @return a new {@link Config} populated from the DTO
     * @throws IllegalArgumentException if {@code toml} is {@code null}
     */
    public static Config toConfig(TomlXcoreConfig toml) {
        if (toml == null) {
            throw new IllegalArgumentException("toml must not be null");
        }
        toml.normalize();

        Config config = new Config();

        // server
        config.server = toml.server.name;
        config.publicHostOverride = toml.server.publicHostOverride;
        config.playerLimit = toml.server.playerLimit;
        config.gameStartedTimer = toml.server.gameStartedTimer;

        // paths
        config.globalConfigDirectory = toml.paths.globalConfigDirectory;

        // discord
        config.discordChannelId = toml.discord.channelIdAsLong();

        // transport.redis
        config.redisUrl = toml.transport.redis.url;
        config.redisGroupPrefix = toml.transport.redis.groupPrefix;
        config.redisConsumerName = toml.transport.redis.consumerName;
        config.redisReclaimEnabled = toml.transport.redis.reclaim.enabled;
        config.redisReclaimMinIdleMs = toml.transport.redis.reclaim.minIdleMs;
        config.redisReclaimBatch = toml.transport.redis.reclaim.batch;
        config.redisDlqEnabled = toml.transport.redis.dlq.enabled;
        config.redisMaxDeliveryAttempts = toml.transport.redis.dlq.maxDeliveryAttempts;
        config.redisDlqPrefix = toml.transport.redis.dlq.prefix;

        // runtime
        config.disabledCommands = copySet(toml.runtime.disabledCommands);
        config.disabledFeatures = copySet(toml.runtime.disabledFeatures);

        // event hub
        config.isEventHubMap = toml.eventHub.enabled;
        config.eventHubMapID = toml.eventHub.mapId;

        // translation
        config.translation = mapTranslation(toml.translation);

        return config;
    }

    /**
     * Maps a normalized legacy {@link Config} into the structured
     * {@link TomlXcoreConfig} DTO used for TOML persistence.
     *
     * @param config the legacy runtime config; must not be {@code null}
     * @return a new {@link TomlXcoreConfig} populated from the legacy model
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    public static TomlXcoreConfig toTomlXcoreConfig(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        config.normalize();

        TomlXcoreConfig toml = new TomlXcoreConfig();

        toml.server.name = config.server;
        toml.server.publicHostOverride = nullToBlank(config.publicHostOverride);
        toml.server.playerLimit = config.playerLimit;
        toml.server.gameStartedTimer = config.gameStartedTimer;

        toml.paths.globalConfigDirectory = nullToBlank(config.globalConfigDirectory);

        toml.discord.channelId = Long.toString(config.discordChannelId);

        toml.transport.redis.url = config.redisUrl;
        toml.transport.redis.groupPrefix = config.redisGroupPrefix;
        toml.transport.redis.consumerName = config.redisConsumerName;
        toml.transport.redis.reclaim.enabled = config.redisReclaimEnabled;
        toml.transport.redis.reclaim.minIdleMs = config.redisReclaimMinIdleMs;
        toml.transport.redis.reclaim.batch = config.redisReclaimBatch;
        toml.transport.redis.dlq.enabled = config.redisDlqEnabled;
        toml.transport.redis.dlq.maxDeliveryAttempts = config.redisMaxDeliveryAttempts;
        toml.transport.redis.dlq.prefix = config.redisDlqPrefix;

        toml.runtime.disabledCommands = copyMutableSet(config.disabledCommands);
        toml.runtime.disabledFeatures = copyMutableSet(config.disabledFeatures);

        toml.eventHub.enabled = config.isEventHubMap;
        toml.eventHub.mapId = nullToBlank(config.eventHubMapID);

        toml.translation = mapTomlTranslation(config.translation);
        toml.normalize();
        return toml;
    }

    /**
     * Maps a normalized {@link TomlSecretsConfig} into a legacy {@link GlobalConfig}.
     *
     * <p>The returned instance is fully initialized and normalized so that
     * consumers do not need to call {@link GlobalConfig#normalize()} again,
     * although doing so is harmless.</p>
     *
     * @param toml the TOML DTO to map; must not be {@code null}
     * @return a new {@link GlobalConfig} populated from the DTO
     * @throws IllegalArgumentException if {@code toml} is {@code null}
     */
    public static GlobalConfig toGlobalConfig(TomlSecretsConfig toml) {
        if (toml == null) {
            throw new IllegalArgumentException("toml must not be null");
        }
        toml.normalize();

        GlobalConfig global = new GlobalConfig();

        // database
        global.mongoConnectionString = blankToNull(toml.database.mongoConnectionString);
        global.databaseName = blankToNull(toml.database.name);
        global.isDataBaseReadOnly = toml.database.readOnly;
        global.isDataBaseMigration = toml.database.migrationEnabled;

        // external links
        global.discordUrl = toml.externalLinks.discordUrl;
        global.githubUrl = toml.externalLinks.githubUrl;
        global.donatelloUrl = toml.externalLinks.donatelloUrl;
        global.weblateUrl = toml.externalLinks.weblateUrl;
        global.discordRedVSBlueUrl = toml.externalLinks.discordRedVSBlueUrl;

        // moderation
        global.minPlayTimeForVotekick = requireNonNull(toml.moderation, "moderation").votekick.minPlayTimeMinutes;
        global.voteKickBanDurationMinutes = toml.moderation.votekick.banDurationMinutes;
        global.voteDurationSeconds = toml.moderation.votekick.voteDurationSeconds;

        // chat
        global.minPlayTimeForGlobalChat = requireNonNull(toml.chat, "chat").global.minPlayTimeMinutes;

        // maps
        global.mapSwitchDelaySeconds = requireNonNull(toml.maps, "maps").voting.switchDelaySeconds;

        // pagination
        global.eventsPerPage = toml.pagination.eventsPerPage;
        global.mapsPerPage = toml.pagination.mapsPerPage;
        global.commandsPerPage = toml.pagination.commandsPerPage;
        global.privateMessagesPerPage = toml.pagination.privateMessagesPerPage;

        // messages
        global.maxHistory = requireNonNull(toml.messages, "messages").history.maxHistory;
        global.privateMessageMaxLength = toml.messages.privateMessages.maxLength;
        global.privateMessageCooldownSeconds = toml.messages.privateMessages.cooldownSeconds;
        global.privateMessageUnreadLimit = toml.messages.privateMessages.unreadLimit;
        global.privateMessageBlockedLimit = toml.messages.privateMessages.blockedLimit;

        // translation providers
        global.translationProviders = mapTranslationProviders(
                requireNonNull(toml.translation, "translation").providers
        );

        return global;
    }

    /**
     * Maps a normalized legacy {@link GlobalConfig} into the structured
     * {@link TomlSecretsConfig} DTO used for TOML persistence.
     *
     * @param global the legacy runtime global config; must not be {@code null}
     * @return a new {@link TomlSecretsConfig} populated from the legacy model
     * @throws IllegalArgumentException if {@code global} is {@code null}
     */
    public static TomlSecretsConfig toTomlSecretsConfig(GlobalConfig global) {
        if (global == null) {
            throw new IllegalArgumentException("global must not be null");
        }
        global.normalize();

        TomlSecretsConfig toml = new TomlSecretsConfig();

        toml.database.mongoConnectionString = nullToBlank(global.mongoConnectionString);
        toml.database.name = nullToBlank(global.databaseName);
        toml.database.readOnly = global.isDataBaseReadOnly;
        toml.database.migrationEnabled = global.isDataBaseMigration;

        toml.externalLinks.discordUrl = global.discordUrl;
        toml.externalLinks.githubUrl = global.githubUrl;
        toml.externalLinks.donatelloUrl = global.donatelloUrl;
        toml.externalLinks.weblateUrl = global.weblateUrl;
        toml.externalLinks.discordRedVSBlueUrl = global.discordRedVSBlueUrl;

        toml.moderation.votekick.minPlayTimeMinutes = global.minPlayTimeForVotekick;
        toml.moderation.votekick.banDurationMinutes = global.voteKickBanDurationMinutes;
        toml.moderation.votekick.voteDurationSeconds = global.voteDurationSeconds;

        toml.chat.global.minPlayTimeMinutes = global.minPlayTimeForGlobalChat;
        toml.maps.voting.switchDelaySeconds = global.mapSwitchDelaySeconds;

        toml.pagination.eventsPerPage = global.eventsPerPage;
        toml.pagination.mapsPerPage = global.mapsPerPage;
        toml.pagination.commandsPerPage = global.commandsPerPage;
        toml.pagination.privateMessagesPerPage = global.privateMessagesPerPage;

        toml.messages.history.maxHistory = global.maxHistory;
        toml.messages.privateMessages.maxLength = global.privateMessageMaxLength;
        toml.messages.privateMessages.cooldownSeconds = global.privateMessageCooldownSeconds;
        toml.messages.privateMessages.unreadLimit = global.privateMessageUnreadLimit;
        toml.messages.privateMessages.blockedLimit = global.privateMessageBlockedLimit;

        toml.translation.providers = mapTomlTranslationProviders(global.translationProviders);

        toml.normalize();
        return toml;
    }

    // ------------------------------------------------------------------
    // Config helpers
    // ------------------------------------------------------------------

    private static Config.TranslationConfig mapTranslation(TomlXcoreConfig.TranslationConfig src) {
        if (src == null) {
            return new Config.TranslationConfig();
        }

        Config.TranslationConfig dst = new Config.TranslationConfig();
        dst.enabled = src.enabled;
        dst.pipeline = src.pipeline == null ? new ArrayList<>(List.of("google")) : new ArrayList<>(src.pipeline);
        dst.preserveOriginalMessageOnFailure = src.preserveOriginalMessageOnFailure;
        dst.cache = mapTranslationCache(src.cache);
        dst.metrics = mapTranslationMetrics(src.metrics);
        dst.llm = mapLlmPolicy(src.llm);
        return dst;
    }

    private static TomlXcoreConfig.TranslationConfig mapTomlTranslation(Config.TranslationConfig src) {
        if (src == null) {
            return new TomlXcoreConfig.TranslationConfig();
        }

        TomlXcoreConfig.TranslationConfig dst = new TomlXcoreConfig.TranslationConfig();
        dst.enabled = src.enabled;
        dst.pipeline = src.pipeline == null ? new ArrayList<>(List.of("google")) : new ArrayList<>(src.pipeline);
        dst.preserveOriginalMessageOnFailure = src.preserveOriginalMessageOnFailure;
        dst.cache = mapTomlTranslationCache(src.cache);
        dst.metrics = mapTomlTranslationMetrics(src.metrics);
        dst.llm = mapTomlLlmPolicy(src.llm);
        return dst;
    }

    private static Config.TranslationCacheConfig mapTranslationCache(TomlXcoreConfig.CacheConfig src) {
        if (src == null) {
            return new Config.TranslationCacheConfig();
        }
        Config.TranslationCacheConfig dst = new Config.TranslationCacheConfig();
        dst.enabled = src.enabled;
        dst.ttlSeconds = src.ttlSeconds;
        dst.maxTextLength = src.maxTextLength;
        return dst;
    }

    private static Config.TranslationMetricsConfig mapTranslationMetrics(TomlXcoreConfig.MetricsConfig src) {
        if (src == null) {
            return new Config.TranslationMetricsConfig();
        }
        Config.TranslationMetricsConfig dst = new Config.TranslationMetricsConfig();
        dst.enabled = src.enabled;
        dst.minuteBucketsEnabled = src.minuteBucketsEnabled;
        dst.minuteBucketTtlSeconds = src.minuteBucketTtlSeconds;
        return dst;
    }

    private static Config.LlmTranslationPolicyConfig mapLlmPolicy(TomlXcoreConfig.LlmConfig src) {
        if (src == null) {
            return new Config.LlmTranslationPolicyConfig();
        }
        Config.LlmTranslationPolicyConfig dst = new Config.LlmTranslationPolicyConfig();
        dst.preserveFormattingTokens = src.preserveFormattingTokens;
        dst.structuredOutputRequired = src.structuredOutputRequired;
        dst.maxInputChars = src.maxInputChars;
        dst.maxOutputChars = src.maxOutputChars;
        dst.stripControlCharacters = src.stripControlCharacters;
        return dst;
    }

    private static TomlXcoreConfig.CacheConfig mapTomlTranslationCache(Config.TranslationCacheConfig src) {
        if (src == null) {
            return new TomlXcoreConfig.CacheConfig();
        }
        TomlXcoreConfig.CacheConfig dst = new TomlXcoreConfig.CacheConfig();
        dst.enabled = src.enabled;
        dst.ttlSeconds = src.ttlSeconds;
        dst.maxTextLength = src.maxTextLength;
        return dst;
    }

    private static TomlXcoreConfig.MetricsConfig mapTomlTranslationMetrics(Config.TranslationMetricsConfig src) {
        if (src == null) {
            return new TomlXcoreConfig.MetricsConfig();
        }
        TomlXcoreConfig.MetricsConfig dst = new TomlXcoreConfig.MetricsConfig();
        dst.enabled = src.enabled;
        dst.minuteBucketsEnabled = src.minuteBucketsEnabled;
        dst.minuteBucketTtlSeconds = src.minuteBucketTtlSeconds;
        return dst;
    }

    private static TomlXcoreConfig.LlmConfig mapTomlLlmPolicy(Config.LlmTranslationPolicyConfig src) {
        if (src == null) {
            return new TomlXcoreConfig.LlmConfig();
        }
        TomlXcoreConfig.LlmConfig dst = new TomlXcoreConfig.LlmConfig();
        dst.preserveFormattingTokens = src.preserveFormattingTokens;
        dst.structuredOutputRequired = src.structuredOutputRequired;
        dst.maxInputChars = src.maxInputChars;
        dst.maxOutputChars = src.maxOutputChars;
        dst.stripControlCharacters = src.stripControlCharacters;
        return dst;
    }

    // ------------------------------------------------------------------
    // GlobalConfig helpers
    // ------------------------------------------------------------------

    private static Map<String, GlobalConfig.TranslationProviderConfig> mapTranslationProviders(
            Map<String, TomlSecretsConfig.TranslationSection.ProviderConfig> src
    ) {
        if (src == null || src.isEmpty()) {
            var defaults = new LinkedHashMap<String, GlobalConfig.TranslationProviderConfig>();
            defaults.put("google", new GlobalConfig.TranslationProviderConfig());
            return defaults;
        }

        var dst = new LinkedHashMap<String, GlobalConfig.TranslationProviderConfig>();
        src.forEach((id, provider) -> dst.put(id, mapTranslationProvider(provider)));
        return dst;
    }

    private static GlobalConfig.TranslationProviderConfig mapTranslationProvider(
            TomlSecretsConfig.TranslationSection.ProviderConfig src
    ) {
        if (src == null) {
            return new GlobalConfig.TranslationProviderConfig();
        }

        GlobalConfig.TranslationProviderConfig dst = new GlobalConfig.TranslationProviderConfig();
        dst.type = src.type;
        dst.enabled = src.enabled;
        dst.apiKey = blankToNull(src.apiKey);
        dst.baseUrl = blankToNull(src.baseUrl);
        dst.model = blankToNull(src.model);
        dst.apiMode = blankToNull(src.apiMode);
        dst.organization = blankToNull(src.organization);
        dst.project = blankToNull(src.project);
        dst.timeoutSeconds = src.timeoutSeconds;
        dst.maxRetries = src.maxRetries;
        dst.temperature = src.temperature;
        dst.supportedLanguages = copySet(src.supportedLanguages);
        return dst;
    }

    private static Map<String, TomlSecretsConfig.TranslationSection.ProviderConfig> mapTomlTranslationProviders(
            Map<String, GlobalConfig.TranslationProviderConfig> src
    ) {
        if (src == null || src.isEmpty()) {
            return defaultTomlTranslationProviders();
        }

        var dst = new LinkedHashMap<String, TomlSecretsConfig.TranslationSection.ProviderConfig>();
        src.forEach((id, provider) -> dst.put(id, mapTomlTranslationProvider(provider)));
        return dst;
    }

    private static TomlSecretsConfig.TranslationSection.ProviderConfig mapTomlTranslationProvider(
            GlobalConfig.TranslationProviderConfig src
    ) {
        if (src == null) {
            return new TomlSecretsConfig.TranslationSection.ProviderConfig();
        }

        TomlSecretsConfig.TranslationSection.ProviderConfig dst = new TomlSecretsConfig.TranslationSection.ProviderConfig();
        dst.type = src.type;
        dst.enabled = src.enabled;
        dst.apiKey = nullToBlank(src.apiKey);
        dst.baseUrl = src.baseUrl;
        dst.model = src.model;
        dst.apiMode = nullToBlank(src.apiMode);
        dst.organization = nullToBlank(src.organization);
        dst.project = nullToBlank(src.project);
        dst.timeoutSeconds = src.timeoutSeconds;
        dst.maxRetries = src.maxRetries;
        dst.temperature = src.temperature;
        dst.supportedLanguages = copyLinkedHashSet(src.supportedLanguages);
        return dst;
    }

    // ------------------------------------------------------------------
    // Shared utilities
    // ------------------------------------------------------------------

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null after normalization");
        }
        return value;
    }

    private static Set<String> copySet(Set<String> src) {
        return src == null ? new HashSet<>() : new HashSet<>(src);
    }

    private static HashSet<String> copyMutableSet(Set<String> src) {
        return src == null ? new HashSet<>() : new HashSet<>(src);
    }

    private static LinkedHashSet<String> copyLinkedHashSet(Set<String> src) {
        return src == null ? new LinkedHashSet<>() : new LinkedHashSet<>(src);
    }

    private static Map<String, TomlSecretsConfig.TranslationSection.ProviderConfig> defaultTomlTranslationProviders() {
        var defaults = new LinkedHashMap<String, TomlSecretsConfig.TranslationSection.ProviderConfig>();
        defaults.put("google", new TomlSecretsConfig.TranslationSection.ProviderConfig());
        return defaults;
    }
}
