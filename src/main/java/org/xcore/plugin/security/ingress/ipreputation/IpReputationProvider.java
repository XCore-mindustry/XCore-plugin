package org.xcore.plugin.security.ingress.ipreputation;

/**
 * Contract for IP reputation data providers (e.g. ip-api).
 * <p>
 * Implementations handle transport concerns internally and must
 * return a domain result or null when lookup cannot be completed.
 */
public interface IpReputationProvider {

    /**
     * Looks up reputation data for the given IP address.
     *
     * @param ip the IP address to query
     * @return reputation result, or null if the lookup failed or is inconclusive
     */
    IpReputationResult lookup(String ip);
}
