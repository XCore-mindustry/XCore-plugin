package org.xcore.plugin.config;

import arc.files.Fi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalConfigTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("postInit throws clear error when required fields are missing")
    void postInit_throwsClearErrorWhenRequiredFieldsAreMissing() {
        // Arrange
        GlobalConfig globalConfig = new GlobalConfig();
        Fi globalConfigFile = new Fi(tempDir.resolve("secrets.toml").toFile());

        // Act + Assert
        assertThatThrownBy(() -> globalConfig.postInit(globalConfigFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secrets.toml")
                .hasMessageContaining("database.mongo_connection_string")
                .hasMessageContaining("database.name");
    }

    @Test
    @DisplayName("postInit accepts valid configuration and normalizes translation providers")
    void postInit_acceptsValidConfigurationAndNormalizesTranslationProviders() {
        // Arrange
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.mongoConnectionString = "mongodb://localhost:27017";
        globalConfig.databaseName = "xcore";
        globalConfig.translationProviders = null;
        Fi globalConfigFile = new Fi(tempDir.resolve("secrets.toml").toFile());

        // Act + Assert
        assertThatCode(() -> globalConfig.postInit(globalConfigFile))
                .doesNotThrowAnyException();
    }
}
