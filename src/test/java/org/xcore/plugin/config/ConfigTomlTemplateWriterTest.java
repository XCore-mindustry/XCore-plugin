package org.xcore.plugin.config;

import arc.files.Fi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default {@code xcore.toml} is the first thing a fresh install reads, and a key that
 * does not bind to a field is not an error - the operator silently gets the default back.
 * These tests parse what the writer emits and assert the values arrived, so a typo in a
 * documented key fails here instead of on a server.
 */
class ConfigTomlTemplateWriterTest {

    @Test
    @DisplayName("the default template parses and carries the ingress settings")
    void defaultTemplate_carriesIngressSettings(@TempDir Path dir) {
        TomlXcoreConfig config = loadFromTemplate(dir);

        assertThat(config.server.ingressFailureMode).isEqualTo("closed");
        assertThat(config.server.ingressHandshakeBudgetMillis).isEqualTo(2000);
    }

    @Test
    @DisplayName("the default template documents each ingress key it emits")
    void defaultTemplate_documentsIngressKeys() {
        String template = ConfigTomlTemplateWriter.defaultXcoreTomlContent();

        assertThat(template)
                // snake_case, because that is what the TOML binder expects for a
                // camelCase field. A camelCase key here would parse and then be ignored.
                .contains("ingress_failure_mode = \"closed\"")
                .contains("ingress_handshake_budget_millis = 2000");
    }

    @Test
    @DisplayName("a non-default ingress value survives the round trip, so binding is really proven")
    void nonDefaultIngressValues_surviveTheRoundTrip(@TempDir Path dir) throws Exception {
        // Needed because the template documents the defaults. If the key in the template
        // were misspelled, the binder would drop it and the operator would silently get
        // the field default back - which is the same value the template documents, so a
        // template-only assertion cannot tell a working key from a broken one. Writing a
        // value the default would not have produced does tell them apart.
        Fi dataDirectory = new Fi(dir.toFile());
        Fi toml = ConfigTomlLoader.resolveXcoreToml(dataDirectory);
        toml.writeString("""
                version = 1

                [server]
                name = "round-trip"
                ingress_failure_mode = "open"
                ingress_handshake_budget_millis = 1500
                """);

        var result = ConfigTomlLoader.loadXcoreConfig(dataDirectory);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.config.server.ingressFailureMode).isEqualTo("open");
        assertThat(result.config.server.ingressHandshakeBudgetMillis).isEqualTo(1500);
    }

    @Test
    @DisplayName("the documented values are the same ones normalize() enforces")
    void documentedValues_matchNormalizeDefaults() {
        // Guards against the template drifting away from the field defaults: if normalize()
        // ever changes a default, the documentation beside it becomes a lie.
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.normalize();

        assertThat(config.server.ingressFailureMode).isEqualTo("closed");
        assertThat(config.server.ingressHandshakeBudgetMillis).isEqualTo(2000);
    }

    @Test
    @DisplayName("an unknown key in the template would be silently ignored, so bind every key it emits")
    void everyEmittedKeyBindsToAField(@TempDir Path dir) throws Exception {
        // A cheap guard against the class of bug this file is about: writing a key that the
        // binder does not know. Anything not bound to a field is dropped without warning.
        String template = ConfigTomlTemplateWriter.defaultXcoreTomlContent();
        java.util.Set<String> bound = new java.util.HashSet<>();
        for (java.lang.reflect.Field field : TomlXcoreConfig.ServerConfig.class.getFields()) {
            bound.add(toSnakeCase(field.getName()));
        }
        for (java.lang.reflect.Field field : TomlXcoreConfig.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            bound.add(toSnakeCase(field.getName()));
        }

        java.util.List<String> serverSection = template.lines()
                .dropWhile(line -> !line.trim().equals("[server]"))
                .skip(1)
                .takeWhile(line -> !line.trim().startsWith("["))
                .toList();
        assertThat(serverSection).isNotEmpty();

        for (String line : serverSection) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String key = trimmed.substring(0, trimmed.indexOf('=')).trim();
            assertThat(bound)
                    .as("[server] key '%s' in the default template does not bind to a TomlXcoreConfig field", key)
                    .contains(key);
        }
    }

    private static TomlXcoreConfig loadFromTemplate(Path dir) {
        Fi dataDirectory = new Fi(dir.toFile());
        var result = ConfigTomlLoader.loadXcoreConfig(dataDirectory);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.DEFAULT_TEMPLATE);
        return result.config;
    }

    private static String toSnakeCase(String camel) {
        StringBuilder out = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (!out.isEmpty()) {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
