package org.xcore.plugin.command.transport;

public enum TransportCutoverTarget {
    PUBLISH,
    READ_ONLY,
    MUTATING,
    RPC,
    RECLAIM,
    ALL
}
