package org.xcore.plugin.service;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.net.Administration.ActionType;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.SessionService;

@Singleton
public class AntiCheatService {

    private final SessionService sessionService;

    @Inject
    public AntiCheatService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostConstruct
    public void init() {
        Vars.netServer.admins.addActionFilter(action -> {
            if (action.type == ActionType.depositItem) {
                PlayerData playerData = sessionService.get(action.player.uuid()).data;
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
