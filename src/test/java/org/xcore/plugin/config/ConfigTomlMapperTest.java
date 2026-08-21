package org.xcore.plugin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigTomlMapperTest {

    @Test
    @DisplayName("toConfig throws IllegalArgumentException when toml is null")
    void toConfig_throws_whenTomlIsNull() {
        assertThatThrownBy(() -> ConfigTomlMapper.toConfig(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toml must not be null");
    }

    @Test
    @DisplayName("toGlobalConfig throws IllegalArgumentException when toml is null")
    void toGlobalConfig_throws_whenTomlIsNull() {
        assertThatThrownBy(() -> ConfigTomlMapper.toGlobalConfig(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toml must not be null");
    }

    @Test
    @DisplayName("toTomlXcoreConfig throws IllegalArgumentException when config is null")
    void toTomlXcoreConfig_throws_whenConfigIsNull() {
        assertThatThrownBy(() -> ConfigTomlMapper.toTomlXcoreConfig(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config must not be null");
    }

    @Test
    @DisplayName("toTomlSecretsConfig throws IllegalArgumentException when global is null")
    void toTomlSecretsConfig_throws_whenGlobalIsNull() {
        assertThatThrownBy(() -> ConfigTomlMapper.toTomlSecretsConfig(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("global must not be null");
    }

    @Test
    @DisplayName("toConfig maps default DTO to legacy Config with correct defaults")
    void toConfig_mapsDefaultDtoToLegacyConfig() {
        TomlXcoreConfig toml = new TomlXcoreConfig();

        Config config = ConfigTomlMapper.toConfig(toml);

        assertThat(config.server).isEqualTo("server");
        assertThat(config.publicHostOverride).isNull();
        assertThat(config.playerLimit).isEqualTo(30);
        assertThat(config.gameStartedTimer).isTrue();

        assertThat(config.globalConfigDirectory).isNull();

        assertThat(config.discordChannelId).isEqualTo(0L);

        assertThat(config.redisUrl).isEqualTo("redis://127.0.0.1:6379");
        assertThat(config.redisGroupPrefix).isEqualTo("xcore:cg");
        assertThat(config.redisConsumerName).isEqualTo("xcore-node");
        assertThat(config.redisReclaimEnabled).isTrue();
        assertThat(config.redisReclaimMinIdleMs).isEqualTo(15000L);
        assertThat(config.redisReclaimBatch).isEqualTo(50);
        assertThat(config.redisDlqEnabled).isTrue();
        assertThat(config.redisMaxDeliveryAttempts).isEqualTo(3);
        assertThat(config.redisDlqPrefix).isEqualTo("xcore:dlq");

        assertThat(config.disabledCommands).isNotNull().isEmpty();
        assertThat(config.disabledFeatures).isNotNull().isEmpty();

        assertThat(config.isEventHubMap).isFalse();
        assertThat(config.eventHubMapID).isEqualTo("");

        assertThat(config.translation.enabled).isTrue();
        assertThat(config.translation.pipeline).containsExactly("google");
        assertThat(config.translation.preserveOriginalMessageOnFailure).isTrue();
        assertThat(config.translation.cache.enabled).isTrue();
        assertThat(config.translation.cache.ttlSeconds).isEqualTo(1800);
        assertThat(config.translation.cache.maxTextLength).isEqualTo(500);
        assertThat(config.translation.metrics.enabled).isTrue();
        assertThat(config.translation.metrics.minuteBucketsEnabled).isTrue();
        assertThat(config.translation.metrics.minuteBucketTtlSeconds).isEqualTo(21600);
        assertThat(config.translation.llm.preserveFormattingTokens).isTrue();
        assertThat(config.translation.llm.structuredOutputRequired).isTrue();
        assertThat(config.translation.llm.maxInputChars).isEqualTo(500);
        assertThat(config.translation.llm.maxOutputChars).isEqualTo(1200);
        assertThat(config.translation.llm.stripControlCharacters).isTrue();
    }

    @Test
    @DisplayName("toConfig maps populated DTO to legacy Config with correct values")
    void toConfig_mapsPopulatedDtoToLegacyConfig() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.server.name = "mini-pvp";
        toml.server.publicHostOverride = "192.168.1.1";
        toml.server.playerLimit = 50;
        toml.server.gameStartedTimer = false;

        toml.paths.globalConfigDirectory = "/opt/xcore/global";

        toml.discord.channelId = "123456789";

        toml.transport.redis.url = "redis://redis.example.com:6379";
        toml.transport.redis.groupPrefix = "xcore:prod";
        toml.transport.redis.consumerName = "xcore-prod-1";
        toml.transport.redis.reclaim.enabled = false;
        toml.transport.redis.reclaim.minIdleMs = 30000L;
        toml.transport.redis.reclaim.batch = 100;
        toml.transport.redis.dlq.enabled = false;
        toml.transport.redis.dlq.maxDeliveryAttempts = 5;
        toml.transport.redis.dlq.prefix = "xcore:dlq:prod";

        toml.runtime.disabledCommands = Set.of("rtv", "maps");
        toml.runtime.disabledFeatures = Set.of("chat");

        toml.eventHub.enabled = true;
        toml.eventHub.mapId = "event-hub-01";

        toml.translation.enabled = false;
        toml.translation.pipeline = List.of("google", "llm");
        toml.translation.preserveOriginalMessageOnFailure = false;
        toml.translation.cache.enabled = false;
        toml.translation.cache.ttlSeconds = 3600;
        toml.translation.cache.maxTextLength = 1000;
        toml.translation.metrics.enabled = false;
        toml.translation.metrics.minuteBucketsEnabled = false;
        toml.translation.metrics.minuteBucketTtlSeconds = 43200;
        toml.translation.llm.preserveFormattingTokens = false;
        toml.translation.llm.structuredOutputRequired = false;
        toml.translation.llm.maxInputChars = 1000;
        toml.translation.llm.maxOutputChars = 2000;
        toml.translation.llm.stripControlCharacters = false;

        Config config = ConfigTomlMapper.toConfig(toml);

        assertThat(config.server).isEqualTo("mini-pvp");
        assertThat(config.publicHostOverride).isEqualTo("192.168.1.1");
        assertThat(config.playerLimit).isEqualTo(50);
        assertThat(config.gameStartedTimer).isFalse();

        assertThat(config.globalConfigDirectory).isEqualTo("/opt/xcore/global");

        assertThat(config.discordChannelId).isEqualTo(123456789L);

        assertThat(config.redisUrl).isEqualTo("redis://redis.example.com:6379");
        assertThat(config.redisGroupPrefix).isEqualTo("xcore:prod");
        assertThat(config.redisConsumerName).isEqualTo("xcore-prod-1");
        assertThat(config.redisReclaimEnabled).isFalse();
        assertThat(config.redisReclaimMinIdleMs).isEqualTo(30000L);
        assertThat(config.redisReclaimBatch).isEqualTo(100);
        assertThat(config.redisDlqEnabled).isFalse();
        assertThat(config.redisMaxDeliveryAttempts).isEqualTo(5);
        assertThat(config.redisDlqPrefix).isEqualTo("xcore:dlq:prod");

        assertThat(config.disabledCommands).containsExactlyInAnyOrder("rtv", "maps");
        assertThat(config.disabledFeatures).containsExactly("chat");

        assertThat(config.isEventHubMap).isTrue();
        assertThat(config.eventHubMapID).isEqualTo("event-hub-01");

        assertThat(config.translation.enabled).isFalse();
        assertThat(config.translation.pipeline).containsExactly("google", "llm");
        assertThat(config.translation.preserveOriginalMessageOnFailure).isFalse();
        assertThat(config.translation.cache.enabled).isFalse();
        assertThat(config.translation.cache.ttlSeconds).isEqualTo(3600);
        assertThat(config.translation.cache.maxTextLength).isEqualTo(1000);
        assertThat(config.translation.metrics.enabled).isFalse();
        assertThat(config.translation.metrics.minuteBucketsEnabled).isFalse();
        assertThat(config.translation.metrics.minuteBucketTtlSeconds).isEqualTo(43200);
        assertThat(config.translation.llm.preserveFormattingTokens).isFalse();
        assertThat(config.translation.llm.structuredOutputRequired).isFalse();
        assertThat(config.translation.llm.maxInputChars).isEqualTo(1000);
        assertThat(config.translation.llm.maxOutputChars).isEqualTo(2000);
        assertThat(config.translation.llm.stripControlCharacters).isFalse();
    }

    @Test
    @DisplayName("toConfig preserves 19-digit Discord snowflake from TOML string")
    void toConfig_preservesDiscordSnowflakeFromTomlString() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.discord.channelId = "1099650307396476958";

        Config config = ConfigTomlMapper.toConfig(toml);

        assertThat(config.discordChannelId).isEqualTo(1099650307396476958L);
    }

    @Test
    @DisplayName("toConfig rejects invalid Discord snowflake")
    void toConfig_rejectsInvalidDiscordSnowflake() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.discord.channelId = "not-a-snowflake";

        assertThatThrownBy(() -> ConfigTomlMapper.toConfig(toml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("discord.channel_id must contain only decimal digits");
    }

    @Test
    @DisplayName("toGlobalConfig maps default DTO to legacy GlobalConfig with correct defaults")
    void toGlobalConfig_mapsDefaultDtoToLegacyGlobalConfig() {
        TomlSecretsConfig toml = new TomlSecretsConfig();

        GlobalConfig global = ConfigTomlMapper.toGlobalConfig(toml);

        assertThat(global.mongoConnectionString).isNull(); // blankToNull
        assertThat(global.databaseName).isNull(); // blankToNull
        assertThat(global.isDataBaseReadOnly).isFalse();
        assertThat(global.isDataBaseMigration).isFalse();

        assertThat(global.discordUrl).isEqualTo("https://discord.gg/RUMCCa9QAC");
        assertThat(global.githubUrl).isEqualTo("https://github.com/XCore-mindustry/");
        assertThat(global.donatelloUrl).isEqualTo("https://donatello.to/xcore");
        assertThat(global.weblateUrl).isEqualTo("https://xcore.eradication.fun/");
        assertThat(global.discordRedVSBlueUrl).isEqualTo("https://discord.gg/UdnuFetcNt");

        assertThat(global.minPlayTimeForVotekick).isEqualTo(60);
        assertThat(global.voteKickBanDurationMinutes).isEqualTo(30);
        assertThat(global.voteDurationSeconds).isEqualTo(60.0f);
        assertThat(global.minPlayTimeForGlobalChat).isEqualTo(240);
        assertThat(global.mapSwitchDelaySeconds).isEqualTo(10);

        assertThat(global.eventsPerPage).isEqualTo(10);
        assertThat(global.mapsPerPage).isEqualTo(10);
        assertThat(global.commandsPerPage).isEqualTo(6);
        assertThat(global.privateMessagesPerPage).isEqualTo(10);

        assertThat(global.maxHistory).isEqualTo(16);
        assertThat(global.privateMessageMaxLength).isEqualTo(300);
        assertThat(global.privateMessageCooldownSeconds).isEqualTo(10);
        assertThat(global.privateMessageUnreadLimit).isEqualTo(30);
        assertThat(global.privateMessageBlockedLimit).isEqualTo(100);

        assertThat(global.translationProviders).containsOnlyKeys("google");
        GlobalConfig.TranslationProviderConfig google = global.translationProviders.get("google");
        assertThat(google.type).isEqualTo("google");
        assertThat(google.enabled).isTrue();
        assertThat(google.apiKey).isNull(); // blankToNull
        assertThat(google.baseUrl).isNull(); // blankToNull
        assertThat(google.model).isNull(); // blankToNull
        assertThat(google.apiMode).isNull(); // blankToNull
        assertThat(google.organization).isNull(); // blankToNull
        assertThat(google.project).isNull(); // blankToNull
        assertThat(google.timeoutSeconds).isEqualTo(15);
        assertThat(google.maxRetries).isEqualTo(1);
        assertThat(google.temperature).isEqualTo(0.0);
        assertThat(google.supportedLanguages).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("toGlobalConfig maps populated DTO to legacy GlobalConfig with correct values")
    void toGlobalConfig_mapsPopulatedDtoToLegacyGlobalConfig() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        toml.database.mongoConnectionString = "mongodb://localhost:27017";
        toml.database.name = "xcore";
        toml.database.readOnly = true;
        toml.database.migrationEnabled = true;

        toml.externalLinks.discordUrl = "https://discord.gg/test";
        toml.externalLinks.githubUrl = "https://github.com/test/";
        toml.externalLinks.donatelloUrl = "https://donatello.to/test";
        toml.externalLinks.weblateUrl = "https://test.example.com/";
        toml.externalLinks.discordRedVSBlueUrl = "https://discord.gg/test2";

        toml.moderation.votekick.minPlayTimeMinutes = 120;
        toml.moderation.votekick.banDurationMinutes = 60;
        toml.moderation.votekick.voteDurationSeconds = 120.0f;

        toml.chat.global.minPlayTimeMinutes = 300;

        toml.maps.voting.switchDelaySeconds = 20;

        toml.pagination.eventsPerPage = 20;
        toml.pagination.mapsPerPage = 15;
        toml.pagination.commandsPerPage = 10;
        toml.pagination.privateMessagesPerPage = 20;

        toml.messages.history.maxHistory = 32;
        toml.messages.privateMessages.maxLength = 500;
        toml.messages.privateMessages.cooldownSeconds = 20;
        toml.messages.privateMessages.unreadLimit = 50;
        toml.messages.privateMessages.blockedLimit = 200;

        TomlSecretsConfig.TranslationSection.ProviderConfig customProvider = new TomlSecretsConfig.TranslationSection.ProviderConfig();
        customProvider.type = "llm";
        customProvider.enabled = false;
        customProvider.apiKey = "secret-key";
        customProvider.baseUrl = "https://api.custom.com/v1";
        customProvider.model = "custom-model";
        customProvider.apiMode = "chat";
        customProvider.organization = "xcore-org";
        customProvider.project = "xcore-project";
        customProvider.timeoutSeconds = 30;
        customProvider.maxRetries = 3;
        customProvider.temperature = 0.5;
        customProvider.supportedLanguages = Set.of("en", "ru");
        toml.translation.providers = new LinkedHashMap<>(Map.of("custom", customProvider));

        GlobalConfig global = ConfigTomlMapper.toGlobalConfig(toml);

        assertThat(global.mongoConnectionString).isEqualTo("mongodb://localhost:27017");
        assertThat(global.databaseName).isEqualTo("xcore");
        assertThat(global.isDataBaseReadOnly).isTrue();
        assertThat(global.isDataBaseMigration).isTrue();

        assertThat(global.discordUrl).isEqualTo("https://discord.gg/test");
        assertThat(global.githubUrl).isEqualTo("https://github.com/test/");
        assertThat(global.donatelloUrl).isEqualTo("https://donatello.to/test");
        assertThat(global.weblateUrl).isEqualTo("https://test.example.com/");
        assertThat(global.discordRedVSBlueUrl).isEqualTo("https://discord.gg/test2");

        assertThat(global.minPlayTimeForVotekick).isEqualTo(120);
        assertThat(global.voteKickBanDurationMinutes).isEqualTo(60);
        assertThat(global.voteDurationSeconds).isEqualTo(120.0f);
        assertThat(global.minPlayTimeForGlobalChat).isEqualTo(300);
        assertThat(global.mapSwitchDelaySeconds).isEqualTo(20);

        assertThat(global.eventsPerPage).isEqualTo(20);
        assertThat(global.mapsPerPage).isEqualTo(15);
        assertThat(global.commandsPerPage).isEqualTo(10);
        assertThat(global.privateMessagesPerPage).isEqualTo(20);

        assertThat(global.maxHistory).isEqualTo(32);
        assertThat(global.privateMessageMaxLength).isEqualTo(500);
        assertThat(global.privateMessageCooldownSeconds).isEqualTo(20);
        assertThat(global.privateMessageUnreadLimit).isEqualTo(50);
        assertThat(global.privateMessageBlockedLimit).isEqualTo(200);

        assertThat(global.translationProviders).containsOnlyKeys("custom");
        GlobalConfig.TranslationProviderConfig custom = global.translationProviders.get("custom");
        assertThat(custom.type).isEqualTo("llm");
        assertThat(custom.enabled).isFalse();
        assertThat(custom.apiKey).isEqualTo("secret-key");
        assertThat(custom.baseUrl).isEqualTo("https://api.custom.com/v1");
        assertThat(custom.model).isEqualTo("custom-model");
        assertThat(custom.apiMode).isEqualTo("chat");
        assertThat(custom.organization).isEqualTo("xcore-org");
        assertThat(custom.project).isEqualTo("xcore-project");
        assertThat(custom.timeoutSeconds).isEqualTo(30);
        assertThat(custom.maxRetries).isEqualTo(3);
        assertThat(custom.temperature).isEqualTo(0.5);
        assertThat(custom.supportedLanguages).containsExactlyInAnyOrder("en", "ru");
    }

    @Test
    @DisplayName("toGlobalConfig converts blank optional strings to null")
    void toGlobalConfig_convertsBlankOptionalStringsToNull() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        toml.database.mongoConnectionString = "   ";
        toml.database.name = "\t";

        TomlSecretsConfig.TranslationSection.ProviderConfig provider = toml.translation.providers.get("google");
        provider.apiKey = "";
        provider.apiMode = "  ";
        provider.organization = "";
        provider.project = "   ";

        GlobalConfig global = ConfigTomlMapper.toGlobalConfig(toml);

        assertThat(global.mongoConnectionString).isNull();
        assertThat(global.databaseName).isNull();

        GlobalConfig.TranslationProviderConfig mappedProvider = global.translationProviders.get("google");
        assertThat(mappedProvider.apiKey).isNull();
        assertThat(mappedProvider.apiMode).isNull();
        assertThat(mappedProvider.organization).isNull();
        assertThat(mappedProvider.project).isNull();
    }

    @Test
    @DisplayName("toConfig handles null nested sections by normalizing first")
    void toConfig_handlesNullNestedSections_byNormalizingFirst() {
        TomlXcoreConfig toml = new TomlXcoreConfig();
        toml.server = null;
        toml.translation = null;

        Config config = ConfigTomlMapper.toConfig(toml);

        assertThat(config.server).isEqualTo("server");
        assertThat(config.translation).isNotNull();
        assertThat(config.translation.pipeline).containsExactly("google");
    }

    @Test
    @DisplayName("toGlobalConfig handles null nested sections by normalizing first")
    void toGlobalConfig_handlesNullNestedSections_byNormalizingFirst() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        toml.moderation = null;
        toml.translation = null;

        GlobalConfig global = ConfigTomlMapper.toGlobalConfig(toml);

        assertThat(global.minPlayTimeForVotekick).isEqualTo(60);
        assertThat(global.translationProviders).containsOnlyKeys("google");
    }

    @Test
    @DisplayName("toTomlXcoreConfig maps populated legacy Config to TOML DTO")
    void toTomlXcoreConfig_mapsPopulatedLegacyConfig() {
        Config config = new Config();
        config.server = "event";
        config.publicHostOverride = null;
        config.playerLimit = 64;
        config.gameStartedTimer = false;
        config.globalConfigDirectory = "/srv/xcore/global";
        config.discordChannelId = 55L;
        config.redisUrl = "redis://prod:6379";
        config.redisGroupPrefix = "xcore:prod";
        config.redisConsumerName = "consumer-a";
        config.redisReclaimEnabled = false;
        config.redisReclaimMinIdleMs = 32000L;
        config.redisReclaimBatch = 77;
        config.redisDlqEnabled = false;
        config.redisMaxDeliveryAttempts = 7;
        config.redisDlqPrefix = "xcore:dlq:prod";
        config.disabledCommands = Set.of("maps", "rtv");
        config.disabledFeatures = Set.of("translation");
        config.isEventHubMap = true;
        config.eventHubMapID = "hub-map";
        config.translation.enabled = false;
        config.translation.pipeline = List.of("google", "llm");
        config.translation.preserveOriginalMessageOnFailure = false;
        config.translation.cache.enabled = false;
        config.translation.cache.ttlSeconds = 999;
        config.translation.cache.maxTextLength = 1234;
        config.translation.metrics.enabled = false;
        config.translation.metrics.minuteBucketsEnabled = false;
        config.translation.metrics.minuteBucketTtlSeconds = 5678;
        config.translation.llm.preserveFormattingTokens = false;
        config.translation.llm.structuredOutputRequired = false;
        config.translation.llm.maxInputChars = 111;
        config.translation.llm.maxOutputChars = 222;
        config.translation.llm.stripControlCharacters = false;

        TomlXcoreConfig toml = ConfigTomlMapper.toTomlXcoreConfig(config);

        assertThat(toml.server.name).isEqualTo("event");
        assertThat(toml.server.publicHostOverride).isNull();
        assertThat(toml.server.playerLimit).isEqualTo(64);
        assertThat(toml.server.gameStartedTimer).isFalse();
        assertThat(toml.paths.globalConfigDirectory).isEqualTo("/srv/xcore/global");
        assertThat(toml.discord.channelId).isEqualTo("55");
        assertThat(toml.transport.redis.url).isEqualTo("redis://prod:6379");
        assertThat(toml.transport.redis.groupPrefix).isEqualTo("xcore:prod");
        assertThat(toml.transport.redis.consumerName).isEqualTo("consumer-a");
        assertThat(toml.transport.redis.reclaim.enabled).isFalse();
        assertThat(toml.transport.redis.reclaim.minIdleMs).isEqualTo(32000L);
        assertThat(toml.transport.redis.reclaim.batch).isEqualTo(77);
        assertThat(toml.transport.redis.dlq.enabled).isFalse();
        assertThat(toml.transport.redis.dlq.maxDeliveryAttempts).isEqualTo(7);
        assertThat(toml.transport.redis.dlq.prefix).isEqualTo("xcore:dlq:prod");
        assertThat(toml.runtime.disabledCommands).containsExactlyInAnyOrder("maps", "rtv");
        assertThat(toml.runtime.disabledFeatures).containsExactly("translation");
        assertThat(toml.eventHub.enabled).isTrue();
        assertThat(toml.eventHub.mapId).isEqualTo("hub-map");
        assertThat(toml.translation.pipeline).containsExactly("google", "llm");
        assertThat(toml.translation.llm.maxOutputChars).isEqualTo(222);
    }

    @Test
    @DisplayName("toTomlSecretsConfig maps populated legacy GlobalConfig to TOML DTO")
    void toTomlSecretsConfig_mapsPopulatedLegacyGlobalConfig() {
        GlobalConfig global = new GlobalConfig();
        global.mongoConnectionString = null;
        global.databaseName = null;
        global.isDataBaseReadOnly = true;
        global.isDataBaseMigration = true;
        global.discordUrl = "https://discord.gg/custom";
        global.githubUrl = "https://github.com/custom";
        global.donatelloUrl = "https://donate/custom";
        global.weblateUrl = "https://weblate/custom";
        global.discordRedVSBlueUrl = "https://discord.gg/rvb";
        global.minPlayTimeForVotekick = 1;
        global.voteKickBanDurationMinutes = 2;
        global.voteDurationSeconds = 3.5f;
        global.minPlayTimeForGlobalChat = 4;
        global.mapSwitchDelaySeconds = 5;
        global.eventsPerPage = 6;
        global.mapsPerPage = 7;
        global.commandsPerPage = 8;
        global.privateMessagesPerPage = 9;
        global.maxHistory = 10;
        global.privateMessageMaxLength = 11;
        global.privateMessageCooldownSeconds = 12;
        global.privateMessageUnreadLimit = 13;
        global.privateMessageBlockedLimit = 14;

        GlobalConfig.TranslationProviderConfig provider = new GlobalConfig.TranslationProviderConfig();
        provider.type = "llm";
        provider.enabled = false;
        provider.apiKey = null;
        provider.baseUrl = "https://api.provider/v1";
        provider.model = "gpt-x";
        provider.apiMode = null;
        provider.organization = "org";
        provider.project = null;
        provider.timeoutSeconds = 15;
        provider.maxRetries = 16;
        provider.temperature = 0.7;
        provider.supportedLanguages = Set.of("en", "ru");
        global.translationProviders = new LinkedHashMap<>(Map.of("ai", provider));

        TomlSecretsConfig toml = ConfigTomlMapper.toTomlSecretsConfig(global);

        assertThat(toml.database.mongoConnectionString).isEmpty();
        assertThat(toml.database.name).isEmpty();
        assertThat(toml.database.readOnly).isTrue();
        assertThat(toml.database.migrationEnabled).isTrue();
        assertThat(toml.externalLinks.discordUrl).isEqualTo("https://discord.gg/custom");
        assertThat(toml.externalLinks.githubUrl).isEqualTo("https://github.com/custom");
        assertThat(toml.externalLinks.donatelloUrl).isEqualTo("https://donate/custom");
        assertThat(toml.externalLinks.weblateUrl).isEqualTo("https://weblate/custom");
        assertThat(toml.externalLinks.discordRedVSBlueUrl).isEqualTo("https://discord.gg/rvb");
        assertThat(toml.moderation.votekick.minPlayTimeMinutes).isEqualTo(1);
        assertThat(toml.moderation.votekick.banDurationMinutes).isEqualTo(2);
        assertThat(toml.moderation.votekick.voteDurationSeconds).isEqualTo(3.5f);
        assertThat(toml.chat.global.minPlayTimeMinutes).isEqualTo(4);
        assertThat(toml.maps.voting.switchDelaySeconds).isEqualTo(5);
        assertThat(toml.pagination.eventsPerPage).isEqualTo(6);
        assertThat(toml.pagination.mapsPerPage).isEqualTo(7);
        assertThat(toml.pagination.commandsPerPage).isEqualTo(8);
        assertThat(toml.pagination.privateMessagesPerPage).isEqualTo(9);
        assertThat(toml.messages.history.maxHistory).isEqualTo(10);
        assertThat(toml.messages.privateMessages.maxLength).isEqualTo(11);
        assertThat(toml.messages.privateMessages.cooldownSeconds).isEqualTo(12);
        assertThat(toml.messages.privateMessages.unreadLimit).isEqualTo(13);
        assertThat(toml.messages.privateMessages.blockedLimit).isEqualTo(14);
        assertThat(toml.translation.providers).containsOnlyKeys("ai");

        TomlSecretsConfig.TranslationSection.ProviderConfig mappedProvider = toml.translation.providers.get("ai");
        assertThat(mappedProvider.type).isEqualTo("llm");
        assertThat(mappedProvider.enabled).isFalse();
        assertThat(mappedProvider.apiKey).isEmpty();
        assertThat(mappedProvider.baseUrl).isEqualTo("https://api.provider/v1");
        assertThat(mappedProvider.model).isEqualTo("gpt-x");
        assertThat(mappedProvider.apiMode).isEmpty();
        assertThat(mappedProvider.organization).isEqualTo("org");
        assertThat(mappedProvider.project).isEmpty();
        assertThat(mappedProvider.timeoutSeconds).isEqualTo(15);
        assertThat(mappedProvider.maxRetries).isEqualTo(16);
        assertThat(mappedProvider.temperature).isEqualTo(0.7);
        assertThat(mappedProvider.supportedLanguages).containsExactlyInAnyOrder("en", "ru");
    }
}
