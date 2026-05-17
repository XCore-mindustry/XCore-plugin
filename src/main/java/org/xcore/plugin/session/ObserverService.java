package org.xcore.plugin.session;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Player;
import org.xcore.plugin.service.network.RedisObserverStateStore;

@Singleton
public class ObserverService {

    static final Team OBSERVER_TEAM = Team.get(255);

    private final SessionService sessionService;
    private final RedisObserverStateStore observerStateStore;

    @Inject
    public ObserverService(SessionService sessionService, RedisObserverStateStore observerStateStore) {
        this.sessionService = sessionService;
        this.observerStateStore = observerStateStore;
    }

    ObserverService(SessionService sessionService) {
        this(sessionService, null);
    }

    public boolean isObserving(Player player) {
        if (player == null) {
            return false;
        }

        return isObserving(sessionService.get(player));
    }

    public boolean isObserving(Session session) {
        return session != null && session.observing();
    }

    public boolean isObserverTeam(Team team) {
        return team == OBSERVER_TEAM;
    }

    public boolean enter(Session session) {
        if (session == null || session.player == null) {
            return false;
        }

        if (session.observing()) {
            cacheObserverState(resolvePlayerUuid(session), session.observerReturnTeam());
            session.player.clearUnit();
            session.player.team(OBSERVER_TEAM);
            return false;
        }

        Team returnTeam = resolveReturnTeam(session.player);
        session.beginObserving(returnTeam);
        cacheObserverState(resolvePlayerUuid(session), returnTeam);
        session.player.clearUnit();
        session.player.team(OBSERVER_TEAM);
        return true;
    }

    public boolean enter(Player player) {
        if (player == null) {
            return false;
        }

        Session session = sessionService.get(player);
        if (session != null) {
            return enter(session);
        }

        cacheObserverState(player.uuid(), resolveReturnTeam(player));
        player.clearUnit();
        player.team(OBSERVER_TEAM);
        return true;
    }

    public Team exit(Session session) {
        if (session == null || !session.observing()) {
            return null;
        }

        Team returnTeam = session.endObserving();
        clearObserverState(resolvePlayerUuid(session));
        if (session.player != null && returnTeam != null) {
            session.player.team(returnTeam);
        }
        return returnTeam;
    }

    public void restore(Player player) {
        if (player == null) {
            return;
        }

        Session session = sessionService.get(player);
        if (isObserving(session)) {
            player.clearUnit();
            player.team(OBSERVER_TEAM);
            return;
        }

        RedisObserverStateStore.CachedObserverState cachedState = cachedObserverState(player.uuid());
        if (cachedState == null) {
            return;
        }

        if (session != null) {
            session.beginObserving(observerStateStore.resolveReturnTeam(cachedState));
        }

        player.clearUnit();
        player.team(OBSERVER_TEAM);
    }

    public void resetObserverState(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return;
        }

        Session session = sessionService.get(playerUuid);
        if (session != null && session.observing()) {
            session.endObserving();
        }

        clearObserverState(playerUuid);
    }

    private Team resolveReturnTeam(Player player) {
        Team currentTeam = player.team();
        return currentTeam == Team.derelict || currentTeam == OBSERVER_TEAM ? null : currentTeam;
    }

    private String resolvePlayerUuid(Session session) {
        if (session == null || session.data == null || session.data.uuid == null || session.data.uuid.isBlank()) {
            return session != null && session.player != null ? session.player.uuid() : null;
        }
        return session.data.uuid;
    }

    private void cacheObserverState(String playerUuid, Team returnTeam) {
        if (observerStateStore != null) {
            observerStateStore.put(playerUuid, returnTeam);
        }
    }

    private void clearObserverState(String playerUuid) {
        if (observerStateStore != null) {
            observerStateStore.delete(playerUuid);
        }
    }

    private RedisObserverStateStore.CachedObserverState cachedObserverState(String playerUuid) {
        if (observerStateStore == null) {
            return null;
        }
        return observerStateStore.get(playerUuid);
    }
}
