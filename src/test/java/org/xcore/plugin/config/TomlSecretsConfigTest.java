package org.xcore.plugin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TomlSecretsConfigTest {

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

        toml.normalize();

        assertThat(toml.database).isNotNull();
        assertThat(toml.externalLinks).isNotNull();
        assertThat(toml.moderation).isNotNull();
        assertThat(toml.chat).isNotNull();
        assertThat(toml.maps).isNotNull();
        assertThat(toml.pagination).isNotNull();
        assertThat(toml.messages).isNotNull();
        assertThat(toml.translation).isNotNull();

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
    @DisplayName("normalize preserves blank optional strings; blank-to-null conversion happens in mapper")
    void normalize_preservesBlankOptionalStrings_forLaterMapperConversion() {
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
}
