package org.xcore.plugin.security.ingress.ipreputation;

/**
 * Service contract for IP reputation evaluation.
 * <p>
 * Orchestrates allowlist, cache, provider, and policy to produce
 * a blocking decision for a given IP address.
 */
public interface IpReputationService {

    /**
     * Evaluates whether the given IP should be blocked.
     * <p>
     * This method consults the allowlist first, then the cache,
     * then the configured provider if necessary. Errors at any
     * stage fail open (return false).
     *
     * @param ip the IP address to evaluate
     * @return true if the IP should be denied access
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
