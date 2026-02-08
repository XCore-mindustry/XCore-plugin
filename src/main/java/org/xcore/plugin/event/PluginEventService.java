package org.xcore.plugin.event;

import arc.Events;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.*;
import mindustry.server.ServerControl;
import org.xcore.plugin.event.handler.ConnectionHandler;
import org.xcore.plugin.event.handler.GameLifecycleHandler;
import org.xcore.plugin.event.handler.MapVoteHandler;

@Singleton
public class PluginEventService {

    private final ConnectionHandler connectionHandler;
    private final GameLifecycleHandler gameLifecycleHandler;
    private final MapVoteHandler mapVoteHandler;

    @Inject
    public PluginEventService(ConnectionHandler connectionHandler,
                              GameLifecycleHandler gameLifecycleHandler,
                              MapVoteHandler mapVoteHandler) {
        this.connectionHandler = connectionHandler;
        this.gameLifecycleHandler = gameLifecycleHandler;
        this.mapVoteHandler = mapVoteHandler;
    }

    @PostConstruct
    public void init() {
        Events.on(PlayerJoin.class, connectionHandler::onPlayerJoin);
        Events.on(PlayerLeave.class, connectionHandler::onPlayerLeave);
        Events.on(PlayEvent.class, gameLifecycleHandler::onPlayEvent);
        Events.on(GameOverEvent.class, gameLifecycleHandler::onGameOver);
        Events.on(String.class, gameLifecycleHandler::onWorldReload);

        ServerControl.instance.gameOverListener = mapVoteHandler.getGameOverListener();
    }
}