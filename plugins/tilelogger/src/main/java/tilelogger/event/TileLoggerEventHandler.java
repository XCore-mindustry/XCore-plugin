package tilelogger.event;

import arc.func.Cons;
import org.xcore.plugin.event.EventSubscriptions;
import org.xcore.plugin.startup.ShutdownOrder;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.mindustry.Messenger;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.game.EventType;
import mindustry.world.blocks.power.PowerNode.PowerNodeBuild;
import org.xcore.plugin.vote.VoteKick;
import tilelogger.PlayerConfig;
import tilelogger.Rect;
import tilelogger.service.TileLoggerService;

import java.text.SimpleDateFormat;
import java.util.Date;


@Singleton
public class TileLoggerEventHandler {

    private final TileLoggerService service;
    private final Messenger messenger;
    private final EventSubscriptions subscriptions = new EventSubscriptions();
    private Cons<Player> previousOnKick;
    private Cons<Player> onKick;
    private volatile boolean stopped;

    @Inject
    public TileLoggerEventHandler(TileLoggerService service, Messenger messenger) {
        this.service = service;
        this.messenger = messenger;
    }


    @PostConstruct
    public void init() {
        previousOnKick = VoteKick.onKick;
        onKick = player -> {
            if (stopped) return;
            var target = service.findPlayerUuid(player.uuid());
            if (target != null) {
                service.rollback(null, target, -1, -180,
                        new Rect((short)0, (short)0, (short)(Vars.world.width()-1), (short)(Vars.world.height()-1)));
            }
        };
        VoteKick.setOnKick(onKick);

        subscriptions.on(EventType.BuildSelectEvent.class, event -> {
            if (event.builder == null || event.breaking) return;
            service.logBuild(event.tile, service.unitToPlayerInfo(event.builder));
        });

        subscriptions.on(EventType.BlockBuildBeginEvent.class, event -> {
            if (event.unit == null || !event.breaking) return;
            service.logDestroy(event.tile, service.unitToPlayerInfo(event.unit));
        });

        subscriptions.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.unit == null || event.breaking) return;
            service.logBuild(event.tile, service.unitToPlayerInfo(event.unit));
        });

        subscriptions.on(EventType.BuildRotateEvent.class, event -> {
            if (event.unit == null) return;
            service.logBuild(event.build.tile, service.unitToPlayerInfo(event.unit));
        });

        subscriptions.on(EventType.ConfigEvent.class, event -> {
            if (event.tile == null) return;
            if (event.tile instanceof PowerNodeBuild && event.value instanceof Integer i)
                service.logBuild(Vars.world.tile(i), event.player == null ? null : event.player.getInfo());
            service.logBuild(event.tile.tile, event.player == null ? null : event.player.getInfo());
        });

        subscriptions.on(EventType.PickupEvent.class, event -> {
            if (event.build == null) return;
            service.logDestroy(event.build.tile, service.unitToPlayerInfo(event.carrier));
        });

        subscriptions.on(EventType.PayloadDropEvent.class, event -> {
            if (event.build == null) return;
            service.logBuild(event.build.tile, service.unitToPlayerInfo(event.carrier));
        });

        subscriptions.on(EventType.BlockDestroyEvent.class, event -> service.logDestroy(event.tile, null));

        subscriptions.on(EventType.PlayEvent.class, _ -> {
            String mapName = Vars.state.map.file.name();
            String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            service.resetHistory(date + "_" + mapName, true);
        });

        subscriptions.on(EventType.PlayerLeave.class, event -> service.removePlayerConfig(event.player.uuid()));

        subscriptions.on(EventType.TapEvent.class, event -> {
            if (event.tile == null) return;

            PlayerConfig config = service.getPlayerConfig(event.player);

            if (config.historySize > 0) {
                service.showHistory(event.player, (short) event.tile.centerX(), (short) event.tile.centerY(), config.historySize);
            }
            if (service.useAdminTools(event.player)) {
                service.sendTileHistory((short) event.tile.centerX(), (short) event.tile.centerY(), event.player);
            }

            switch (config.selectState) {
                case 1 -> {
                    config.rect.x1 = event.tile.x;
                    config.rect.y1 = event.tile.y;
                    config.selectState++;
                    messenger.to(event.player).send("tilelogger-select-pos1");
                }
                case 2 -> {
                    config.rect.x2 = event.tile.x;
                    config.rect.y2 = event.tile.y;
                    config.selectState = 0;
                    config.rect.normalize();
                    messenger.to(event.player).send("tilelogger-select-done", Args.of("area", config.rect.area()));
                }
            }
        });
    }
    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        stopped = true;
        subscriptions.close();
        if (VoteKick.onKick == onKick) VoteKick.setOnKick(previousOnKick);
    }

}
