package org.xcore.plugin.integration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

/** Refreshes display names synchronously; callers should invoke it on the Mindustry server thread. */
@Singleton
public class PlayerDisplayRefreshService {
    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;

    @Inject
    public PlayerDisplayRefreshService(SessionService sessionService, PlayerDisplayService playerDisplayService) {
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
    }

    public boolean refresh(Player player) {
        if (player == null) return false;
        Session session = sessionService.get(player);
        if (!isLive(session)) return false;
        playerDisplayService.refresh(session);
        return true;
    }

    public boolean refresh(String uuid) {
        if (uuid == null) return false;
        Session session = sessionService.get(uuid);
        if (!isLive(session)) return false;
        playerDisplayService.refresh(session);
        return true;
    }

    public int refreshAll() {
        int refreshed = 0;
        for (Session session : sessionService.getAllCachedSnapshot()) {
            if (!isLive(session)) continue;
            playerDisplayService.refresh(session);
            refreshed++;
        }
        return refreshed;
    }

    private boolean isLive(Session session) {
        return session != null && session.player != null && session.data != null;
    }
}
