package org.xcore.plugin.cloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DisabledCommandPolicyTest {

    @Test
    @DisplayName("normalizes command names consistently")
    void normalizeCommandName_normalizesSlashesCaseAndWhitespace() {
        Config config = new Config();
        DisabledCommandPolicy policy = new DisabledCommandPolicy(config);

        assertThat(policy.normalizeCommandName("  /TeSt   Foo  ")).isEqualTo("test foo");
        assertThat(policy.normalizeCommandName("   ")).isNull();
        assertThat(policy.normalizeCommandName(null)).isNull();
    }

    @Test
    @DisplayName("string disable check requires exact explicit match")
    void isCommandDisabled_string_usesExactMatch() {
        Config config = new Config();
        config.disabledCommands = Set.of("map stats");
        DisabledCommandPolicy policy = new DisabledCommandPolicy(config);

        assertThat(policy.isCommandDisabled("map stats")).isTrue();
        assertThat(policy.isCommandDisabled("/MAP   STATS")).isTrue();
        assertThat(policy.isCommandDisabled("map stats detail")).isFalse();
        assertThat(policy.isCommandDisabled("mapping")).isFalse();
    }

    @Test
    @DisplayName("disabledCommandKey matches full command prefixes but not unrelated names")
    void disabledCommandKey_matchesPrefixSemantics() {
        Config config = new Config();
        config.disabledCommands = Set.of("map", "map stats");
        DisabledCommandPolicy policy = new DisabledCommandPolicy(config);

        assertThat(policy.disabledCommandKey("map next")).isEqualTo("map");
        assertThat(policy.disabledCommandKey("map stats detail")).isIn("map", "map stats");
        assertThat(policy.disabledCommandKey("mapping")).isNull();
    }
}
