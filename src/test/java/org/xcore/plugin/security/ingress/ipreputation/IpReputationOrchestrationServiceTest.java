package org.xcore.plugin.security.ingress.ipreputation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IpReputationOrchestrationServiceTest {

    @Test
    @DisplayName("isBlocked returns false when feature is disabled")
    void isBlocked_featureDisabled_returnsFalse() {
        Config config = configWithEnabled(false);
        var service = newService(config, mockAllowlist(), mockCache(), mockProvider(), mockPolicy());

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("isBlocked returns false for blank ip")
    void isBlocked_blankIp_returnsFalse() {
        Config config = configWithEnabled(true);
        var service = newService(config, mockAllowlist(), mockCache(), mockProvider(), mockPolicy());

        assertThat(service.isBlocked("")).isFalse();
        assertThat(service.isBlocked(null)).isFalse();
    }

    @Test
    @DisplayName("isBlocked returns false when ip is on allowlist")
    void isBlocked_allowlisted_returnsFalse() {
        Config config = configWithEnabled(true);
        IpReputationAllowlist allowlist = mockAllowlist();
        when(allowlist.contains("1.2.3.4")).thenReturn(true);

        var service = newService(config, allowlist, mockCache(), mockProvider(), mockPolicy());

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("isBlocked uses cache hit when available")
    void isBlocked_cacheHit_usesCache() {
        Config config = configWithEnabled(true);
        IpReputationCache cache = mockCache();
        IpReputationResult cached = new IpReputationResult("1.2.3.4", true, false, false);
        when(cache.get("1.2.3.4")).thenReturn(cached);

        IpReputationPolicy policy = mockPolicy();
        when(policy.isBlocked(cached)).thenReturn(true);

        IpReputationProvider provider = mockProvider();
        var service = newService(config, mockAllowlist(), cache, provider, policy);

        assertThat(service.isBlocked("1.2.3.4")).isTrue();
        verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("isBlocked consults provider on cache miss and caches result")
    void isBlocked_cacheMiss_consultsProviderAndCaches() {
        Config config = configWithEnabled(true);
        IpReputationCache cache = mockCache();
        when(cache.get("1.2.3.4")).thenReturn(null);

        IpReputationProvider provider = mockProvider();
        IpReputationResult result = new IpReputationResult("1.2.3.4", true, false, false);
        when(provider.lookup("1.2.3.4")).thenReturn(result);

        IpReputationPolicy policy = mockPolicy();
        when(policy.isBlocked(result)).thenReturn(true);

        var service = newService(config, mockAllowlist(), cache, provider, policy);

        assertThat(service.isBlocked("1.2.3.4")).isTrue();
        verify(cache).put("1.2.3.4", result);
    }

    @Test
    @DisplayName("isBlocked fails open when provider returns null")
    void isBlocked_providerNull_failsOpen() {
        Config config = configWithEnabled(true);
        IpReputationCache cache = mockCache();
        when(cache.get("1.2.3.4")).thenReturn(null);

        IpReputationProvider provider = mockProvider();
        when(provider.lookup("1.2.3.4")).thenReturn(null);

        var service = newService(config, mockAllowlist(), cache, provider, mockPolicy());

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("isBlocked fails open when allowlist throws")
    void isBlocked_allowlistThrows_failsOpen() {
        Config config = configWithEnabled(true);
        IpReputationAllowlist allowlist = mockAllowlist();
        when(allowlist.contains("1.2.3.4")).thenThrow(new RuntimeException("redis down"));

        IpReputationCache cache = mockCache();
        IpReputationProvider provider = mockProvider();
        IpReputationPolicy policy = mockPolicy();

        var service = newService(config, allowlist, cache, provider, policy);

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
        verifyNoInteractions(cache, provider, policy);
    }

    @Test
    @DisplayName("isBlocked fails open when cache throws")
    void isBlocked_cacheThrows_failsOpen() {
        Config config = configWithEnabled(true);
        IpReputationCache cache = mockCache();
        when(cache.get("1.2.3.4")).thenThrow(new RuntimeException("redis down"));

        IpReputationProvider provider = mockProvider();
        IpReputationResult result = new IpReputationResult("1.2.3.4", false, false, false);
        when(provider.lookup("1.2.3.4")).thenReturn(result);

        IpReputationPolicy policy = mockPolicy();
        when(policy.isBlocked(result)).thenReturn(false);

        var service = newService(config, mockAllowlist(), cache, provider, policy);

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("isBlocked fails open when provider throws")
    void isBlocked_providerThrows_failsOpen() {
        Config config = configWithEnabled(true);
        IpReputationCache cache = mockCache();
        when(cache.get("1.2.3.4")).thenReturn(null);

        IpReputationProvider provider = mockProvider();
        when(provider.lookup("1.2.3.4")).thenThrow(new RuntimeException("timeout"));

        var service = newService(config, mockAllowlist(), cache, provider, mockPolicy());

        assertThat(service.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("isBlocked fails open when cache put throws")
    void isBlocked_cachePutThrows_stillEvaluatesPolicy() {
        Config config = configWithEnabled(true);
        IpReputationCache cache = mockCache();
        when(cache.get("1.2.3.4")).thenReturn(null);
        when(cache.put(anyString(), any())).thenThrow(new RuntimeException("redis down"));

        IpReputationProvider provider = mockProvider();
        IpReputationResult result = new IpReputationResult("1.2.3.4", true, false, false);
        when(provider.lookup("1.2.3.4")).thenReturn(result);

        IpReputationPolicy policy = mockPolicy();
        when(policy.isBlocked(result)).thenReturn(true);

        var service = newService(config, mockAllowlist(), cache, provider, policy);

        assertThat(service.isBlocked("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("lookup returns null when feature is disabled")
    void lookup_featureDisabled_returnsNull() {
        Config config = configWithEnabled(false);
        var service = newService(config, mockAllowlist(), mockCache(), mockProvider(), mockPolicy());

        assertThat(service.lookup("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("lookup returns provider result when enabled")
    void lookup_featureEnabled_returnsProviderResult() {
        Config config = configWithEnabled(true);
        IpReputationProvider provider = mockProvider();
        IpReputationResult result = new IpReputationResult("1.2.3.4", true, false, false);
        when(provider.lookup("1.2.3.4")).thenReturn(result);

        var service = newService(config, mockAllowlist(), mockCache(), provider, mockPolicy());

        assertThat(service.lookup("1.2.3.4")).isEqualTo(result);
    }

    @Test
    @DisplayName("lookup fails open when provider throws")
    void lookup_providerThrows_failsOpen() {
        Config config = configWithEnabled(true);
        IpReputationProvider provider = mockProvider();
        when(provider.lookup("1.2.3.4")).thenThrow(new RuntimeException("timeout"));

        var service = newService(config, mockAllowlist(), mockCache(), provider, mockPolicy());

        assertThat(service.lookup("1.2.3.4")).isNull();
    }

    private static IpReputationOrchestrationService newService(
            Config config,
            IpReputationAllowlist allowlist,
            IpReputationCache cache,
            IpReputationProvider provider,
            IpReputationPolicy policy) {
        return new IpReputationOrchestrationService(config, allowlist, cache, provider, policy);
    }

    private static Config configWithEnabled(boolean enabled) {
        Config config = new Config();
        config.ipReputation.enabled = enabled;
        return config;
    }

    private static IpReputationAllowlist mockAllowlist() {
        return mock(IpReputationAllowlist.class);
    }

    private static IpReputationCache mockCache() {
        return mock(IpReputationCache.class);
    }

    private static IpReputationProvider mockProvider() {
        return mock(IpReputationProvider.class);
    }

    private static IpReputationPolicy mockPolicy() {
        return mock(IpReputationPolicy.class);
    }
}
