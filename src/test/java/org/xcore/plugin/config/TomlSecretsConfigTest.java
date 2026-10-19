package org.xcore.plugin.config;

import arc.files.Fi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TomlSecretsConfigTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("fresh instance has defaults matching legacy GlobalConfig")
    void freshInstance_hasDefaultsMatchingLegacyGlobalConfig() {
        TomlSecretsConfig toml = new TomlSecretsConfig();

        assertThat(toml.version).isEqualTo(1);

        assertThat(toml.database.mongoConnectionString).isEqualTo("");
        assertThat(toml.database.name).isEqualTo("");
        assertThat(toml.database.readOnly).isFalse();
        assertThat(toml.database.migrationEnabled).isFalse();

        assertThat(toml.externalLinks.discordUrl).isEqualTo("https://discord.gg/RUMCCa9QAC");
        assertThat(toml.externalLinks.githubUrl).isEqualTo("https://github.com/XCore-mindustry/");
        assertThat(toml.externalLinks.donatelloUrl).isEqualTo("https://donatello.to/xcore");
        assertThat(toml.externalLinks.weblateUrl).isEqualTo("https://xcore.eradication.fun/");
        assertThat(toml.externalLinks.discordRedVSBlueUrl).isEqualTo("https://discord.gg/UdnuFetcNt");

        assertThat(toml.moderation.votekick.minPlayTimeMinutes).isEqualTo(60);
        assertThat(toml.moderation.votekick.banDurationMinutes).isEqualTo(30);
        assertThat(toml.moderation.votekick.voteDurationSeconds).isEqualTo(60.0f);

        assertThat(toml.chat.global.minPlayTimeMinutes).isEqualTo(240);

        assertThat(toml.maps.voting.switchDelaySeconds).isEqualTo(10);

        assertThat(toml.pagination.eventsPerPage).isEqualTo(10);
        assertThat(toml.pagination.mapsPerPage).isEqualTo(10);
        assertThat(toml.pagination.commandsPerPage).isEqualTo(6);
        assertThat(toml.pagination.privateMessagesPerPage).isEqualTo(10);

        assertThat(toml.messages.history.maxHistory).isEqualTo(16);
        assertThat(toml.messages.privateMessages.maxLength).isEqualTo(300);
        assertThat(toml.messages.privateMessages.cooldownSeconds).isEqualTo(10);
        assertThat(toml.messages.privateMessages.unreadLimit).isEqualTo(30);
        assertThat(toml.messages.privateMessages.blockedLimit).isEqualTo(100);

        assertThat(toml.translation.providers).containsOnlyKeys("google");
        TomlSecretsConfig.TranslationSection.ProviderConfig google = toml.translation.providers.get("google");
        assertThat(google.type).isEqualTo("google");
        assertThat(google.enabled).isTrue();
        assertThat(google.apiKey).isEqualTo("");
        assertThat(google.baseUrl).isEqualTo("");
        assertThat(google.model).isEqualTo("");
        assertThat(google.apiMode).isEqualTo("");
        assertThat(google.organization).isEqualTo("");
        assertThat(google.project).isEqualTo("");
        assertThat(google.timeoutSeconds).isEqualTo(15);
        assertThat(google.maxRetries).isEqualTo(1);
        assertThat(google.temperature).isEqualTo(0.0);
        assertThat(google.supportedLanguages).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("normalize repairs null nested sections")
    void normalize_repairsNullNestedSections() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        toml.database = null;
        toml.externalLinks = null;
        toml.moderation = null;
        toml.chat = null;
        toml.maps = null;
        toml.pagination = null;
        toml.messages = null;
        toml.translation = null;
        toml.cases = null;

        toml.normalize();

        assertThat(toml.database).isNotNull();
        assertThat(toml.externalLinks).isNotNull();
        assertThat(toml.moderation).isNotNull();
        assertThat(toml.chat).isNotNull();
        assertThat(toml.maps).isNotNull();
        assertThat(toml.pagination).isNotNull();
        assertThat(toml.messages).isNotNull();
        assertThat(toml.translation).isNotNull();
        assertThat(toml.cases).isNotNull();

        assertThat(toml.moderation.votekick).isNotNull();
        assertThat(toml.chat.global).isNotNull();
        assertThat(toml.maps.voting).isNotNull();
        assertThat(toml.messages.history).isNotNull();
        assertThat(toml.messages.privateMessages).isNotNull();
        assertThat(toml.translation.providers).containsOnlyKeys("google");
    }

    @Test
    @DisplayName("normalize creates default google provider when empty")
    void normalize_createsDefaultGoogleProvider_whenEmpty() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        toml.translation.providers.clear();

        toml.normalize();

        assertThat(toml.translation.providers).containsOnlyKeys("google");
        assertThat(toml.translation.providers.get("google").type).isEqualTo("google");
    }

    @Test
    @DisplayName("normalize repairs invalid provider fields")
    void normalize_repairsInvalidProviderFields() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        TomlSecretsConfig.TranslationSection.ProviderConfig provider = new TomlSecretsConfig.TranslationSection.ProviderConfig();
        provider.type = "";
        provider.baseUrl = "";
        provider.model = "";
        provider.timeoutSeconds = 0;
        provider.maxRetries = -1;
        provider.supportedLanguages = null;
        toml.translation.providers = new LinkedHashMap<>(Map.of("llm", provider));

        toml.normalize();

        TomlSecretsConfig.TranslationSection.ProviderConfig normalized = toml.translation.providers.get("llm");
        assertThat(normalized.type).isEqualTo("google");
        assertThat(normalized.baseUrl).isEqualTo("");
        assertThat(normalized.model).isEqualTo("");
        assertThat(normalized.timeoutSeconds).isEqualTo(15);
        assertThat(normalized.maxRetries).isEqualTo(1);
        assertThat(normalized.supportedLanguages).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("normalize preserves blank optional strings")
    void normalize_preservesBlankOptionalStrings() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        TomlSecretsConfig.TranslationSection.ProviderConfig provider = toml.translation.providers.get("google");
        provider.apiKey = "";
        provider.apiMode = "   ";
        provider.organization = "\t";
        provider.project = "";

        toml.normalize();

        assertThat(provider.apiKey).isEqualTo("");
        assertThat(provider.apiMode).isEqualTo("   ");
        assertThat(provider.organization).isEqualTo("\t");
        assertThat(provider.project).isEqualTo("");
    }

    @Test
    @DisplayName("validate throws clear error when required fields are missing")
    void validate_throwsClearErrorWhenRequiredFieldsAreMissing() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        Fi secretsConfigFile = new Fi(tempDir.resolve("secrets.toml").toFile());

        assertThatThrownBy(() -> toml.validate(secretsConfigFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secrets.toml")
                .hasMessageContaining("database.mongo_connection_string")
                .hasMessageContaining("database.name");
    }

    @Test
    @DisplayName("validate accepts valid configuration")
    void validate_acceptsValidConfiguration() {
        TomlSecretsConfig toml = new TomlSecretsConfig();
        toml.database.mongoConnectionString = "mongodb://localhost:27017";
        toml.database.name = "xcore";
        Fi secretsConfigFile = new Fi(tempDir.resolve("secrets.toml").toFile());

        assertThatCode(() -> toml.validate(secretsConfigFile))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rating.seasons has documented defaults, binds from TOML and repairs blank values")
    void ratingSeasons_defaultsBindingAndRepair() throws Exception {
        TomlSecretsConfig defaults = new TomlSecretsConfig();
        assertThat(defaults.rating.seasons.length).isEqualTo("3mo");
        assertThat(defaults.rating.seasons.timezone).isEqualTo("UTC");
        assertThat(defaults.rating.seasons.noticeThresholds).containsExactly("7d", "3d", "24h", "1h");
        assertThat(defaults.rating.seasons.settlementGrace).isEqualTo("5m");
        assertThat(defaults.rating.seasons.podiumSize).isEqualTo(10);
        assertThat(defaults.rating.seasons.podiumMinMatches).isEqualTo(10);
        assertThat(defaults.rating.seasons.reset).isEqualTo("soft");
        assertThat(defaults.rating.seasons.resetCarry).isEqualTo(0.5);

        TomlSecretsConfig bound = PluginConfigLoader.lenientTomlMapper().readValue("""
                [rating.seasons]
                length = "6w"
                timezone = "Europe/Kyiv"
                notice_thresholds = ["2d"]
                settlement_grace = ""
                podium_size = 0
                podium_min_matches = 3
                reset = "hard"
                reset_carry = 0.25
                """, TomlSecretsConfig.class);
        bound.normalize();

        assertThat(bound.rating.seasons.length).isEqualTo("6w");
        assertThat(bound.rating.seasons.timezone).isEqualTo("Europe/Kyiv");
        assertThat(bound.rating.seasons.noticeThresholds).containsExactly("2d");
        assertThat(bound.rating.seasons.settlementGrace).isEqualTo("5m");
        assertThat(bound.rating.seasons.podiumSize).isEqualTo(10);
        assertThat(bound.rating.seasons.podiumMinMatches).isEqualTo(3);
        assertThat(bound.rating.seasons.reset).isEqualTo("hard");
        assertThat(bound.rating.seasons.resetCarry).isEqualTo(0.25);

        TomlSecretsConfig template = PluginConfigLoader.lenientTomlMapper()
                .readValue(ConfigTomlTemplateWriter.defaultSecretsTomlContent(), TomlSecretsConfig.class);
        assertThat(template.rating.seasons).usingRecursiveComparison().isEqualTo(defaults.rating.seasons);
    }

    @Test
    @DisplayName("cases and maps.checks have documented defaults with player reports switched off")
    void casesAndMapChecks_defaults() {
        TomlSecretsConfig defaults = new TomlSecretsConfig();

        assertThat(defaults.cases.reports.enabled).isFalse();
        assertThat(defaults.cases.reports.cooldownSeconds).isEqualTo(60);
        assertThat(defaults.cases.reports.sameTargetCooldownMinutes).isEqualTo(30);
        assertThat(defaults.cases.reports.dailyLimit).isEqualTo(10);
        assertThat(defaults.cases.reports.networkBurstLimit).isEqualTo(6);
        assertThat(defaults.cases.reports.minPlayTimeMinutes).isEqualTo(0);
        assertThat(defaults.cases.reports.chatLines).isEqualTo(30);
        assertThat(defaults.cases.reports.chatWindowMinutes).isEqualTo(10);
        assertThat(defaults.cases.reports.blockWindowSeconds).isEqualTo(600);
        assertThat(defaults.cases.reports.recentLeaverMinutes).isEqualTo(10);
        assertThat(defaults.cases.notices.maxPerJoin).isEqualTo(5);

        assertThat(defaults.maps.checks.maxFileBytes).isEqualTo(4194304);
        assertThat(defaults.maps.checks.maxTiles).isEqualTo(360000);
        assertThat(defaults.maps.checks.profiles).containsOnlyKeys("tower-defense");
        TomlSecretsConfig.MapsConfig.ChecksConfig.ProfileConfig towerDefense =
                defaults.maps.checks.profiles.get("tower-defense");
        assertThat(towerDefense.roadFloor).isEqualTo("metal-tiles-2");
        assertThat(towerDefense.borderWall).isEqualTo("metal-wall-2");
        assertThat(towerDefense.borderTolerance).isEqualTo(0.05);
        assertThat(towerDefense.requireDropZoneRadiusZero).isTrue();
    }

    @Test
    @DisplayName("a secrets file without cases or maps.checks keeps the defaults")
    void casesAndMapChecks_absentSectionsKeepDefaults() throws Exception {
        TomlSecretsConfig bound = PluginConfigLoader.lenientTomlMapper().readValue("""
                [database]
                name = "xcore"
                """, TomlSecretsConfig.class);
        bound.normalize();

        assertThat(bound.cases.reports.enabled).isFalse();
        assertThat(bound.cases.reports.cooldownSeconds).isEqualTo(60);
        assertThat(bound.cases.notices.maxPerJoin).isEqualTo(5);
        assertThat(bound.maps.checks.maxFileBytes).isEqualTo(4194304);
        assertThat(bound.maps.checks.maxTiles).isEqualTo(360000);
        assertThat(bound.maps.checks.profiles.get("tower-defense").roadFloor).isEqualTo("metal-tiles-2");
    }

    @Test
    @DisplayName("cases and maps.checks bind values from TOML")
    void casesAndMapChecks_bindFromToml() throws Exception {
        TomlSecretsConfig bound = PluginConfigLoader.lenientTomlMapper().readValue("""
                [cases.reports]
                enabled = true
                cooldown_seconds = 5
                same_target_cooldown_minutes = 15
                daily_limit = 3
                network_burst_limit = 2
                min_play_time_minutes = 30
                chat_lines = 12
                chat_window_minutes = 4
                block_window_seconds = 120
                recent_leaver_minutes = 7

                [cases.notices]
                max_per_join = 2

                [maps.checks]
                max_file_bytes = 1024
                max_tiles = 2000

                [maps.checks.profiles.tower-defense]
                road_floor = ""
                border_wall = "copper-wall"
                border_tolerance = 0.2
                require_drop_zone_radius_zero = false
                """, TomlSecretsConfig.class);
        bound.normalize();

        assertThat(bound.cases.reports.enabled).isTrue();
        assertThat(bound.cases.reports.cooldownSeconds).isEqualTo(5);
        assertThat(bound.cases.reports.sameTargetCooldownMinutes).isEqualTo(15);
        assertThat(bound.cases.reports.dailyLimit).isEqualTo(3);
        assertThat(bound.cases.reports.networkBurstLimit).isEqualTo(2);
        assertThat(bound.cases.reports.minPlayTimeMinutes).isEqualTo(30);
        assertThat(bound.cases.reports.chatLines).isEqualTo(12);
        assertThat(bound.cases.reports.chatWindowMinutes).isEqualTo(4);
        assertThat(bound.cases.reports.blockWindowSeconds).isEqualTo(120);
        assertThat(bound.cases.reports.recentLeaverMinutes).isEqualTo(7);
        assertThat(bound.cases.notices.maxPerJoin).isEqualTo(2);

        assertThat(bound.maps.checks.maxFileBytes).isEqualTo(1024);
        assertThat(bound.maps.checks.maxTiles).isEqualTo(2000);
        TomlSecretsConfig.MapsConfig.ChecksConfig.ProfileConfig towerDefense =
                bound.maps.checks.profiles.get("tower-defense");
        assertThat(towerDefense.roadFloor).isEmpty();
        assertThat(towerDefense.borderWall).isEqualTo("copper-wall");
        assertThat(towerDefense.borderTolerance).isEqualTo(0.2);
        assertThat(towerDefense.requireDropZoneRadiusZero).isFalse();
    }

    @Test
    @DisplayName("cases and maps.checks repair negative counts and out-of-range tolerance")
    void casesAndMapChecks_repairInvalidValues() throws Exception {
        TomlSecretsConfig bound = PluginConfigLoader.lenientTomlMapper().readValue("""
                [cases.reports]
                cooldown_seconds = -1
                chat_lines = -3

                [cases.notices]
                max_per_join = -1

                [maps.checks]
                max_file_bytes = 0
                max_tiles = -5

                [maps.checks.profiles.tower-defense]
                border_tolerance = 2.0
                """, TomlSecretsConfig.class);
        bound.normalize();

        assertThat(bound.cases.reports.cooldownSeconds).isEqualTo(60);
        assertThat(bound.cases.reports.chatLines).isEqualTo(30);
        assertThat(bound.cases.notices.maxPerJoin).isEqualTo(5);
        assertThat(bound.maps.checks.maxFileBytes).isEqualTo(4194304);
        assertThat(bound.maps.checks.maxTiles).isEqualTo(360000);
        assertThat(bound.maps.checks.profiles.get("tower-defense").borderTolerance).isEqualTo(0.05);
    }
}
