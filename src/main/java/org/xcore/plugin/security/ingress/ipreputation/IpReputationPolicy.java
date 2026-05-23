package org.xcore.plugin.security.ingress.ipreputation;

import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;

/**
 * Evaluates whether an IP reputation result should trigger a block
 * according to the per-server policy configuration.
 * <p>
 * This class is stateless and safe to inject as a singleton.
 * It fails open (returns not-blocked) when the result is null.
 */
@Singleton
public class IpReputationPolicy {

    private final Config.IpReputationConfig config;

    public IpReputationPolicy(Config config) {
        this.config = config.ipReputation;
    }

    /**
     * Determines whether the given reputation result should be blocked.
     *
     * @param result the reputation lookup result, may be null
     * @return true if the IP should be denied access
     */
    public boolean isBlocked(IpReputationResult result) {
        if (result == null) {
            return false;
        }

        boolean shouldBlockProxy = config.blockProxy || config.blockVpn || config.blockTor;
        if (shouldBlockProxy && result.proxy()) {
            return true;
        }

        if (config.blockHosting && result.hosting()) {
            return true;
        }

        return false;
    }
}
