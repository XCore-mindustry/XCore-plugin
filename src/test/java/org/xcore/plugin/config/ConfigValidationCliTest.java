package org.xcore.plugin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigValidationCliTest {
    @TempDir Path directory;

    @Test
    void validate_usesCoreParserWithoutChangingFile() throws Exception {
        Path file = directory.resolve("candidate.toml");
        String content = "[discord]\nchannel_id = 18446744073709551615\n[permissions]\nmode = \"roles\"\n";
        Files.writeString(file, content);
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        assertThat(ConfigValidationCli.run(new String[]{"xcore", file.toString()}, new PrintStream(output), new PrintStream(errors))).isZero();
        assertThat(Files.readString(file)).isEqualTo(content);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString()).isEmpty();
    }

    @Test
    void rejectedConfigDoesNotEchoItsValues() throws Exception {
        Path file = directory.resolve("candidate.toml");
        Files.writeString(file, "[permissions]\nmode = \"private-secret-value\"\n");
        var errors = new ByteArrayOutputStream();
        assertThat(ConfigValidationCli.run(new String[]{"xcore", file.toString()}, System.out, new PrintStream(errors))).isEqualTo(1);
        assertThat(errors.toString()).contains("validation failed").doesNotContain("private-secret-value");
    }

    @Test
    void resolvesEffectiveSecretsPathAndDoesNotSeedMissingFile() throws Exception {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        String[] args = {"resolve-secrets", directory.toString()};
        assertThat(ConfigValidationCli.run(args, new PrintStream(output), new PrintStream(errors))).isEqualTo(1);
        assertThat(directory.resolve("xcore.toml")).doesNotExist();
        Files.writeString(directory.resolve("xcore.toml"), "[paths]\nglobal_config_directory = \""
                + directory.resolve("shared").toString().replace("\\", "\\\\") + "\"\n");
        output.reset();
        assertThat(ConfigValidationCli.run(args, new PrintStream(output), new PrintStream(errors))).isZero();
        assertThat(output.toString().trim()).isEqualTo(directory.resolve("shared/secrets.toml").toString());
    }

    @Test
    void requiredSecretsAndFieldTypesAreChecked() throws Exception {
        Path file = directory.resolve("candidate.toml");
        var errors = new PrintStream(new ByteArrayOutputStream());
        Files.writeString(file, "[database]\nname = \"\"\nmongo_connection_string = \"\"\n");
        assertThat(ConfigValidationCli.run(new String[]{"secrets", file.toString()}, System.out, errors)).isEqualTo(1);
        Files.writeString(file, "[server]\nplayer_limit = { invalid = true }\n");
        assertThat(ConfigValidationCli.run(new String[]{"xcore", file.toString()}, System.out, errors)).isEqualTo(1);
    }
}
