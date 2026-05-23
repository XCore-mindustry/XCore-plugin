package org.xcore.plugin.security.ingress.ipreputation;

/**
 * Contract for caching IP reputation lookup results.
 * <p>
 * Implementations may use Redis, in-memory stores, or other backends.
 * Cache misses and errors must be handled gracefully by callers.
 */
public interface IpReputationCache {

    /**
     * Retrieves a cached result for the given IP.
     *
     * @param ip the IP address
     * @return cached result, or null if not present or lookup failed
     */
    IpReputationResult get(String ip);

    /**
     * Stores a result in the cache.
     *
     * @param ip     the IP address
     * @param result the reputation result to cache
     * @return true if the result was stored successfully
     */
    boolean put(String ip, IpReputationResult result);
}
