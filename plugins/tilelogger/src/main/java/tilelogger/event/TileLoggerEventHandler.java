package tilelogger.event;

import arc.Events;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.world.blocks.power.PowerNode.PowerNodeBuild;
import org.xcore.plugin.event.NetEventService;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.vote.VoteKick;
import tilelogger.PlayerConfig;
import tilelogger.Rect;
import tilelogger.service.TileLoggerService;

import java.text.SimpleDateFormat;
import java.util.Date;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class TileLoggerEventHandler {

    private final TileLoggerService service;
    private final NetEventService netEventService;
    private final BundleService bundle;

    @Inject
    public TileLoggerEventHandler(TileLoggerService service, NetEventService netEventService, BundleService bundle) {
        this.service = service;
        this.netEventService = netEventService;
        this.bundle = bundle;
    }


    @PostConstruct
    public void init() {
        netEventService.setIpAcceptor(service::checkSubnetAccepted);

        VoteKick.setOnKick(player -> {
            var target = service.findPlayerUuid(player.uuid());
            if (target != null) {
                service.rollback(null, target, -1, -180,
                        new Rect((short)0, (short)0, (short)(Vars.world.width()-1), (short)(Vars.world.height()-1)));
            }
        });

        Events.on(EventType.BuildSelectEvent.class, event -> {
            if (event.builder == null || event.breaking) return;
            service.logBuild(event.tile, service.unitToPlayerInfo(event.builder));
        });

        Events.on(EventType.BlockBuildBeginEvent.class, event -> {
            if (event.unit == null || !event.breaking) return;
            service.logDestroy(event.tile, service.unitToPlayerInfo(event.unit));
        });

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.unit == null || event.breaking) return;
            service.logBuild(event.tile, service.unitToPlayerInfo(event.unit));
        });

        Events.on(EventType.BuildRotateEvent.class, event -> {
            if (event.unit == null) return;
            service.logBuild(event.build.tile, service.unitToPlayerInfo(event.unit));
        });

        Events.on(EventType.ConfigEvent.class, event -> {
            if (event.tile == null) return;
            if (event.tile instanceof PowerNodeBuild && event.value instanceof Integer i)
                service.logBuild(Vars.world.tile(i), event.player == null ? null : event.player.getInfo());
            service.logBuild(event.tile.tile, event.player == null ? null : event.player.getInfo());
        });

        Events.on(EventType.PickupEvent.class, event -> {
            if (event.build == null) return;
            service.logDestroy(event.build.tile, service.unitToPlayerInfo(event.carrier));
        });

        Events.on(EventType.PayloadDropEvent.class, event -> {
            if (event.build == null) return;
            service.logBuild(event.build.tile, service.unitToPlayerInfo(event.carrier));
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> service.logDestroy(event.tile, null));

        Events.on(EventType.PlayEvent.class, _ -> {
            String mapName = Vars.state.map.file.name();
            String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            service.resetHistory(date + "_" + mapName, true);
        });

        Events.on(EventType.PlayerLeave.class, event -> service.removePlayerConfig(event.player.uuid()));

        Events.on(EventType.TapEvent.class, event -> {
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
                    bundle.send(event.player, "tilelogger-select-pos1", args());
                }
                case 2 -> {
                    config.rect.x2 = event.tile.x;
                    config.rect.y2 = event.tile.y;
                    config.selectState = 0;
                    config.rect.normalize();
                    bundle.send(event.player, "tilelogger-select-done", args("area", config.rect.area()));
                }
            }
        });
    }
}
