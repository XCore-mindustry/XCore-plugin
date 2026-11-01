package org.xcore.plugin.cases;

import java.time.Instant;
import java.util.Map;

/** A durable notice and, when claimed, its unique delivery owner. */
public record CaseNotice(String id, String playerUuid, String kind, Map<String, String> params,
                         String claimToken, Instant claimUntil) {
    public CaseNotice {
        params = Map.copyOf(params);
    }

    public String bundleKey() {
        return "case-notice-" + kind.replace('_', '-');
    }
}
