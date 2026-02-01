package org.xcore.plugin.security.ingress;

import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;

/**
 * Interface for connection security checks.<br>
 *<br>
 * Priority system:<br>
 * - Negative priorities: Fast, synchronous checks (run first, sequentially)<br>
 * - Zero/Positive priorities: Potentially slow checks (can be parallelized)<br>
 *<br>
 * Lower priority number = runs earlier.<br>
 */
public interface IngressCheck {

    /**
     * Performs a security check on the incoming connection.<br>
     *
     * @param con The network connection
     * @param packet The connect packet with player info
     * @return AccessResult.Allowed if check passes, AccessResult.Denied if connection should be rejected
     */
    AccessResult check(NetConnection con, ConnectPacket packet);

    /**
     * Priority determines execution order.<br>
     * Negative values run synchronously first (fast checks).<br>
     * Non-negative values may run in parallel (slow checks like API calls).<br>
     *
     * @return priority value (lower = earlier)
     */
    default int priority() {
        return 0;
    }

    /**
     * Whether this check can be safely run in parallel with other checks.<br>
     * Override to return false for checks that modify shared state.<br>
     *
     * @return true if check is thread-safe for parallel execution
     */
    default boolean isParallelizable() {
        return priority() >= 0;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
