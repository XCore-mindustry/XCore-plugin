package org.xcore.plugin.security.ingress.ipreputation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;

import static org.assertj.core.api.Assertions.assertThat;

class IpReputationPolicyTest {

    @Test
    @DisplayName("returns false for null result")
    void returnsFalse_forNullResult() {
        TomlXcoreConfig config = new TomlXcoreConfig();

        assertThat(new IpReputationPolicy(config).isBlocked(null)).isFalse();
    }

    @Test
    @DisplayName("blockProxy honors proxy signal independently")
    void blockProxy_honorsProxySignalIndependently() {
        TomlXcoreConfig config = baseConfig();
        config.ipReputation.blockProxy = true;
        config.ipReputation.blockVpn = false;
        config.ipReputation.blockTor = false;

        assertThat(new IpReputationPolicy(config).isBlocked(proxyResult())).isTrue();
    }

    @Test
    @DisplayName("blockVpn honors combined provider proxy signal independently")
    void blockVpn_honorsCombinedProviderSignalIndependently() {
        TomlXcoreConfig config = baseConfig();
        config.ipReputation.blockProxy = false;
        config.ipReputation.blockVpn = true;
        config.ipReputation.blockTor = false;

        assertThat(new IpReputationPolicy(config).isBlocked(proxyResult())).isTrue();
    }

    @Test
    @DisplayName("blockTor honors combined provider proxy signal independently")
    void blockTor_honorsCombinedProviderSignalIndependently() {
        TomlXcoreConfig config = baseConfig();
        config.ipReputation.blockProxy = false;
        config.ipReputation.blockVpn = false;
        config.ipReputation.blockTor = true;

        assertThat(new IpReputationPolicy(config).isBlocked(proxyResult())).isTrue();
    }

    @Test
    @DisplayName("blockHosting honors hosting signal")
    void blockHosting_honorsHostingSignal() {
        TomlXcoreConfig config = baseConfig();
        config.ipReputation.blockHosting = true;

        IpReputationResult result = new IpReputationResult("1.2.3.4", false, true, false);

        assertThat(new IpReputationPolicy(config).isBlocked(result)).isTrue();
    }

    @Test
    @DisplayName("returns false when all toggles are disabled")
    void returnsFalse_whenAllTogglesDisabled() {
        TomlXcoreConfig config = baseConfig();

        assertThat(new IpReputationPolicy(config).isBlocked(proxyResult())).isFalse();
    }

    private static TomlXcoreConfig baseConfig() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.ipReputation.blockProxy = false;
        config.ipReputation.blockVpn = false;
        config.ipReputation.blockTor = false;
        config.ipReputation.blockHosting = false;
        return config;
    }

    private static IpReputationResult proxyResult() {
        return new IpReputationResult("1.2.3.4", true, false, false);
    }
}
