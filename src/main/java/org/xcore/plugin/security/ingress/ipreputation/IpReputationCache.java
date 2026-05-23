package org.xcore.plugin.security.ingress.ipreputation;

/**
 * Contract for caching IP reputation lookup results.
 * <p>
 * Implementations may use Redis, in-memory stores, or other backends.
 * Cache misses and errors must be handled gracefully by callers.
 */
public interface IpReputationCache {

    /**
 * Retrieve the cached reputation result for the specified IP address.
 *
 * @param ip the IP address to look up (IPv4 or IPv6)
 * @return the cached {@link IpReputationResult} for the given IP, or `null` if no entry is present or the lookup failed
 */
    IpReputationResult get(String ip);

    /**
 * Stores the provided reputation result in the cache for the given IP address.
 *
 * @param ip     the IP address used as the cache key
 * @param result the reputation result to store
 * @return `true` if the result was stored successfully, `false` otherwise
 */
    boolean put(String ip, IpReputationResult result);
}
