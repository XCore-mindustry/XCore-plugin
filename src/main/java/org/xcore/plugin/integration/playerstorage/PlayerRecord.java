package org.xcore.plugin.integration.playerstorage;

import java.util.Map;
import java.util.Objects;

public record PlayerRecord(String playerUuid, Map<String, Object> values, long revision, int schemaVersion) {
    public PlayerRecord {
        if (playerUuid == null || playerUuid.isBlank() || values == null) throw new IllegalArgumentException("Invalid player record");
        values = Map.copyOf(values);
        if (revision < 0 || schemaVersion < 1) throw new IllegalArgumentException("Invalid player record metadata");
    }
}
