package org.xcore.plugin.event.net.connect;

import arc.func.Boolf;
import jakarta.inject.Singleton;

@Singleton
public class ConnectionFilterService {

    public FilterResult filter(String address, Boolf<String> ipAcceptor) {
        if (!ipAcceptor.get(address)) {
            return new FilterResult(false, 1, 1);
        }
        return new FilterResult(true, 0, 0);
    }

    public record FilterResult(boolean allowed, int blockedIpDelta, int blockedIpsPerMinuteDelta) {
    }
}
