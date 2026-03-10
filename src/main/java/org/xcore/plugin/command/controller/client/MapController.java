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
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.ui.menu.MapMenu;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class MapController implements CloudClientController {

    private final MapDataRepository mapDataRepository;
    private final MapService mapService;
    private final Provider<MapMenu> menu;

    @Inject
    public MapController(
            MapDataRepository mapDataRepository,
            MapService mapService,
            Provider<MapMenu> menu
    ) {
        this.mapDataRepository = mapDataRepository;
        this.mapService = mapService;
        this.menu = menu;
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

        MapData data = mapDataRepository.findOrCreate(
                map.plainName(), map.file.name(), map.author(), Vars.state.rules.mode().name()
        );

        menu.get().map(menu.get().getUuid(sender), data);
    }

    @Command("maps|map-ui [page]")
    public void maps(XCoreSender sender, @Argument("page") @Default("1") int page) {
        menu.get().maps(menu.get().getUuid(sender), page);
    }

    @Command("rtv [map]")
    public void rtv(XCoreSender sender, @Argument("map") Map map) {
        Map target = map != null ? map : mapService.resolveNextMap(Vars.state.rules.mode(), Vars.state.map);
        mapService.startRtvSession(sender.player(), target, map != null, false);
    }

    @Permission("admin")
    @Command("artv [map]")
    public void artv(XCoreSender sender, @Argument("map") Map map) {
        Map target = map != null ? map : mapService.resolveNextMap(Vars.state.rules.mode(), Vars.state.map);
        mapService.startRtvSession(sender.player(), target, map != null, true);
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
