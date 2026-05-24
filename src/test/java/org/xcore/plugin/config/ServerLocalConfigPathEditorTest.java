package org.xcore.plugin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerLocalConfigPathEditorTest {

    private final ServerLocalConfigPathEditor editor = new ServerLocalConfigPathEditor(new SerializationFactory().prettyGson());

    @Test
    @DisplayName("update supports legacy alias paths")
    void update_supportsLegacyAliasPaths() {
        Config updated = editor.update(new Config(), "playerLimit", "64");

        assertThat(updated).isNotNull();
        assertThat(updated.playerLimit).isEqualTo(64);
    }

    @Test
    @DisplayName("update supports canonical TOML dotted paths")
    void update_supportsCanonicalTomlDottedPaths() {
        Config updated = editor.update(new Config(), "server.player_limit", "48");

        assertThat(updated).isNotNull();
        assertThat(updated.playerLimit).isEqualTo(48);
    }

    @Test
    @DisplayName("update supports nested transport redis dotted paths")
    void update_supportsNestedTransportRedisDottedPaths() {
        Config updated = editor.update(new Config(), "transport.redis.url", "redis://example:6379");

        assertThat(updated).isNotNull();
        assertThat(updated.redisUrl).isEqualTo("redis://example:6379");
    }

    @Test
    @DisplayName("update parses comma separated translation pipeline and drops blanks")
    void update_parsesCommaSeparatedTranslationPipelineAndDropsBlanks() {
        Config updated = editor.update(new Config(), "translation.pipeline", " google , , openai , deepl ");

        assertThat(updated).isNotNull();
        assertThat(updated.translation.pipeline).containsExactly("google", "openai", "deepl");
    }

    @Test
    @DisplayName("update parses JSON array translation pipeline")
    void update_parsesJsonArrayTranslationPipeline() {
        Config updated = editor.update(new Config(), "translation.pipeline", "[\"google\", \"openai\"]");

        assertThat(updated).isNotNull();
        assertThat(updated.translation.pipeline).containsExactly("google", "openai");
    }

    @Test
    @DisplayName("update returns null for unsupported path")
    void update_returnsNullForUnsupportedPath() {
        Config updated = editor.update(new Config(), "missing.path", "value");

        assertThat(updated).isNull();
    }

    @Test
    @DisplayName("update throws friendly exception for invalid boolean value")
    void update_throwsFriendlyExceptionForInvalidBooleanValue() {
        assertThatThrownBy(() -> editor.update(new Config(), "consoleEnabled", "maybe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value 'maybe' for 'console_enabled' (expected true or false).");
    }

    @Test
    @DisplayName("update throws friendly exception for invalid integer value")
    void update_throwsFriendlyExceptionForInvalidIntegerValue() {
        assertThatThrownBy(() -> editor.update(new Config(), "playerLimit", "not-a-number"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value 'not-a-number' for 'player_limit' (expected integer number).")
                .cause()
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("update throws friendly exception for invalid long value")
    void update_throwsFriendlyExceptionForInvalidLongValue() {
        assertThatThrownBy(() -> editor.update(new Config(), "discordChannelId", "not-a-long"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value 'not-a-long' for 'channel_id' (expected integer number).")
                .cause()
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("update throws friendly exception for malformed translation pipeline JSON")
    void update_throwsFriendlyExceptionForMalformedTranslationPipelineJson() {
        assertThatThrownBy(() -> editor.update(new Config(), "translation.pipeline", "[\"google\","))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value '[\"google\",' for 'translation.pipeline' (expected a comma-separated list or JSON string array).")
                .cause()
                .isNotNull();
    }
}
