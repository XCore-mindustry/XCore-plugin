package org.xcore.plugin.translation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationPrefilterTest {

    private final TomlXcoreConfig config = new TomlXcoreConfig();
    private final TranslationPrefilter prefilter = new TranslationPrefilter(config);

    @Test
    @DisplayName("colour tags, icons and extra whitespace are removed")
    void prepare_cleansText() {
        assertThat(prefilter.prepare("[scarlet]where  is the[] core?")).isEqualTo("where is the core?");
    }

    @Test
    @DisplayName("text without enough letters, links and universal words are not translated")
    void prepare_skipsTextThatNeedsNoTranslation() {
        assertThat(prefilter.prepare("123 :) !!")).isNull();
        assertThat(prefilter.prepare("a")).isNull();
        assertThat(prefilter.prepare("https://example.com/map")).isNull();
        assertThat(prefilter.prepare("GG WP!!")).isNull();
        assertThat(prefilter.prepare("[red]lol[]")).isNull();
        assertThat(prefilter.prepare("   ")).isNull();
        assertThat(prefilter.prepare("да")).isEqualTo("да");
    }

    @Test
    @DisplayName("a disabled prefilter only cleans the text")
    void prepare_whenDisabled_onlyCleans() {
        config.translation.prefilter.enabled = false;

        assertThat(prefilter.prepare("[red]gg[]")).isEqualTo("gg");
    }
}
