package org.xcore.plugin.security.ingress.ipreputation;

import java.util.Set;

/**
 * Contract for the IP reputation allowlist.
 * <p>
 * IPs on the allowlist bypass reputation checks entirely.
 * Implementations may use Redis, in-memory stores, or other backends.
 */
public interface IpReputationAllowlist {

    /**
 * Determines whether the specified IP address is explicitly allowlisted.
 *
 * @param ip the IP address to check
 * @return `true` if the IP address is on the allowlist, `false` otherwise
 */
    boolean contains(String ip);

    /**
 * Add the given IP address to the reputation allowlist so it bypasses reputation checks.
 *
 * @param ip the IP address to allowlist
 * @return {@code true} if the IP was successfully added, {@code false} otherwise
 */
    boolean add(String ip);

    /**
     * Removes an IP from the allowlist.
     *
     * @param ip the IP address
     * @return true if the operation succeeded
     */
    boolean remove(String ip);

    /**
 * Get all IP addresses currently on the allowlist; these addresses bypass IP reputation checks.
 *
 * @return a non-null Set containing all allowlisted IP addresses; may be empty
 */
    Set<String> list();
}
