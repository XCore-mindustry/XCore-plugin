package tilelogger.event;

import arc.func.Cons;
import arc.util.Log;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.mindustry.Messenger;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.world.blocks.power.PowerNode.PowerNodeBuild;
import org.xcore.plugin.event.EventSubscriptions;
import org.xcore.plugin.startup.ShutdownOrder;
import org.xcore.plugin.vote.VoteKick;
import tilelogger.*;
import tilelogger.service.TileLoggerService;

@Singleton
public class TileLoggerEventHandler {
    private final TileLoggerService service;
    private final Messenger messenger;
    private final EventSubscriptions subscriptions = new EventSubscriptions();
    private Cons<Player> previousOnKick, onKick;
    private volatile boolean stopped;
    @Inject
    public TileLoggerEventHandler(TileLoggerService service, Messenger messenger) {
        this.service = service;
        this.messenger = messenger;
    }
    private <T> void on(Class<T> type, Cons<T> listener) {
        subscriptions.on(type, event -> {
            try {
                listener.get(event);
            } catch (RuntimeException error) {
                Log.err("[TileLogger] Event processing failed", error);
            }
        });
    }
    @PostConstruct
    public void init() {
        previousOnKick = VoteKick.onKick;
        onKick = player -> {
            if (stopped)
                return;
            // Identity is already known. Never resolve a vote target through synchronous storage.
            try {
                service.rollback(null, new PlayerDescriptor(player.plainName(), player.uuid(), -1), -1, -180,
                        new Rect(0, 0, Vars.world.width() - 1, Vars.world.height() - 1));
            } catch (RuntimeException error) {
                Log.err("[TileLogger] Vote rollback failed", error);
            }
        };
        VoteKick.setOnKick(onKick);
        service.registerPackets();
        on(EventType.BuildSelectEvent.class, event -> {
            if (event.builder == null)
                return;
            service.logBuild(event.tile, service.unitToPlayerInfo(event.builder),
                    event.breaking ? ActionKind.DESTROY_STARTED : ActionKind.BUILD_STARTED);
        });
        on(EventType.BlockBuildBeginEvent.class, event -> {
            if (event.tile == null)
                return;
            service.logBuild(event.tile, service.unitToPlayerInfo(event.unit),
                    event.breaking ? ActionKind.DESTROY_STARTED : ActionKind.BUILD_STARTED);
        });
        on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.breaking)
                service.logDestroy(
                        event.tile, service.unitToPlayerInfo(event.unit), ActionKind.DESTROY_COMPLETED);
            else
                service.logBuild(
                        event.tile, service.unitToPlayerInfo(event.unit), ActionKind.BUILD_COMPLETED);
        });
        on(EventType.BuildRotateEvent.class, event -> {
            if (event.build != null)
                service.logBuild(event.build.tile, service.unitToPlayerInfo(event.unit), ActionKind.ROTATE);
        });
        on(EventType.ConfigEvent.class, event -> {
            if (event.tile == null)
                return;
            var actor = event.player == null ? null : event.player.getInfo();
            if (event.tile instanceof PowerNodeBuild && event.value instanceof Integer packed)
                service.logBuild(Vars.world.tile(packed), actor, ActionKind.CONFIGURE);
            service.logBuild(event.tile.tile, actor, ActionKind.CONFIGURE);
        });
        on(EventType.PickupEvent.class, event -> {
            if (event.build != null)
                service.logPickup(event.build, service.unitToPlayerInfo(event.carrier));
        });
        on(EventType.PayloadDropEvent.class, event -> {
            if (event.build != null)
                service.logBuild(event.build.tile, service.unitToPlayerInfo(event.carrier), ActionKind.DROP);
        });
        on(EventType.BlockDestroyEvent.class,
                event -> service.logDestroy(event.tile, null, ActionKind.DESTROY_COMPLETED));
        on(EventType.ResetEvent.class, event -> service.invalidateWorld());
        on(EventType.PlayEvent.class, event -> service.startMapHistory());
        on(EventType.PlayerLeave.class, event -> service.removePlayerConfig(event.player.uuid()));
        on(EventType.TapEvent.class, event -> {
            if (event.tile == null)
                return;
            var tile = event.tile.build == null ? event.tile : event.tile.build.tile;
            PlayerConfig config = service.getPlayerConfig(event.player);
            if (config.historySize > 0)
                service.showHistory(event.player, tile.x, tile.y, config.historySize);
            if (service.useAdminTools(event.player))
                service.sendTileHistory(tile.x, tile.y, event.player);
            if (config.session != service.generation()) {
                config.selection = PlayerConfig.Selection.NONE;
                return;
            }
            switch (config.selection) {
                case FIRST_POINT -> {
                    config.rect.x1 = event.tile.x;
                    config.rect.y1 = event.tile.y;
                    config.selection = PlayerConfig.Selection.SECOND_POINT;
                    messenger.to(event.player).send("tilelogger-select-pos1");
                }
                case SECOND_POINT -> {
                    config.rect.x2 = event.tile.x;
                    config.rect.y2 = event.tile.y;
                    config.rect.normalize();
                    config.selection = PlayerConfig.Selection.READY;
                    messenger.to(event.player)
                            .send("tilelogger-select-done", Args.of("area", config.rect.area()));
                }
                default -> {
                }
            }
        });
        if (Vars.state != null && Vars.state.isPlaying()) service.startMapHistory();
    }
    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        stopped = true;
        subscriptions.close();
        service.unregisterPackets();
        if (VoteKick.onKick == onKick)
            VoteKick.setOnKick(previousOnKick);
    }
}
