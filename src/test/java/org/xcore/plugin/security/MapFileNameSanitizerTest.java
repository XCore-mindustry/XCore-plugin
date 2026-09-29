package org.xcore.plugin.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapFileNameSanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Crossroads.msav",
            "crossroads.msav",
            "My Map (v2).msav",
            "map_2024-01-01.msav",
            "a.msav",
            "abcdefghijklmnopqrstuvwxyz0123456789 ._()-.msav"
    })
    @DisplayName("accepts ordinary map names")
    void acceptsOrdinaryNames(String raw) {
        assertThatCode(() -> MapFileNameSanitizer.requireSafeName(raw))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("returns the same name it validated")
    void returnsSameName() {
        assertThat(MapFileNameSanitizer.requireSafeName("Crossroads.msav")).isEqualTo("Crossroads.msav");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../../etc/cron.d/pwn.msav",
            "../../evil.msav",
            "..\\..\\windows\\system32\\pwn.msav",
            "/etc/passwd.msav",
            "/absolute/x.msav",
            "sub/dir/x.msav",
            "./x.msav",
            "..",
            ".",
            ""
    })
    @DisplayName("rejects names that choose their own location, or name nothing")
    void rejectsTraversalAndEmpty(String raw) {
        assertThatThrownBy(() -> MapFileNameSanitizer.requireSafeName(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "payload.sh",
            "payload.msav.exe",
            "payload.msavx",
            "no-extension",
            ".hidden.msav",
            "-leading-dash.msav",
            "null\u0000byte.msav",
            "tab\u0009sep.msav",
            "юникод.msav",
            "trailing.msav/",
            "with;semicolon.msav",
            "with$dollar.msav"
    })
    @DisplayName("rejects anything outside the conservative allowlist")
    void rejectsOutsideAllowlist(String raw) {
        assertThatThrownBy(() -> MapFileNameSanitizer.requireSafeName(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> MapFileNameSanitizer.requireSafeName(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects names longer than the bounded maximum")
    void rejectsOverlongNames() {
        String overlong = "a".repeat(200) + ".msav";
        assertThatThrownBy(() -> MapFileNameSanitizer.requireSafeName(overlong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    @DisplayName("accepts a name at exactly the length boundary")
    void acceptsBoundaryLength() {
        // 64 stem characters (the pattern allows 1 + 63) plus the 5-character ".msav".
        String boundary = "a".repeat(64) + ".msav";
        assertThat(boundary).hasSize(69);
        assertThatCode(() -> MapFileNameSanitizer.requireSafeName(boundary))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects one character past the length boundary")
    void rejectsPastBoundaryLength() {
        String tooLong = "a".repeat(65) + ".msav";
        assertThatThrownBy(() -> MapFileNameSanitizer.requireSafeName(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
