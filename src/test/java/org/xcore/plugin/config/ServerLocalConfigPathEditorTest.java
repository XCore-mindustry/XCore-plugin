package org.xcore.plugin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerLocalConfigPathEditorTest {

    private final ServerLocalConfigPathEditor editor = new ServerLocalConfigPathEditor(new SerializationFactory().prettyGson());

    @Test
    @DisplayName("update supports TomlXcoreConfig directly")
    void update_supportsTomlXcoreConfigDirectly() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "server.player_limit", "64");

        assertThat(updated).isNotNull();
        assertThat(updated.server.playerLimit).isEqualTo(64);
    }

    @Test
    @DisplayName("update supports legacy alias paths")
    void update_supportsLegacyAliasPaths() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "playerLimit", "64");

        assertThat(updated).isNotNull();
        assertThat(updated.server.playerLimit).isEqualTo(64);
    }

    @Test
    @DisplayName("update supports canonical TOML dotted paths")
    void update_supportsCanonicalTomlDottedPaths() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "server.player_limit", "48");

        assertThat(updated).isNotNull();
        assertThat(updated.server.playerLimit).isEqualTo(48);
    }

    @Test
    @DisplayName("update supports nested transport redis dotted paths")
    void update_supportsNestedTransportRedisDottedPaths() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "transport.redis.url", "redis://example:6379");

        assertThat(updated).isNotNull();
        assertThat(updated.transport.redis.url).isEqualTo("redis://example:6379");
    }

    @Test
    @DisplayName("update parses comma separated translation pipeline and drops blanks")
    void update_parsesCommaSeparatedTranslationPipelineAndDropsBlanks() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "translation.pipeline", " google , , openai , deepl ");

        assertThat(updated).isNotNull();
        assertThat(updated.translation.pipeline).containsExactly("google", "openai", "deepl");
    }

    @Test
    @DisplayName("update parses JSON array translation pipeline")
    void update_parsesJsonArrayTranslationPipeline() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "translation.pipeline", "[\"google\", \"openai\"]");

        assertThat(updated).isNotNull();
        assertThat(updated.translation.pipeline).containsExactly("google", "openai");
    }

    @Test
    @DisplayName("update mutates TomlXcoreConfig pipeline directly")
    void update_mutatesTomlXcoreConfigPipelineDirectly() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "translation.pipeline", "google, openai");

        assertThat(updated).isNotNull();
        assertThat(updated.translation.pipeline).containsExactly("google", "openai");
    }

    @Test
    @DisplayName("update returns null for unsupported path on TomlXcoreConfig")
    void update_returnsNullForUnsupportedPathOnTomlXcoreConfig() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "missing.path", "value");

        assertThat(updated).isNull();
    }

    @Test
    @DisplayName("update throws friendly exception for invalid boolean value")
    void update_throwsFriendlyExceptionForInvalidBooleanValue() {
        assertThatThrownBy(() -> editor.update(new TomlXcoreConfig(), "gameStartedTimer", "maybe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value 'maybe' for 'game_started_timer' (expected true or false).");
    }

    @Test
    @DisplayName("update throws friendly exception for invalid integer value")
    void update_throwsFriendlyExceptionForInvalidIntegerValue() {
        assertThatThrownBy(() -> editor.update(new TomlXcoreConfig(), "playerLimit", "not-a-number"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value 'not-a-number' for 'player_limit' (expected integer number).")
                .cause()
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("update writes Discord channel id as string snowflake")
    void update_writesDiscordChannelIdAsStringSnowflake() {
        TomlXcoreConfig updated = editor.update(new TomlXcoreConfig(), "discordChannelId", "1099650307396476958");

        assertThat(updated).isNotNull();
        assertThat(updated.discord.channelId).isEqualTo("1099650307396476958");
    }

    @Test
    @DisplayName("update throws friendly exception for invalid Discord snowflake")
    void update_throwsFriendlyExceptionForInvalidDiscordSnowflake() {
        assertThatThrownBy(() -> editor.update(new TomlXcoreConfig(), "discordChannelId", "not-a-long"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value 'not-a-long' for 'channel_id' (expected decimal digits).");
    }

    @Test
    @DisplayName("update throws friendly exception for malformed translation pipeline JSON")
    void update_throwsFriendlyExceptionForMalformedTranslationPipelineJson() {
        assertThatThrownBy(() -> editor.update(new TomlXcoreConfig(), "translation.pipeline", "[\"google\","))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value '[\"google\",' for 'translation.pipeline' (expected a comma-separated list or JSON string array).")
                .cause()
                .isNotNull();
    }
}
