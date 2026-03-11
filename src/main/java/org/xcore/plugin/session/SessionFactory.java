package org.xcore.plugin.session;

import mindustry.gen.Player;
import org.xcore.plugin.model.PlayerData;

public interface SessionFactory {
    Session create(Player player, PlayerData playerData);
}
