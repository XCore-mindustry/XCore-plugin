package org.xcore.plugin.security.ingress.ipreputation;

import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.Config;

/**
 * Concrete implementation of {@link IpReputationService} that orchestrates
 * allowlist, cache, provider, and policy to produce a blocking decision.
 * <p>
 * This class is stateless and safe to inject as a singleton.
 * Errors at any stage fail open (return {@code false} / {@code null}).
 */
@Singleton
public class IpReputationOrchestrationService implements IpReputationService {

    private final Config config;
    private final IpReputationAllowlist allowlist;
    private final IpReputationCache cache;
    private final IpReputationProvider provider;
    private final IpReputationPolicy policy;

    public IpReputationOrchestrationService(
            Config config,
            IpReputationAllowlist allowlist,
            IpReputationCache cache,
            IpReputationProvider provider,
            IpReputationPolicy policy) {
        this.config = config;
        this.allowlist = allowlist;
        this.cache = cache;
        this.provider = provider;
        this.policy = policy;
    }

    @Override
    public boolean isBlocked(String ip) {
        if (!config.ipReputation.enabled) {
            return false;
        }

        if (ip == null || ip.isBlank()) {
            return false;
        }

        String normalized = ip.trim();

        try {
            if (allowlist.contains(normalized)) {
                return false;
            }
        } catch (Exception e) {
            PLog.warnTag("IpReputation", "Allowlist check failed for @: @", normalized, e.getMessage());
            // fail open: continue to cache/provider
        }

        IpReputationResult result = null;

        try {
            result = cache.get(normalized);
        } catch (Exception e) {
            PLog.warnTag("IpReputation", "Cache read failed for @: @", normalized, e.getMessage());
            // fail open: continue to provider
        }

        if (result == null) {
            try {
                result = provider.lookup(normalized);
            } catch (Exception e) {
                PLog.warnTag("IpReputation", "Provider lookup failed for @: @", normalized, e.getMessage());
                // fail open
                return false;
            }

            if (result != null) {
                try {
                    cache.put(normalized, result);
                } catch (Exception e) {
                    PLog.warnTag("IpReputation", "Cache write failed for @: @", normalized, e.getMessage());
                    // fail open: we still have the result to evaluate
                }
            }
        }

        return policy.isBlocked(result);
    }

    @Override
    public IpReputationResult lookup(String ip) {
        if (!config.ipReputation.enabled) {
            return null;
        }

        if (ip == null || ip.isBlank()) {
            return null;
        }

        String normalized = ip.trim();

        try {
            return provider.lookup(normalized);
        } catch (Exception e) {
            PLog.warnTag("IpReputation", "Provider lookup failed for @: @", normalized, e.getMessage());
            return null;
        }
    }
}
