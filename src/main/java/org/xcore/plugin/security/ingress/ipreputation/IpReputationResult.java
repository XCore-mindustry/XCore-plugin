package org.xcore.plugin.security.ingress.ipreputation;

/**
 * Domain model representing the result of an IP reputation lookup.
 * <p>
 * Transport-agnostic: this record does not expose HTTP status codes,
 * JSON structures, or provider-specific response shapes.
 */
public record IpReputationResult(
    String ip,
    boolean proxy,
    boolean hosting,
    boolean mobile
) {
}
