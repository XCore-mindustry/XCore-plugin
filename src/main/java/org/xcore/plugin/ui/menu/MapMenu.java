package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.SeqStream;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.List;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

@Singleton
public class MapMenu extends Menu {

    private final MapDataRepository mapDataRepository;
    private final EventDataRepository eventDataRepository;
    private final MapService mapService;
    private final Provider<EventMenu> eventMenu;

    @Inject
    public MapMenu(Config config, GlobalConfig globalConfig, SessionService sessionService,
                   MapDataRepository mapDataRepository, EventDataRepository eventDataRepository,
                   MapService mapService, Provider<EventMenu> eventMenu) {
        super(config, globalConfig, sessionService);
        this.mapDataRepository = mapDataRepository;
        this.eventDataRepository = eventDataRepository;
        this.mapService = mapService;
        this.eventMenu = eventMenu;
    }

    public void map(String uuid, MapData m) {
        Session session = sessionService.get(uuid).clear();
        Map mindustryMap = mapService.findMap(m.name);

        String last = m.playedTimes == 0
                ? session.locale().t("never")
                : (System.currentTimeMillis() - m.lastPlayedTime) / 60000 + "m";

        String desc = (mindustryMap == null || mindustryMap.description().isEmpty())
                ? session.locale().t("no-description")
                : mindustryMap.description();

        var builder = session.builder()
                .title("commands-map-title")
                .content("commands-map-content", args(
                        "name", m.name, "author", m.author, "description", desc,
                        "width", (mindustryMap == null) ? "?" : mindustryMap.width,
                        "height", (mindustryMap == null) ? "?" : mindustryMap.height,
                        "reputation", m.reputation, "popularity", String.format("%.1f", m.popularity),
                        "interest", String.format("%.1f", m.interest), "played", m.playedTimes,
                        "playedYear", m.playedTimesYear, "lastPlayed", last,
                        "like", m.like, "dislike", m.dislike,
                        "min", m.minimumGameTime / 60000, "avg", m.averageGameTime / 60000, "max", m.maximumGameTime / 60000
                ));

        Boolean currentVote = session.data.mapVotes.get(m.id.toString());
        String likeTxt = Boolean.TRUE.equals(currentVote) ? session.locale().t("map-vote-like-selected") : session.locale().t("map-vote-like");
        String dislikeTxt = Boolean.FALSE.equals(currentVote) ? session.locale().t("map-vote-dislike-selected") : session.locale().t("map-vote-dislike");

        builder.addRow(likeTxt, () -> { mapService.handleReputation(session.player, true, m); map(uuid, m); },
                       dislikeTxt, () -> { mapService.handleReputation(session.player, false, m); map(uuid, m); });

        EventData activeEvent = eventDataRepository.findActive().orElse(null);
        if (!config.isEvent() || (activeEvent == null || !activeEvent.isActive) || activeEvent.map.equals(m.id)) {
            builder.start()
                .add(session.locale().t("map-rtv"), () -> mapService.startRtvSession(session.player, mindustryMap, true, false))
                .ifAdd(session.player.admin, session.locale().t("map-artv"), () -> mapService.startRtvSession(session.player, mindustryMap, true, true))
                .end();
        }

        builder.addRow("map-maps", () -> { session.clearHistory(); maps(uuid, 1); });

        if (config.isEvent()) {
            builder.addRow("event-menu-create-start-map", () -> {
                session.pushHistory(() -> map(uuid, m));
                eventMenu.get().createStart(uuid, m);
            });
        }

        builder.addNavigationRow().show();
    }

    public void maps(String uuid, int page) {
        Session session = sessionService.get(uuid).clear();
        Seq<Map> availableMaps = mapService.getAvailableMaps();
        var pagination = CustomGatherers.calculatePagination(availableMaps.size, globalConfig.mapsPerPage);

        if (availableMaps.isEmpty()) {
            session.locale().send("empty");
            return;
        }

        int validPage = pagination.clampPage(page);
        String gameMode = state.rules.mode().name();

        var builder = session.builder()
                .title("commands-maps-title")
                .content("commands-maps-content", args("page", validPage, "total", pagination.totalPages()))

                .start()
                    .ifAdd(validPage > 1, "previous", () -> maps(uuid, validPage - 1))
                    .ifAdd(validPage < pagination.totalPages(), "next", () -> maps(uuid, validPage + 1))
                .end()

                .addForEach(SeqStream.of(availableMaps).gather(CustomGatherers.page(globalConfig.mapsPerPage, validPage)).flatMap(List::stream)::iterator,
                (b, map) -> b.addRow(map.name(), () -> {
                    session.pushHistory(() -> maps(uuid, validPage));
                    MapData data = mapDataRepository.findOrCreate(map.plainName(), map.file.name(), map.author(), gameMode);
                    map(uuid, data);
                }));

        builder.addNavigationRow().show();
    }

    public void showGameOverMenu(MapData current, MapData next, Team winner) {
        String nextName = next != null ? next.name : "Unknown";
        String nextAuthor = next != null ? next.author : "Unknown";

        Groups.player.each(player -> {
            Session session = sessionService.get(player.uuid()).clear();

            Boolean currentVote = session.data.mapVotes.get(current.id.toString());
            String likeButtonText = Boolean.TRUE.equals(currentVote)
                    ? session.locale().format("map-vote-like-selected", args())
                    : session.locale().format("map-vote-like", args());
            String dislikeButtonText = Boolean.FALSE.equals(currentVote)
                    ? session.locale().format("map-vote-dislike-selected", args())
                    : session.locale().format("map-vote-dislike", args());

            session.builder().title("map-vote-title")
                    .content("map-vote-content", args(
                            "mapName", nextName,
                            "author", nextAuthor,
                            "seconds", 10
                    ))
                    .add(likeButtonText, () -> mapService.handleReputation(player, true, current))
                    .add(dislikeButtonText, () -> mapService.handleReputation(player, false, current))
                    .end()
                    .add("current-map", () -> {
                        session.clearHistory();
                        map(player.uuid(), current);
                    })
                    .add("next-map", () -> {
                        session.clearHistory();
                        map(player.uuid(), next);
                    })
                    .addNavigationRow().show();
        });
    }
}
