package org.xcore.plugin.service;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.net.Administration.ActionType;
import org.xcore.plugin.model.PlayerData;

@Singleton
public class AntiCheatService {

    private final PlayerSessionService playerSessionService;

    @Inject
    public AntiCheatService(PlayerSessionService playerSessionService) {
        this.playerSessionService = playerSessionService;
    }

    @PostConstruct
    public void init() {
        Vars.netServer.admins.addActionFilter(action -> {
            if (action.type == ActionType.depositItem) {
                PlayerData playerData = playerSessionService.get(action.player.uuid());
                if (playerData == null) return true;
                if (System.nanoTime() - playerData.lastUnload < 1_000_000_000) {
                    return false;
                }
                playerData.lastUnload = System.nanoTime();
            }
            return true;
        });
    }
}
