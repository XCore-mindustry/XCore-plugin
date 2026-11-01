package org.xcore.plugin.integration;

import mindustry.gen.Player;
import org.xcore.plugin.model.PlayerData;

/** Supplies an optional, already-formatted prefix for a player's display name. */
public interface PlayerDisplayProvider {
    String id();

    default int priority() {
        return 0;
    }

    String resolve(PlayerData data, Player player);
}
