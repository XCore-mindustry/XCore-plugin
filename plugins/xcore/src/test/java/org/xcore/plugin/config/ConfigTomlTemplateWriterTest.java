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

    @Test
    void casesAndMapChecksTemplateRoundTripBindsEveryKey() throws Exception {
        String template = ConfigTomlTemplateWriter.defaultSecretsTomlContent();
        TomlSecretsConfig defaults = PluginConfigLoader.lenientTomlMapper().readValue(template, TomlSecretsConfig.class);
        assertThat(defaults.cases.reports.enabled).isFalse();
        assertThat(defaults.cases.notices.maxPerJoin).isEqualTo(5);
        assertThat(defaults.maps.checks.maxFileBytes).isEqualTo(4194304);
        assertThat(defaults.maps.checks.severities).isEmpty();
        var defaultProfile = defaults.maps.checks.profiles.get("tower-defense");
        assertThat(defaultProfile.minWidth).isZero(); assertThat(defaultProfile.maxWidth).isZero();
        assertThat(defaultProfile.minHeight).isZero(); assertThat(defaultProfile.maxHeight).isZero();
        String changed = template
                .replace("enabled = false", "enabled = true")
                .replace("cooldown_seconds = 60", "cooldown_seconds = 71")
                .replace("same_target_cooldown_minutes = 30", "same_target_cooldown_minutes = 31")
                .replace("daily_limit = 10", "daily_limit = 11")
                .replace("network_burst_limit = 6", "network_burst_limit = 7")
                .replace("min_play_time_minutes = 0", "min_play_time_minutes = 8")
                .replace("chat_lines = 30", "chat_lines = 21")
                .replace("chat_window_minutes = 10", "chat_window_minutes = 12")
                .replace("block_window_seconds = 600", "block_window_seconds = 601")
                .replace("recent_leaver_minutes = 10", "recent_leaver_minutes = 13")
                .replace("max_per_join = 5", "max_per_join = 3")
                .replace("max_file_bytes = 4194304", "max_file_bytes = 123456")
                .replace("max_tiles = 360000", "max_tiles = 12345")
                .replace("severities = {}", "severities = { \"td-road-floor-mixed\" = \"warning\" }")
                .replace("min_width = 0", "min_width = 11")
                .replace("max_width = 0", "max_width = 101")
                .replace("min_height = 0", "min_height = 21")
                .replace("max_height = 0", "max_height = 201")
                .replace("metal-tiles-2", "stone")
                .replace("metal-wall-2", "copper-wall")
                .replace("border_tolerance = 0.05", "border_tolerance = 0.07")
                .replace("require_drop_zone_radius_zero = true", "require_drop_zone_radius_zero = false");
        TomlSecretsConfig bound = PluginConfigLoader.lenientTomlMapper().readValue(changed, TomlSecretsConfig.class);
        var reports = bound.cases.reports;
        assertThat(reports.enabled).isTrue();
        assertThat(reports.cooldownSeconds).isEqualTo(71);
        assertThat(reports.sameTargetCooldownMinutes).isEqualTo(31);
        assertThat(reports.dailyLimit).isEqualTo(11);
        assertThat(reports.networkBurstLimit).isEqualTo(7);
        assertThat(reports.minPlayTimeMinutes).isEqualTo(8);
        assertThat(reports.chatLines).isEqualTo(21);
        assertThat(reports.chatWindowMinutes).isEqualTo(12);
        assertThat(reports.blockWindowSeconds).isEqualTo(601);
        assertThat(reports.recentLeaverMinutes).isEqualTo(13);
        assertThat(bound.cases.notices.maxPerJoin).isEqualTo(3);
        assertThat(bound.maps.checks.maxFileBytes).isEqualTo(123456);
        assertThat(bound.maps.checks.maxTiles).isEqualTo(12345);
        var profile = bound.maps.checks.profiles.get("tower-defense");
        assertThat(profile.roadFloor).isEqualTo("stone");
        assertThat(profile.borderWall).isEqualTo("copper-wall");
        assertThat(profile.borderTolerance).isEqualTo(0.07);
        assertThat(profile.requireDropZoneRadiusZero).isFalse();
        assertThat(bound.maps.checks.severities).containsExactlyEntriesOf(java.util.Map.of("td-road-floor-mixed", "warning"));
        assertThat(profile.minWidth).isEqualTo(11); assertThat(profile.maxWidth).isEqualTo(101);
        assertThat(profile.minHeight).isEqualTo(21); assertThat(profile.maxHeight).isEqualTo(201);
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
