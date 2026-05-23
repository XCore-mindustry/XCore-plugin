package org.xcore.plugin.security.ingress.ipreputation;

/**
 * Service contract for IP reputation evaluation.
 * <p>
 * Orchestrates allowlist, cache, provider, and policy to produce
 * a blocking decision for a given IP address.
 */
public interface IpReputationService {

    /**
 * Determine whether the specified IP address should be blocked.
 *
 * Evaluation consults the allowlist first, then the cache, and finally the configured provider when needed; any errors cause fail-open behavior (treated as not blocked).
 *
 * @param ip the IP address to evaluate
 * @return `true` if the IP should be blocked, `false` otherwise
 */
    boolean isBlocked(String ip);

    /**
     * Performs a fresh provider lookup for the given IP,
     * bypassing cache. Useful for diagnostics and commands.
     *
     * @param ip the IP address to look up
     * @return the provider result, or null if lookup failed
     */
    IpReputationResult lookup(String ip);
}
