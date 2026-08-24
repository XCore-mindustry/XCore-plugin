package org.xcore.plugin.event;

import arc.Events;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.server.ServerControl;
import org.xcore.plugin.event.handler.ConnectionHandler;
import org.xcore.plugin.event.handler.GameDataHandler;
import org.xcore.plugin.event.handler.GameLifecycleHandler;
import org.xcore.plugin.event.handler.MapVoteHandler;
import org.xcore.plugin.event.net.connect.ConnectionAccessHandler;

@Singleton
public class PluginEventService {

    private final ConnectionHandler connectionHandler;
    private final GameLifecycleHandler gameLifecycleHandler;
    private final GameDataHandler gameDataHandler;
    private final MapVoteHandler mapVoteHandler;
    
    private final ConnectionAccessHandler connectionAccessHandler;

    @Inject
    public PluginEventService(ConnectionHandler connectionHandler,
                              GameLifecycleHandler gameLifecycleHandler,
                              GameDataHandler gameDataHandler,
                              MapVoteHandler mapVoteHandler,
                              ConnectionAccessHandler connectionAccessHandler) {
        this.connectionHandler = connectionHandler;
        this.gameLifecycleHandler = gameLifecycleHandler;
        this.gameDataHandler = gameDataHandler;
        this.mapVoteHandler = mapVoteHandler;
        this.connectionAccessHandler = connectionAccessHandler;
    }

    @PostConstruct
    public void init() {
        Events.on(EventType.ConnectPacketEvent.class, event -> {
            connectionAccessHandler.allow(event.connection, event.packet);
        });
        
        Events.on(PlayerJoin.class, connectionHandler::onPlayerJoin);
        Events.on(PlayerLeave.class, connectionHandler::onPlayerLeave);

        Events.on(PlayEvent.class, gameLifecycleHandler::onPlayEvent);
        Events.on(GameOverEvent.class, gameLifecycleHandler::onGameOver);
        Events.on(String.class, gameLifecycleHandler::onWorldReload);

        Events.on(BlockBuildBeginEvent.class, gameDataHandler::onBlockBuildBegin);
        Events.on(BlockBuildEndEvent.class, gameDataHandler::onBlockBuild);
        Events.on(BlockDestroyEvent.class, gameDataHandler::onBlockDestroy);
        Events.on(PickupEvent.class, gameDataHandler::onPickup);
        Events.on(PlayerJoin.class, gameDataHandler::onPlayerJoin);
        Events.on(PlayerLeave.class, gameDataHandler::onPlayerLeave);

        ServerControl.instance.gameOverListener = mapVoteHandler.getGameOverListener();
    }
}