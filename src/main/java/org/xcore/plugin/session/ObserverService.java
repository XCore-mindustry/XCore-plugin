package org.xcore.plugin.session;

import arc.Events;
import arc.graphics.Color;
import arc.struct.Seq;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.service.network.RedisObserverStateStore;

@Singleton
public class ObserverService {

    static final Team OBSERVER_TEAM = Team.get(255);

    static {
        OBSERVER_TEAM.name = "spectator";
        OBSERVER_TEAM.color.set(Color.valueOf("6e7080"));
        OBSERVER_TEAM.hasPalette = false;
    }

    private final SessionService sessionService;
    private final RedisObserverStateStore observerStateStore;
    private final Async async;

    @Inject
    public ObserverService(SessionService sessionService,
                           RedisObserverStateStore observerStateStore,
                           Async async) {
        this.sessionService = sessionService;
        this.observerStateStore = observerStateStore;
        this.async = async;
    }

    public ObserverService(SessionService sessionService, RedisObserverStateStore observerStateStore) {
        this(sessionService, observerStateStore, null);
    }

    ObserverService(SessionService sessionService) {
        this(sessionService, null);
    }

    @PostConstruct
    public void init() {
        cleanupObserverCores();
        Events.on(EventType.WorldLoadEvent.class, e -> cleanupObserverCores());
        Events.on(EventType.PlayEvent.class, e -> cleanupObserverCores());

        if (Vars.netServer != null && Vars.netServer.admins != null) {
            Vars.netServer.admins.addActionFilter(action -> {
                if (action != null && action.player != null && isObserving(action.player)) {
                    return false;
                }
                return true;
            });
        }

        Events.run(EventType.Trigger.update, () -> {
            if (Groups.player == null || Groups.player.isEmpty()) return;
            Groups.player.each(player -> {
                if (player != null && isObserving(player)) {
                    player.deathTimer = 0f;
                    if (player.unit() != null && player.unit().isValid()) {
                        player.clearUnit();
                    }
                }
            });
        });

        Events.on(EventType.UnitChangeEvent.class, event -> {
            if (event != null && event.player != null && isObserving(event.player)) {
                event.player.clearUnit();
            }
        });
    }

    public void cleanupObserverCores() {
        if (Vars.world == null || Vars.state == null) return;
        Seq<CoreBlock.CoreBuild> cores = OBSERVER_TEAM.cores();
        if (cores == null || cores.isEmpty()) return;
        CoreBlock.CoreBuild[] arr = cores.toArray(CoreBlock.CoreBuild.class);
        for (CoreBlock.CoreBuild core : arr) {
            if (core != null && core.tile != null) {
                core.tile.setNet(core.block, Team.derelict, core.rotation);
            }
        }
    }

    public boolean isObserving(Player player) {
        if (player == null) {
            return false;
        }

        Session session = sessionService.get(player);
        if (session != null && session.observing()) {
            return true;
        }

        return player.team() == OBSERVER_TEAM;
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
            if (session.player.unit() != null) {
                session.player.unit().kill();
            }
            session.player.clearUnit();
            session.player.team(OBSERVER_TEAM);
            session.player.deathTimer = 0f;
            return false;
        }

        Team returnTeam = resolveReturnTeam(session.player);
        session.beginObserving(returnTeam);
        cacheObserverState(resolvePlayerUuid(session), returnTeam);
        if (session.player.unit() != null) {
            session.player.unit().kill();
        }
        session.player.clearUnit();
        session.player.team(OBSERVER_TEAM);
        session.player.deathTimer = 0f;
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
        if (player.unit() != null) {
            player.unit().kill();
        }
        player.clearUnit();
        player.team(OBSERVER_TEAM);
        player.deathTimer = 0f;
        return true;
    }

    public Team exit(Session session) {
        if (session == null || !session.observing()) {
            return null;
        }

        Team returnTeam = session.endObserving();
        clearObserverState(resolvePlayerUuid(session));
        if (session.player != null) {
            Team targetTeam = returnTeam != null ? returnTeam
                    : (Vars.netServer != null && Vars.netServer.assigner != null
                            ? Vars.netServer.assignTeam(session.player) : Team.sharded);
            session.player.team(targetTeam);
            session.player.clearUnit();
            if (Vars.state != null && Vars.state.teams != null) {
                session.player.checkSpawn();
            }
        }
        return returnTeam;
    }

    public void restore(Player player) {
        if (player == null || observerStateStore == null) {
            return;
        }

        Session session = sessionService.get(player);
        if (isObserving(session)) {
            if (player.unit() != null) {
                player.unit().kill();
            }
            player.clearUnit();
            player.team(OBSERVER_TEAM);
            player.deathTimer = 0f;
            return;
        }

        if (async != null) {
            async.onMainForPlayer(player, observerStateStore.getAsync(player.uuid()), (p, cachedState) -> {
                if (cachedState == null) {
                    return;
                }
                Session currentSession = sessionService.get(p);
                if (currentSession != null) {
                    currentSession.beginObserving(observerStateStore.resolveReturnTeam(cachedState));
                }
                if (p.unit() != null) {
                    p.unit().kill();
                }
                p.clearUnit();
                p.team(OBSERVER_TEAM);
                p.deathTimer = 0f;
            });
            return;
        }

        RedisObserverStateStore.CachedObserverState cachedState = observerStateStore.get(player.uuid());
        if (cachedState == null) {
            return;
        }

        if (session != null) {
            session.beginObserving(observerStateStore.resolveReturnTeam(cachedState));
        }

        if (player.unit() != null) {
            player.unit().kill();
        }
        player.clearUnit();
        player.team(OBSERVER_TEAM);
        player.deathTimer = 0f;
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
            observerStateStore.putAsync(playerUuid, returnTeam);
        }
    }

    private void clearObserverState(String playerUuid) {
        if (observerStateStore != null) {
            observerStateStore.deleteAsync(playerUuid);
        }
    }
}
