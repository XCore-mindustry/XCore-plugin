package org.xcore.plugin.event;

import arc.Core;
import arc.func.Cons;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
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
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.startup.ShutdownOrder;

@Singleton
public class PluginEventService {

    private final ConnectionHandler connectionHandler;
    private final GameLifecycleHandler gameLifecycleHandler;
    private final GameDataHandler gameDataHandler;
    private final MapVoteHandler mapVoteHandler;
    
    private final ConnectionAccessHandler connectionAccessHandler;
    private final EventSubscriptions subscriptions = new EventSubscriptions();
    private Cons<GameOverEvent> previousGameOverListener;
    private Cons<GameOverEvent> gameOverListener;
    private ServerControl serverControl;

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
        subscriptions.on(EventType.ConnectPacketEvent.class, event -> {
            connectionAccessHandler.allow(event.connection, event.packet);
        });

        subscriptions.on(PlayerJoin.class, event -> {
            connectionHandler.onPlayerJoin(event);
            gameDataHandler.onPlayerJoin(event);
        });

        subscriptions.on(PlayerLeave.class, event -> {
            connectionHandler.onPlayerLeave(event);
            gameDataHandler.onPlayerLeave(event);
        });

        subscriptions.on(PlayEvent.class, gameLifecycleHandler::onPlayEvent);
        subscriptions.on(GameOverEvent.class, gameLifecycleHandler::onGameOver);
        subscriptions.on(String.class, gameLifecycleHandler::onWorldReload);

        subscriptions.on(BlockBuildBeginEvent.class, gameDataHandler::onBlockBuildBegin);
        subscriptions.on(BlockBuildEndEvent.class, gameDataHandler::onBlockBuild);
        subscriptions.on(PickupEvent.class, gameDataHandler::onPickup);

        if (ServerControl.instance != null) {
            serverControl = ServerControl.instance;
            previousGameOverListener = serverControl.gameOverListener;
            gameOverListener = mapVoteHandler.getGameOverListener();
            serverControl.gameOverListener = gameOverListener;
        }
    }
    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        subscriptions.close();
        Runnable restore = () -> {
            if (serverControl != null && serverControl.gameOverListener == gameOverListener) {
                serverControl.gameOverListener = previousGameOverListener;
            }
        };
        if (GameThread.isGameThread()) restore.run();
        else Core.app.post(restore);
    }
}
