package org.xcore.plugin.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NullSafeTest {

    @Test
    @DisplayName("orElse returns value when value is not null")
    void returnsValue() {
        assertThat(NullSafe.orElse("value", "default")).isEqualTo("value");
    }

    @Test
    @DisplayName("orElse returns default when value is null")
    void returnsDefault() {
        assertThat(NullSafe.orElse(null, "default")).isEqualTo("default");
    }

    @Test
    @DisplayName("orElse allows null default")
    void allowsNullDefault() {
        assertThat(NullSafe.<String>orElse(null, null)).isNull();
    }
}
