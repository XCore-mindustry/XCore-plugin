package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.maps.Map;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.SeqStream;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.EventService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteEvent;
import org.xcore.plugin.vote.VoteService;

import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class EventMenu extends Menu {

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;
    private final PlayerDataRepository playerDataRepository;
    private final MapService mapService;
    private final EventService eventService;
    private final VoteService voteService;
    private final Provider<MapMenu> mapMenu;

    @Inject
    public EventMenu(Config config, GlobalConfig globalConfig, SessionService sessionService,
                     EventDataRepository eventDataRepository, MapDataRepository mapDataRepository,
                     PlayerDataRepository playerDataRepository, MapService mapService, EventService eventService,
                     VoteService voteService, Provider<MapMenu> mapMenu) {
        super(config, globalConfig, sessionService);
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.playerDataRepository = playerDataRepository;
        this.mapService = mapService;
        this.eventService = eventService;
        this.voteService = voteService;
        this.mapMenu = mapMenu;
    }

    public void main(String uuid) {
        Session session = sessionService.get(uuid).clear();
        EventData active = eventDataRepository.findActive().orElse(null);

        var builder = session.builder()
                .title("event-menu-main-title")
                .content("event-menu-main-content")
                .start()
                    .addLocal(session.locale().t("event-menu-create-start"), () -> {
                        session.pushHistory(() -> main(uuid));
                        createStart(uuid, null);
                    })
                    .addLocal(session.locale().t("event-menu-events"), () -> {
                        session.pushHistory(() -> main(uuid));
                        events(uuid, 1);
                    })
                .end();

        if (active != null) {
            builder.addLocalRow("event-menu-this-event", () -> {
                session.pushHistory(() -> main(uuid));
                event(uuid, active);
            });
        }

        if (session.player.admin) {
            builder.start();
            if (voteService.getCurrentSession() instanceof VoteEvent) {
                builder.addLocal(session.locale().t("event-menu-vote-stop"), voteService::endVote);
            }
            if (active != null && active.isActive) {
                builder.addLocal(session.locale().t("event-menu-stop"), eventDataRepository::finishActiveEvent);
            }
            builder.end();
        }

        builder.addNavigationRow().show();
    }

    public void createStart(String uuid, MapData map) {
        Session session = sessionService.get(uuid).clear();
        EventData draft = session.getDraft(EventData.class);
        draft.author = session.data.id;
        if (map != null) draft.map = map.id;

        session.setTextHandler((text) -> {
            draft.name = text;
            edit(uuid);
        });

        Call.textInput(session.player.con, session.menuService.getTextId(),
                session.locale().t("event-menu-create-start-title"),
                session.locale().t("event-menu-create-start-message"),
                20, session.locale().t("event-menu-create-start-default", args("playerName", session.player.name)), false);
    }

    public void edit(String uuid) {
        Session session = sessionService.get(uuid).clear();
        if (!session.hasDraft(EventData.class)) { main(uuid); return; }

        EventData draft = session.getDraft(EventData.class);
        MapData mapData = mapDataRepository.findById(draft.map);
        PlayerData authorData = playerDataRepository.findById(draft.author);

        String yes = session.locale().t("yes");
        String no = session.locale().t("no");

        session.builder()
                .title("event-menu-edit-title")
                .content("event-menu-edit-content", args(
                        "name", draft.name, "description", draft.description,
                        "author", (authorData == null) ? "Unknown" : authorData.nickname,
                        "mapName", (mapData == null) ? "" : mapData.name,
                        "isMajor", draft.isMajor ? yes : no,
                        "isTemporary", draft.isTemporary ? yes : no,
                        "plannedStartTime", formatTime(draft.plannedStartTime, session),
                        "plannedEndTime",  formatTime(draft.plannedEndTime, session)
                ))
                .start()
                    .addLocal(session.locale().t("event-menu-edit-name"), () -> {
                        session.setTextHandler(t -> { draft.name = t; edit(uuid); });
                        Call.textInput(session.player.con, session.menuService.getTextId(), session.locale().t("event-menu-edit-name-title"), "", 24, draft.name, false);
                    })
                    .addLocal(session.locale().t("event-menu-edit-description"), () -> {
                        session.setTextHandler(t -> { draft.description = t; edit(uuid); });
                        Call.textInput(session.player.con, session.menuService.getTextId(), session.locale().t("event-menu-edit-description-title"), "", 1000, draft.description, false);
                    })
                .end()
                .start()
                    .addLocal(session.locale().t("event-menu-edit-map"), () -> {
                        session.pushHistory(() -> edit(uuid));
                        mapSelection(uuid, 1);
                    })
                    .addLocal(draft.isTemporary ? session.locale().t("event-menu-edit-temporary-active") : session.locale().t("event-menu-edit-temporary-inactive"), () -> {
                        draft.isTemporary = !draft.isTemporary; edit(uuid);
                    })
                .end()
                .start()
                    .addLocal(session.locale().t("event-menu-edit-planned-start"), () -> {
                        session.setTextHandler(t -> { draft.plannedStartTime = parseTime(t); edit(uuid); });
                        Call.textInput(session.player.con, session.menuService.getTextId(), session.locale().t("event-menu-edit-planned-start-title"), "", 64, "", false);
                    })
                    .addLocal(session.locale().t("event-menu-edit-planned-end"), () -> {
                        session.setTextHandler(t -> { draft.plannedEndTime = parseTime(t); edit(uuid); });
                        Call.textInput(session.player.con, session.menuService.getTextId(), session.locale().t("event-menu-edit-planned-end-title"), "", 10, "", false);
                    })
                .end()
                .start()
                    .addLocal("[green]" + session.locale().t("save"), () -> {
                        if (draft.map == null) { session.locale().send("error-no-map"); return; }
                        eventDataRepository.save(draft);
                        session.clearDraft(EventData.class);
                        events(uuid, 1);
                    })
                    .addLocal("[red]" + session.locale().t("cancel"), () -> {
                        session.clearDraft(EventData.class);
                        main(uuid);
                    })
                .end()
                .apply(b -> {
                    if (session.player.admin) {
                        b.addRow(draft.isMajor ? session.locale().t("event-menu-edit-major-active") : session.locale().t("event-menu-edit-major-inactive"), () -> {
                            draft.isMajor = !draft.isMajor; edit(uuid);
                        });
                    }
                })
                .show();
    }

    public void event(String uuid, EventData event) {
        Session session = sessionService.get(uuid).clear();
        MapData mapData = mapDataRepository.findById(event.map);
        PlayerData authorData = playerDataRepository.findById(event.author);

        String yes = session.locale().t("yes");
        String no = session.locale().t("no");

        var builder = session.builder()
                .title("event-menu-event-title")
                .content("event-menu-event-content", args(
                        "name", event.name, "description", event.description,
                        "author", (authorData == null) ? "Unknown" : authorData.nickname,
                        "mapName", (mapData == null) ? "" : mapData.name,
                        "isMajor", event.isMajor ? yes : no, "isConducted", event.isFinished ? yes : no,
                        "isActive", event.isActive ? yes : no, "isTemporary", event.isTemporary ? yes : no,
                        "createdEventTime", formatTime(event.createdModelTime, session),
                        "plannedStartTime", formatTime(event.plannedStartTime, session),
                        "plannedEndTime", formatTime(event.plannedEndTime, session),
                        "like", event.like, "dislike", event.dislike
                ));

        Boolean currentVote = session.data.eventVotes.get(event.id.toString());
        String likeTxt = Boolean.TRUE.equals(currentVote) ? session.locale().t("map-vote-like-selected") : session.locale().t("map-vote-like");
        String dislikeTxt = Boolean.FALSE.equals(currentVote) ? session.locale().t("map-vote-dislike-selected") : session.locale().t("map-vote-dislike");

        builder.addRow(likeTxt, () -> { eventService.handleReputation(session.player, true, event); event(uuid, event); },
                       dislikeTxt, () -> { eventService.handleReputation(session.player, false, event); event(uuid, event); });

        if (!voteService.isVoting()) {
            builder.start()
                .addLocal(session.locale().t("event-vote"), () -> eventService.startVoteSession(session.player, event, false))
                .ifAdd(session.player.admin, session.locale().t("event-avote"), () -> eventService.startVoteSession(session.player, event, true))
                .end();
        }

        boolean isOwner = session.data.id != null && session.data.id.equals(event.author);
        if (!event.isFinished && !event.isActive && (!event.isMajor || session.player.admin) && (isOwner || session.player.admin)) {
            builder.addLocalRow("event-menu-edit", () -> {
                session.pushHistory(() -> event(uuid, event));
                session.setDraft(event);
                edit(uuid);
            });
        }

        builder.addLocalRow("event-menu-events", () -> { session.pushHistory(() -> event(uuid, event)); events(uuid, 1); });

        if (mapData != null) {
            builder.addLocalRow("event-menu-event-map", () -> {
                session.pushHistory(() -> event(uuid, event));
                mapMenu.get().map(uuid, mapData);
            });
        }

        builder.addNavigationRow().show();
    }

    public void events(String uuid, int page) {
        Session session = sessionService.get(uuid).clear();
        int total = (int) eventDataRepository.count(session.sortStatus);
        int perPage = globalConfig.eventsPerPage;
        var pagination = CustomGatherers.calculatePagination(total, perPage);

        int validPage = pagination.clampPage(page);
        int skip = (validPage - 1) * perPage;
        List<EventData> events = eventDataRepository.findPage(skip, perPage, session.sortStatus);

        String menuContent;
        if (total == 0) {
            menuContent = session.locale().t("event-menu-events-empty");
        } else {
            menuContent = session.locale().t("event-menu-events-content", args(
                "page", validPage,
                "total", pagination.totalPages()
            ));
        }

        session.builder()
                .title("event-menu-events-title")
                .rawContent(menuContent)
                .start()
                    .addStatusButton("finished", () -> events(uuid, 1))
                    .addStatusButton("major", () -> events(uuid, 1))
                    .addStatusButton("active", () -> events(uuid, 1))
                .end()
                .start()
                    .ifAddLocal(validPage > 1, "previous", () -> events(uuid, validPage - 1))
                    .ifAddLocal(validPage < pagination.totalPages(), "next", () -> events(uuid, validPage + 1))
                .end()
                .addForEach(events, (b, e) -> b.addRow(e.isActive ? session.locale().t("event-menu-events-selected", args("name", e.name)) : e.name, () -> {
                    session.pushHistory(() -> events(uuid, validPage));
                    event(uuid, e);
                }))
                .addLocalRow("event-menu-main", () -> { session.clearHistory(); main(uuid); })
                .addNavigationRow()
                .show();
    }

    public void mapSelection(String uuid, int page) {
        Session session = sessionService.get(uuid).clear();
        Seq<Map> maps = mapService.getAvailableMaps();
        var pagination = CustomGatherers.calculatePagination(maps.size, globalConfig.mapsPerPage);

        int validPage = pagination.clampPage(page);
        session.builder()
                .title("event-menu-maps-title")
                .content("event-menu-maps-content", args("page", validPage, "total", pagination.totalPages()))
                .start()
                    .ifAddLocal(validPage > 1, "previous", () -> mapSelection(uuid, validPage - 1))
                    .ifAddLocal(validPage < pagination.totalPages(), "next", () -> mapSelection(uuid, validPage + 1))
                .end()
                .addForEach(SeqStream.of(maps).gather(CustomGatherers.page(globalConfig.mapsPerPage, validPage)).flatMap(List::stream)::iterator, (b, m) -> b.addRow(m.name(), () -> {
                    MapData data = mapDataRepository.findOrCreate(m.plainName(), m.file.name(), m.author(), Vars.state.rules.mode().name());
                    session.getDraft(EventData.class).map = data.id;
                    edit(uuid);
                }))
                .addNavigationRow()
                .show();
    }

    private long parseTime(String input) {
        if (input == null || input.isEmpty()) return 0;
        try {
            if (input.startsWith("+")) {
                long now = System.currentTimeMillis();
                String valueStr = input.substring(1, input.length() - 1);
                char unit = input.charAt(input.length() - 1);
                long value = Long.parseLong(valueStr);
                return now + switch (unit) {
                    case 'm' -> value * 60_000L;
                    case 'h' -> value * 3_600_000L;
                    case 'd' -> value * 86_400_000L;
                    default -> 0;
                };
            }
            return Long.parseLong(input);
        } catch (Exception e) { return 0; }
    }
}
