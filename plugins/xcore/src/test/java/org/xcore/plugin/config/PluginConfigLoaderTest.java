package org.xcore.plugin.config;

import arc.files.Fi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginConfigLoaderTest {

    @TempDir
    Path tempDir;

    static class SampleConfig implements SelfNormalizing {
        public String name = "default-name";
        public int retries = 3;
        boolean normalized;

        @Override
        public void normalize() {
            normalized = true;
        }
    }

    private static final String TEMPLATE = """
            # Sample plugin configuration.
            name = "from-template"
            retries = 9
            """;

    private PluginConfigLoader<SampleConfig> loader(Path dir) {
        return new PluginConfigLoader<>(new Fi(dir.resolve("sample.toml").toFile()), SampleConfig.class,
                () -> TEMPLATE);
    }

    @Test
    @DisplayName("load writes the default template when the file is absent")
    void load_writesDefaultTemplateWhenAbsent() throws IOException {
        PluginConfigLoader<SampleConfig> loader = loader(tempDir);

        SampleConfig config = loader.load();

        assertThat(tempDir.resolve("sample.toml")).hasContent(TEMPLATE);
        assertThat(config.name).isEqualTo("from-template");
        assertThat(config.retries).isEqualTo(9);
        assertThat(config.normalized).isTrue();
    }

    @Test
    @DisplayName("load ignores unknown sections and keeps existing files untouched")
    void load_ignoresUnknownSectionsAndKeepsFileUntouched() throws IOException {
        Path file = tempDir.resolve("sample.toml");
        Files.writeString(file, """
                # admin comment that must survive
                name = "existing"
                [some_future_section]
                mystery = true
                """);
        String before = Files.readString(file);

        SampleConfig config = loader(tempDir).load();

        assertThat(config.name).isEqualTo("existing");
        // missing fields keep Java defaults
        assertThat(config.retries).isEqualTo(3);
        assertThat(config.normalized).isTrue();
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    @Test
    @DisplayName("load fails loudly on malformed TOML")
    void load_failsOnMalformedToml() throws IOException {
        Files.writeString(tempDir.resolve("sample.toml"), "name = [unclosed");

        assertThatThrownBy(() -> loader(tempDir).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sample.toml");
    }
}
