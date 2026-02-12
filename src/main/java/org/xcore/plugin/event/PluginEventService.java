package org.xcore.plugin.event;

import arc.Events;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.*;
import mindustry.server.ServerControl;
import org.xcore.plugin.event.handler.ConnectionHandler;
import org.xcore.plugin.event.handler.GameDataHandler;
import org.xcore.plugin.event.handler.GameLifecycleHandler;
import org.xcore.plugin.event.handler.MapVoteHandler;

@Singleton
public class PluginEventService {

    private final ConnectionHandler connectionHandler;
    private final GameLifecycleHandler gameLifecycleHandler;
    private final GameDataHandler gameDataHandler;
    private final MapVoteHandler mapVoteHandler;

    @Inject
    public PluginEventService(ConnectionHandler connectionHandler,
                              GameLifecycleHandler gameLifecycleHandler,
                              GameDataHandler gameDataHandler,
                              MapVoteHandler mapVoteHandler) {
        this.connectionHandler = connectionHandler;
        this.gameLifecycleHandler = gameLifecycleHandler;
        this.gameDataHandler = gameDataHandler;
        this.mapVoteHandler = mapVoteHandler;
    }

    @PostConstruct
    public void init() {
        Events.on(PlayerJoin.class, connectionHandler::onPlayerJoin);
        Events.on(PlayerLeave.class, connectionHandler::onPlayerLeave);

        Events.on(PlayEvent.class, gameLifecycleHandler::onPlayEvent);
        Events.on(GameOverEvent.class, gameLifecycleHandler::onGameOver);
        Events.on(String.class, gameLifecycleHandler::onWorldReload);

        Events.on(BlockBuildEndEvent.class, gameDataHandler::onBlockBuild);
        Events.on(BlockDestroyEvent.class, gameDataHandler::onBlockDestroy);
        Events.on(UnitCreateEvent.class, gameDataHandler::onUnitCreate);
        Events.on(UnitDestroyEvent.class, gameDataHandler::onUnitDestroy);
        Events.on(PlayerJoin.class, gameDataHandler::onPlayerJoin);
        Events.on(PlayerLeave.class, gameDataHandler::onPlayerLeave);

        ServerControl.instance.gameOverListener = mapVoteHandler.getGameOverListener();
    }
}