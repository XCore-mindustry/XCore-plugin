package org.xcore.plugin.integration.idempotency;

public record LedgerClaim(boolean acquired, LedgerEntry entry) {}
