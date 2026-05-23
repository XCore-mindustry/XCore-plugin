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

    /**
     * Create an IpReputationOrchestrationService wired with its required collaborators.
     *
     * @param config    configuration containing ip reputation settings (e.g., enablement)
     * @param allowlist allowlist used to short-circuit evaluations for permitted IPs
     * @param cache     cache used to read/write IpReputationResult entries
     * @param provider  provider used to perform authoritative IP reputation lookups
     * @param policy    policy used to decide whether a given IpReputationResult constitutes a block
     */
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

    /**
     * Determine whether the given IP address should be blocked by the IP reputation policy.
     *
     * <p>The method is gated by the IP reputation feature flag and treats null or blank input as not blocked.
     * It consults the allowlist (errors cause the method to treat the IP as not blocked), attempts a cache
     * lookup, falls back to the provider on cache miss (provider errors cause the method to treat the IP as
     * not blocked), and attempts to write provider results back to the cache (cache errors do not change the
     * eventual policy evaluation). The final decision is produced by the configured policy and may be based
     * on a cached result, a provider result, or {@code null}.</p>
     *
     * @param ip the IP address to evaluate; if {@code null} or blank it is treated as not blocked
     * @return {@code true} if the IP is considered blocked by the configured policy, {@code false} otherwise
     */
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
            return false;
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

    /**
     * Retrieve the reputation information for an IP address from the configured provider.
     *
     * <p>Whitespace around the input is trimmed before lookup. If IP reputation is disabled,
     * the input is null or blank, or the provider lookup fails, this method returns {@code null}.</p>
     *
     * @param ip the IP address to look up; leading and trailing whitespace will be ignored
     * @return the {@code IpReputationResult} for the normalized IP, or {@code null} if disabled,
     *         the input is null/blank, or a provider error occurs
     */
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
