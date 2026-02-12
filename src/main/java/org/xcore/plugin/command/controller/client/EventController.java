package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.SeqStream;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.ui.MenuSession;
import org.xcore.plugin.ui.StatusEnum;
import org.xcore.plugin.vote.VoteEvent;
import org.xcore.plugin.vote.VoteEventFactory;
import org.xcore.plugin.vote.VoteService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class EventController implements CloudClientController {

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;
    private final PlayerDataRepository playerDataRepository;
    private final PlayerSessionService playerSessionService;
    private final GlobalConfig globalConfig;
    private final BundleService bundle;
    private final VoteService voteService;
    private final VoteEventFactory voteEventFactory;
    private final MapService mapService;
    private final Provider<MapController> mapController;
    private final MenuService menuService;

    @Inject
    public EventController(EventDataRepository eventDataRepository, MapDataRepository mapDataRepository, PlayerDataRepository playerDataRepository,
                           PlayerSessionService playerSessionService,
                           GlobalConfig globalConfig,
                           BundleService bundle, VoteService voteService, VoteEventFactory voteEventFactory,
                           MapService mapService, Provider<MapController> mapController, MenuService menuService) {
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.globalConfig = globalConfig;
        this.bundle = bundle;
        this.voteService = voteService;
        this.voteEventFactory = voteEventFactory;
        this.mapService = mapService;
        this.mapController = mapController;
        this.menuService = menuService;
    }

    @Command("event")
    public void event(XCoreSender sender) {
        handleMain(sender.player());
    }

    @Command("events [page]")
    public void events(XCoreSender sender, @Argument("page") @Default("1") int page) {
        handleEvents(sender.player(), page);
    }

    private Optional<EventData> currentEvent() {
        return this.eventDataRepository.findActive();
    }

    public void handleMain(Player player) {
        EventData event = currentEvent().orElse(null);
        String menuTitle = bundle.format(bundle.locale(player), "event-menu-main-title", args());
        String menuContent = bundle.format(bundle.locale(player), "event-menu-main-content", args());
        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(bundle.format(bundle.locale(player), "event-menu-create-start", args()), () -> {
            session.pushHistory(() -> handleMain(player));
            handleCreateStart(player, null);
        }));
        row1.add(session.add(bundle.format(bundle.locale(player), "event-menu-events", args()), () -> {
            session.pushHistory(() -> handleMain(player));
            handleEvents(player, 1);
        }));
        rows.add(row1);

        if (event != null) {
            List<String> row2 = new ArrayList<>();
            row2.add(session.add(bundle.format(bundle.locale(player), "event-menu-this-event", args()), () -> {
                session.pushHistory(() -> handleMain(player));
                handleEvent(player, event);
            }));
            rows.add(row2);
        }

        List<String> row3 = new ArrayList<>();
        if (voteService.getCurrentSession() instanceof VoteEvent && player.admin()) {
            row3.add(session.add(bundle.format(bundle.locale(player), "event-menu-vote-stop", args()), voteService::endVote));
        }
        if (event != null && event.isActive && player.admin()) {
            row3.add(session.add(bundle.format(bundle.locale(player), "event-menu-stop", args()), eventDataRepository::finishActiveEvent));
        }
        rows.add(row3);

        menuService.addNavigationRow(player, session, rows);
        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

    public void handleCreateStart(Player player, MapData map) {
        String title = bundle.format(bundle.locale(player), "event-menu-create-start-title", args());
        String message = bundle.format(bundle.locale(player), "event-menu-create-start-message", args());
        String defaultText = bundle.format(bundle.locale(player), "event-menu-create-start-default", args("playerName", player.name));

        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        session.setDraft(new EventData());

        PlayerData playerData = playerSessionService.get(player.uuid());
        if (playerData != null) {
            session.getDraft(EventData.class).author = playerData.id;
        }

        session.textHandler = (text) -> {
            session.getDraft(EventData.class).name = text;
            if (map != null) {
                session.getDraft(EventData.class).map = map.id;
            }
            handleEdit(player);
        };

        Call.textInput(player.con, menuService.getTextId(), title, message, 20, defaultText, false);
    }

    private void handleEdit(Player player) {
        MenuSession session = menuService.get(player.uuid());
        if (!session.hasDraft(EventData.class)) {
            handleMain(player);
            return;
        }

        EventData draft = session.getDraft(EventData.class);
        MapData mapData = mapDataRepository.findById(draft.map);
        PlayerData playerData = playerDataRepository.findById(draft.author);

        String yes = bundle.format(bundle.locale(player), "yes", args());
        String no = bundle.format(bundle.locale(player), "no", args());

        String menuTitle = bundle.format(bundle.locale(player), "event-menu-edit-title", args());
        String menuContent = bundle.format(bundle.locale(player), "event-menu-edit-content", args(
                "name", draft.name,
                "description", draft.description,
                "author", (playerData == null) ? "" : playerData.nickname,
                "mapName", (mapData == null) ? "" : mapData.name,
                "isMajor", draft.isMajor ? yes : no,
                "isTemporary", draft.isTemporary ? yes : no,
                "plannedStartTime", draft.plannedStartTime,
                "plannedEndTime", draft.plannedEndTime
        ));

        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(bundle.format(bundle.locale(player), "event-menu-edit-name", args()), () -> {
            session.textHandler = text -> { draft.name = text; handleEdit(player); };
            Call.textInput(player.con, menuService.getTextId(),
                    bundle.format(bundle.locale(player), "event-menu-edit-name-title", args()),
                    bundle.format(bundle.locale(player), "event-menu-edit-name-message", args()),
                    24, draft.name, false);
        }));
        row1.add(session.add(bundle.format(bundle.locale(player), "event-menu-edit-description", args()), () -> {
            session.textHandler = text -> { draft.description = text; handleEdit(player); };
            Call.textInput(player.con, menuService.getTextId(),
                    bundle.format(bundle.locale(player), "event-menu-edit-description-title", args()),
                    bundle.format(bundle.locale(player), "event-menu-edit-description-message", args()),
                    1000, draft.description, false);
        }));
        rows.add(row1);

        List<String> row2 = new ArrayList<>();
        row2.add(session.add(bundle.format(bundle.locale(player), "event-menu-edit-map", args()), () -> {
            session.pushHistory(() -> handleEdit(player));
            handleMapSelection(player, 1);
        }));
        row2.add(session.add(bundle.format(bundle.locale(player),
                draft.isTemporary ? "event-menu-edit-temporary-active" : "event-menu-edit-temporary-inactive", args()), () -> {
            draft.isTemporary = !draft.isTemporary;
            handleEdit(player);
        }));
        rows.add(row2);

        List<String> row3 = new ArrayList<>();
        row3.add(session.add(bundle.format(bundle.locale(player), "event-menu-edit-planned-start", args()), () -> {
            session.textHandler = text -> {
                draft.plannedStartTime = parseTime(text);
                handleEdit(player);
            };
            Call.textInput(player.con, menuService.getTextId(),
                    bundle.format(bundle.locale(player), "event-menu-edit-planned-start-title", args()),
                    bundle.format(bundle.locale(player), "event-menu-edit-planned-start-message", args()),
                    64, "", false);
        }));
        row3.add(session.add(bundle.format(bundle.locale(player), "event-menu-edit-planned-end", args()), () -> {
            session.textHandler = text -> { draft.plannedEndTime = parseTime(text); handleEdit(player); };
            Call.textInput(player.con, menuService.getTextId(),
                    bundle.format(bundle.locale(player), "event-menu-edit-planned-end-title", args()),
                    bundle.format(bundle.locale(player), "event-menu-edit-planned-end-message", args()),
                    10, "", false);
        }));
        rows.add(row3);

        List<String> row4 = new ArrayList<>();
        row4.add(session.add(bundle.format(bundle.locale(player), "save", args()), () -> {
            if (draft.map == null) {
                bundle.send(player, "error-no-map", args());
                return;
            }
            eventDataRepository.save(draft);
            session.clearDraft(EventData.class);
            handleEvents(player, 1);
        }));
        row4.add(session.add(bundle.format(bundle.locale(player), "cancel", args()), () ->
                menuService.clear(player.uuid())));
        rows.add(row4);

        if (player.admin) {
            rows.add(List.of(session.add(bundle.format(bundle.locale(player),
                    draft.isMajor ? "event-menu-edit-major-active" : "event-menu-edit-major-inactive", args()), () -> {
                draft.isMajor = !draft.isMajor;
                handleEdit(player);
            })));
        }

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

    private void handleEvent(Player player, EventData event) {
        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        MapData mapData = mapDataRepository.findById(event.map);
        PlayerData playerData = playerDataRepository.findById(event.author);

        String yes = bundle.format(bundle.locale(player), "yes", args());
        String no = bundle.format(bundle.locale(player), "no", args());

        String menuTitle = bundle.format(bundle.locale(player), "event-menu-event-title", args());
        String menuContent = bundle.format(bundle.locale(player), "event-menu-event-content", args(
                "name", event.name,
                "description", event.description,
                "author", (playerData == null) ? "" : playerData.nickname,
                "mapName", (mapData == null) ? "" : mapData.name,
                "isMajor", event.isMajor ? yes : no,
                "isConducted", event.isFinished ? yes : no,
                "isActive", event.isActive ? yes : no,
                "isTemporary", event.isTemporary ? yes : no,
                "createdEventTime", event.createdModelTime,
                "plannedStartTime", event.plannedStartTime,
                "plannedEndTime", event.plannedEndTime,
                "like", event.like,
                "dislike", event.dislike
        ));

        PlayerData pData = playerSessionService.get(player.uuid());
        Boolean currentVote = pData.eventVotes.get(event.id.toString());

        String likeButtonText = Boolean.TRUE.equals(currentVote)
                ? bundle.format(bundle.locale(player), "map-vote-like-selected", args())
                : bundle.format(bundle.locale(player), "map-vote-like", args());
        String dislikeButtonText = Boolean.FALSE.equals(currentVote)
                ? bundle.format(bundle.locale(player), "map-vote-dislike-selected", args())
                : bundle.format(bundle.locale(player), "map-vote-dislike", args());

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(likeButtonText, () -> handleReputation(player, true, event)));
        row1.add(session.add(dislikeButtonText, () -> handleReputation(player, false, event)));
        rows.add(row1);

        if (!voteService.isVoting()) {
            List<String> row2 = new ArrayList<>();
            row2.add(session.add(bundle.format(bundle.locale(player), "event-vote", args()),
                    () -> startVoteSession(player, event, false)));
            if (player.admin) {
                row2.add(session.add(bundle.format(bundle.locale(player), "event-avote", args()),
                        () -> startVoteSession(player, event, true)));
            }
            rows.add(row2);
        }

        boolean isOwner = pData.id != null && pData.id.equals(event.author);
        if (!event.isFinished && !event.isActive && (!event.isMajor || player.admin) && (isOwner || player.admin)) {
            List<String> row3 = new ArrayList<>();
            row3.add(session.add(bundle.format(bundle.locale(player), "event-menu-edit", args()), () -> {
                session.pushHistory(() -> handleEvent(player, event));
                session.setDraft(event);
                handleEdit(player);
            }));
            rows.add(row3);
        }

        menuService.addNavigationRow(player, session, rows);

        List<String> row4 = new ArrayList<>();
        row4.add(session.add(bundle.format(bundle.locale(player), "event-menu-events", args()), () -> {
            session.clearHistory();
            handleEvents(player, 1);
        }));
        rows.add(row4);

        if (mapData != null) {
            List<String> row5 = new ArrayList<>();
            row5.add(session.add(bundle.format(bundle.locale(player), "event-menu-event-map", args()), () -> {
                session.pushHistory(() -> handleEvent(player, event));
                mapController.get().handleMap(player, mapData);
            }));
            rows.add(row5);
        }

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

    public void handleEvents(Player player, int page) {
        int totalEvents = (int) eventDataRepository.count();
        int perPage = globalConfig.eventsPerPage;
        var pagination = CustomGatherers.calculatePagination(totalEvents, perPage);

        if (totalEvents == 0) {
            bundle.send(player, "event-menu-events-empty", args());
            return;
        }

        int validPage = pagination.clampPage(page);
        String menuTitle = bundle.format(bundle.locale(player), "event-menu-events-title", args());
        String menuContent = bundle.format(bundle.locale(player), "event-menu-events-content", args(
                "page", validPage,
                "total", pagination.totalPages()
        ));

        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        List<List<String>> rows = new ArrayList<>();

        List<String> sortRow = new ArrayList<>();

        Runnable lambda = () -> {handleEvents(player, page); };
        menuService.addStatusButton(player, session, sortRow, "finished", lambda);
        menuService.addStatusButton(player, session, sortRow, "major", lambda);
        menuService.addStatusButton(player, session, sortRow, "active", lambda);

        rows.add(sortRow);

        List<String> navRow = new ArrayList<>();
        if (validPage > 1) {
            navRow.add(session.add(bundle.format(bundle.locale(player), "previous", args()), () -> handleEvents(player, validPage - 1)));
        }
        if (validPage < pagination.totalPages()) {
            navRow.add(session.add(bundle.format(bundle.locale(player), "next", args()), () -> handleEvents(player, validPage + 1)));
        }
        if (!navRow.isEmpty()) rows.add(navRow);

        int skip = (validPage - 1) * perPage;
        List<EventData> events = eventDataRepository.findPage(skip, perPage, session.sortStatus);

        for (EventData event : events) {
            String buttonText = event.isActive ? bundle.format(bundle.locale(player), "event-menu-events-selected", args("name", event.name)) : event.name;
            rows.add(List.of(session.add(buttonText, () -> {
                session.pushHistory(() -> handleEvents(player, validPage));
                handleEvent(player, event);
            })));
        }

        menuService.addNavigationRow(player, session, rows);

        List<String> row1 = new ArrayList<>();
        row1.add(session.add(bundle.format(bundle.locale(player), "event-menu-main", args()), () -> {
            session.clearHistory();
            handleMain(player);
        }));
        rows.add(row1);

        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

    private void handleMapSelection(Player player, int page) {
        Seq<Map> maps = mapService.getAvailableMaps();
        var pagination = CustomGatherers.calculatePagination(maps.size, globalConfig.mapsPerPage);

        if (pagination.totalPages() == 0) {
            bundle.send(player, "error-maps-empty", args());
            return;
        }

        int validPage = pagination.clampPage(page);
        String menuTitle = bundle.format(bundle.locale(player), "event-menu-maps-title", args());
        String menuContent = bundle.format(bundle.locale(player), "event-menu-maps-content", args(
                "page", validPage,
                "total", pagination.totalPages()
        ));

        MenuSession session = menuService.get(player.uuid());
        session.actions.clear();
        if (!session.hasDraft(EventData.class)) {
            handleMain(player);
            return;
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> navRow = new ArrayList<>();
        if (validPage > 1) {
            navRow.add(session.add(bundle.format(bundle.locale(player), "previous", args()), () -> handleMapSelection(player, validPage - 1)));
        }
        if (validPage < pagination.totalPages()) {
            navRow.add(session.add(bundle.format(bundle.locale(player), "next", args()), () -> handleMapSelection(player, validPage + 1)));
        }
        if (!navRow.isEmpty()) rows.add(navRow);

        String gameMode = Vars.state.rules.mode().name();
        SeqStream.of(maps)
                .gather(CustomGatherers.page(globalConfig.mapsPerPage, validPage))
                .flatMap(List::stream)
                .forEach(map -> rows.add(List.of(session.add(map.name(), () -> {
                    MapData data = mapDataRepository.findOrCreate(map.plainName(), map.file.name(), map.author(), gameMode);
                    if (data.id == null) mapDataRepository.save(data);
                    session.getDraft(EventData.class).map = data.id;
                    handleEdit(player);
                }))));

        menuService.addNavigationRow(player, session, rows);
        Call.menu(player.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
    }

    private void startVoteSession(Player player, EventData target, boolean forced) {
        if (voteService.isVoting() && !(voteService.getCurrentSession() instanceof VoteEvent)) {
            bundle.send(player, "error-vote-in-progress", args());
            return;
        } else if (voteService.isVoting() && !forced) {
            bundle.send("error-vote-in-progress", args());
            return;
        } else if (voteService.isVoting() && forced) {
            voteService.endVote();
        }

        if (target == null) {
            bundle.send("error-event-not-found", args());
            return;
        }

        if (forced) {
            eventDataRepository.activateEvent(target);
            bundle.send("commands-artv-event-skipped", args("name", target.name, "nickname", player.coloredName()));
        } else {
            var vote = voteEventFactory.create(target);
            voteService.startVote(vote);
            vote.vote(player, 1);
        }
    }

    private void handleReputation(Player player, boolean like, EventData event) {
        PlayerData p = playerSessionService.get(player.uuid());
        Boolean prev = p.eventVotes.get(event.id.toString());

        if (Boolean.valueOf(like).equals(prev)) {
            bundle.send("error-already-voted", args());
            return;
        }

        if (like) {
            event.like += 1;
            if (prev != null) {
                event.dislike -= 1;
                bundle.send("like-event-changed", args());
            } else {
                bundle.send("like-event-success", args());
            }
        } else {
            event.dislike += 1;
            if (prev != null) {
                event.like -= 1;
                bundle.send("dislike-event-changed", args());
            } else {
                bundle.send("dislike-event-success", args());
            }
        }

        p.eventVotes.put(event.id.toString(), like);
        playerDataRepository.save(p);
        eventDataRepository.save(event);
    }

    private long parseTime(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }

        try {
            if (input.startsWith("+")) {
                long now = System.currentTimeMillis();

                String valueStr = input.substring(1, input.length() - 1);
                char unit = input.charAt(input.length() - 1);

                long value = Long.parseLong(valueStr);

                long millisInSecond = 1000L;
                long millisInMinute = 60 * millisInSecond;
                long millisInHour = 60 * millisInMinute;
                long millisInDay = 24 * millisInHour;

                long offset = switch (unit) {
                    case 'm' -> value * millisInMinute;
                    case 'h' -> value * millisInHour;
                    case 'd' -> value * millisInDay;
                    default -> 0;
                };

                return now + offset;
            }

            return Long.parseLong(input);
        } catch (Exception e) {
            return 0;
        }
    }
}