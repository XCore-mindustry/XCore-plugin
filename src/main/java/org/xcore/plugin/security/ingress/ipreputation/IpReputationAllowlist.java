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
     * Checks whether the given IP is explicitly allowed.
     *
     * @param ip the IP address
     * @return true if the IP is on the allowlist
     */
    boolean contains(String ip);

    /**
     * Adds an IP to the allowlist.
     *
     * @param ip the IP address
     * @return true if the operation succeeded
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
     * Returns all IPs currently on the allowlist.
     *
     * @return a set of allowlisted IPs; may be empty but never null
     */
    Set<String> list();
}
