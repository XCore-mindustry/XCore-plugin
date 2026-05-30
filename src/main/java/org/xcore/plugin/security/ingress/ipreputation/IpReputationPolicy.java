package org.xcore.plugin.security.ingress.ipreputation;

import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;

/**
 * Evaluates whether an IP reputation result should trigger a block
 * according to the per-server policy configuration.
 * <p>
 * This class is stateless and safe to inject as a singleton.
 * It fails open (returns not-blocked) when the result is null.
 */
@Singleton
public class IpReputationPolicy {

    private final TomlXcoreConfig.IpReputationConfig config;

    /**
     * Creates a new IpReputationPolicy using values from the provided application configuration.
     *
     * @param config the application configuration whose {@code ipReputation} subsection will be used by this policy
     */
    public IpReputationPolicy(TomlXcoreConfig config) {
        this.config = config.ipReputation;
    }

    /**
     * Determines whether an IP represented by the given reputation result should be blocked.
     *
     * @param result the reputation lookup result; may be null
     * @return `true` if the IP should be denied access, `false` otherwise
     */
    public boolean isBlocked(IpReputationResult result) {
        if (result == null) {
            return false;
        }

        if (config.blockProxy && result.proxy()) {
            return true;
        }

        // ip-api exposes a combined proxy/VPN/Tor signal via the proxy field,
        // so VPN and Tor policy toggles are evaluated against that same signal.
        if (config.blockVpn && result.proxy()) {
            return true;
        }

        if (config.blockTor && result.proxy()) {
            return true;
        }

        if (config.blockHosting && result.hosting()) {
            return true;
        }

        return false;
    }
}
