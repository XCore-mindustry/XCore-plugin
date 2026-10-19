package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.maps.Map;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.ui.menu.MapMenu;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class MapController implements CloudClientController {

    private final MapDataRepository mapDataRepository;
    private final MapService mapService;
    private final MapMenu menu;
    private final Async async;

    @Inject
    public MapController(
            MapDataRepository mapDataRepository,
            MapService mapService,
            MapMenu menu,
            Async async
    ) {
        this.mapDataRepository = mapDataRepository;
        this.mapService = mapService;
        this.menu = menu;
        this.async = async;
    }

    public MapController(
            MapDataRepository mapDataRepository,
            MapService mapService,
            Provider<MapMenu> menuProvider,
            Async async
    ) {
        this(mapDataRepository, mapService, menuProvider != null ? menuProvider.get() : null, async);
    }

    @Command("map|map-stats|map-statistics")
    public void map(XCoreSender sender) {
        map(sender, Vars.state.map);
    }

    @Command("map|map-stats|map-statistics <map>")
    public void map(XCoreSender sender, @Argument("map") Map map) {
        if (map == null) {
            sender.send("error-map-not-found", args());
            return;
        }

        // The continuation is scoped to a connected player, and forPlayer requires one.
        // The console is a legitimate caller of this command and has no player.
        if (!sender.isPlayer()) {
            sender.send("error-only-players", args());
            return;
        }

        // Commands are dispatched from the game thread, and findOrCreate both reads and can
        // write the map row, so it must not run here. Everything the lookup needs is read
        // from live game state first: a map can be swapped or deleted while the round trip
        // is in flight, and Vars.state.rules is not safe to read from the storage thread.
        String plainName = map.plainName();
        String fileName = map.file.name();
        String author = map.author();
        String mode = Vars.state.rules.mode().name();

        // forPlayer drops the continuation if the player left while the lookup ran, which
        // is the right outcome: there is no menu left to show.
        async.forPlayer(sender.player(), () -> mapDataRepository.findOrCreate(plainName, fileName, author, mode),
                (player, data) -> menu.map(menu.getUuid(sender), data));
    }

    @Command("maps|map-ui [page]")
    public void maps(XCoreSender sender, @Argument("page") @Default("1") int page) {
        menu.maps(menu.getUuid(sender), page);
    }

    @Command("rtv [map]")
    public void rtv(XCoreSender sender, @Argument("map") Map map) {
        Map target = map != null ? map : mapService.resolveNextMap(Vars.state.rules.mode(), Vars.state.map);
        mapService.startRtvSession(sender.player(), target, map != null, false);
    }

    @Permission(PermissionNodes.MAPS_FORCE_RTV)
    @Command("artv [map]")
    public void artv(XCoreSender sender, @Argument("map") Map map) {
        Map target = map != null ? map : mapService.resolveNextMap(Vars.state.rules.mode(), Vars.state.map);
        mapService.startRtvSession(sender.player(), target, map != null, true);
    }

    @Command("vnw")
    public void vnw(XCoreSender sender) {
        mapService.startNewWaveSession(sender.player(), false);
    }

    @Permission(PermissionNodes.MAPS_FORCE_VNW)
    @Command("avnw")
    public void avnw(XCoreSender sender) {
        mapService.startNewWaveSession(sender.player(), true);
    }

    @Command("like|+")
    public void like(XCoreSender sender) {
        mapService.handleReputation(sender.player(), true);
    }

    @Command("dislike|-")
    public void dislike(XCoreSender sender) {
        mapService.handleReputation(sender.player(), false);
    }
}
