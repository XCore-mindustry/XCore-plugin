package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Localizer;
import mindustry.game.Team;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.InMemoryLadderStore;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderSeasons;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.match.InMemoryMatchStore;
import org.xcore.plugin.rating.match.MatchParticipant;
import org.xcore.plugin.rating.match.MatchRecord;
import org.xcore.plugin.rating.match.MatchReport;
import org.xcore.plugin.rating.prize.InMemoryPrizeGrantRepository;
import org.xcore.plugin.rating.season.InMemorySeasonStore;
import org.xcore.plugin.rating.season.SeasonAnnouncer;
import org.xcore.plugin.rating.season.SeasonResolver;
import org.xcore.plugin.rating.season.SeasonSchedule;
import org.xcore.plugin.rating.view.LadderViews;
import org.xcore.plugin.rating.view.MatchPresenters;
import org.xcore.plugin.rating.view.StandardMatchPresenter;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchHistoryUiControllerTest {
    private static final Instant NOW = Instant.parse("2026-10-09T18:00:00Z");
    private static final String VIEWER = "viewer-uuid";
    private static final LadderDefinition MINI_PVP = new LadderDefinition("minipvp", "top-menu-category-mini-pvp",
            RatingPolicy.teamEloV1());
    private static final LadderDefinition FFA = new LadderDefinition("ffa", "top-menu-category-hexed",
            RatingPolicy.placementEloV1());

    private InMemoryMatchStore matches;
    private PlayerMenu playerMenu;
    private SessionService sessionService;
    private Deque<Runnable> mainThread;
    private Session session;
    private MatchHistoryMenu menu;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        matches = new InMemoryMatchStore();
        // The second season runs, so the first season's matches sit under a band of their own.
        LadderSeasons seasons = new LadderSeasons() {
            @Override
            public void open(LadderDefinition definition, Runnable onSeasonChanged) {
            }

            @Override
            public int current(String ladderId) {
                return 2;
            }

            @Override
            public int at(String ladderId, Instant when) {
                return 2;
            }

            @Override
            public int seed(LadderDefinition definition, int previousRating) {
                return previousRating;
            }

            @Override
            public void matchSettled(String ladderId, int season) {
            }
        };
        LadderService ladders = new LadderService(new InMemoryLadderStore(), new InMemoryIdempotencyLedger(),
                seasons, matches);
        ladders.register(MINI_PVP);
        ladders.register(FFA);

        MatchPresenters presenters = new MatchPresenters();
        presenters.register(MINI_PVP.id(), new StandardMatchPresenter(Iconc.modePvp));

        SeasonAnnouncer announcer = mock(SeasonAnnouncer.class);
        when(announcer.followed()).thenReturn(List.of(MINI_PVP));
        PlayerDataRepository players = mock(PlayerDataRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        LadderViews views = new LadderViews(new InMemorySeasonStore(), new SeasonResolver(),
                SeasonSchedule.from(new TomlSecretsConfig().rating.seasons), players,
                new InMemoryPrizeGrantRepository(), clock);

        playerMenu = mock(PlayerMenu.class);
        sessionService = mock(SessionService.class);
        mainThread = new ArrayDeque<>();
        Async async = new Async(InlineStorageExecutor.create(), mainThread::add);
        menu = new MatchHistoryMenu(ladders, announcer, views, presenters, () -> playerMenu,
                mock(MenuService.class), sessionService, async, clock);

        PlayerData viewer = new PlayerData(VIEWER, true);
        viewer.nickname = "[#ff8800]ospx";
        Bundle bundle = mock(Bundle.class);
        Localizer localizer = mock(Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        session = new Session(new TomlSecretsConfig(), bundle, null, players, player, viewer);
        when(sessionService.get(VIEWER)).thenReturn(session);
    }

    /** A two-team match of the viewer's, with names and a map as long as they come. */
    private static MatchRecord teamMatch(String id, Instant ended, int season, boolean viewerWon, boolean rated) {
        List<MatchParticipant> participants = new ArrayList<>();
        int winners = viewerWon ? Team.sharded.id : Team.crux.id;
        for (int i = 0; i < 6; i++) {
            int team = i < 3 ? Team.sharded.id : Team.crux.id;
            boolean won = team == winners;
            String uuid = i == 0 ? VIEWER : "uuid-player-" + id + "-" + i;
            String name = i == 5 ? "late_guy" : "[#00ff88]ОченьДлинныйНикнеймИгрокаБезПробелов" + i;
            MatchParticipant participant = !rated || i == 5
                    ? MatchParticipant.uncounted(uuid, name, team, won ? 1 : 2, won, 1000,
                    rated ? "late_join" : "match_unrated")
                    : MatchParticipant.counted(uuid, name, team, won ? 1 : 2, won, 1628 + i, won ? 124 : -124,
                    won ? "winner" : "defeated");
            participants.add(participant.withParticipation(i == 0 ? 0.73 : 1.0));
        }
        return MatchRecord.of(MINI_PVP.id(), season, id, "team-elo-v2", rated ? null : "not_enough_players",
                new MatchReport(ended.minus(Duration.ofMinutes(11)), ended, null,
                        "Ancient Caldera Of The Very Long Map Name", participants));
    }

    /** A free-for-all round of 24, the viewer somewhere in the middle. */
    private static MatchRecord bigRound(String id, Instant ended) {
        List<MatchParticipant> participants = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String uuid = i == 7 ? VIEWER : "uuid-round-" + i;
            participants.add(MatchParticipant.counted(uuid, "ДлинноеИмяУчастникаРаунда" + i, null, i + 1, i == 0,
                    1500 - i, 30 - 3 * i, i == 0 ? "winner" : "defeated").withExtra(Map.of("hexes", 24 - i)));
        }
        return MatchRecord.of(FFA.id(), 2, id, "placement-elo-v1", null,
                new MatchReport(ended.minus(Duration.ofMinutes(25)), ended, "TIMEOUT", null, participants));
    }

    private void crowdedHistory() {
        for (int i = 0; i < 12; i++) {
            Instant ended = NOW.minus(Duration.ofHours(i * 7L)).minusSeconds(30);
            matches.record(teamMatch("m" + i, ended, i < 6 ? 2 : 1, i % 2 == 0, i != 3));
        }
        matches.record(bigRound("round", NOW.minus(Duration.ofMinutes(5))));
    }

    private MatchHistoryUiController controller() {
        return menu.controller(session);
    }

    private MatchHistoryUiController.Model open(MatchHistoryUiController controller, String ladderId) {
        return controller.model(controller.fetch(controller.query(ladderId), VIEWER));
    }

    /**
     * Sends {@code event} to a dialog showing {@code model} and, once storage has answered, the
     * event that answer comes back as.
     */
    @SuppressWarnings("unchecked")
    private UpdateResult<MatchHistoryUiController.Model> loadThrough(MatchHistoryUiController controller,
                                                                     MatchHistoryUiController.Model model,
                                                                     MatchHistoryUiController.Event event) {
        UiSession<MatchHistoryUiController.Model, MatchHistoryUiController.Event> ui = mock(UiSession.class);
        when(ui.model()).thenReturn(model);
        session.setActiveUiSession(ui);

        UpdateResult<MatchHistoryUiController.Model> pending = controller.update(model, event, null);
        // Until storage answers the dialog keeps showing what it had.
        assertThat(pending.isNoop()).isTrue();
        while (!mainThread.isEmpty()) {
            mainThread.poll().run();
        }
        ArgumentCaptor<MatchHistoryUiController.Event> dispatched =
                ArgumentCaptor.forClass(MatchHistoryUiController.Event.class);
        verify(ui).dispatch(dispatched.capture());
        return controller.update(model, dispatched.getValue(), null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en"})
    @DisplayName("a full page of matches fits every screen, and no button carries a player's UUID")
    void list_isLaidOutForEveryScreen(String language) {
        session.localization = LayoutAssert.localization(language);
        crowdedHistory();
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model model = open(controller, null);

        assertThat(model.ladderId()).isEqualTo(MINI_PVP.id());
        assertThat(model.matches()).hasSize(MatchHistoryUiController.MATCHES_PER_PAGE);
        assertThat(model.totalPages()).isEqualTo(2);
        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            LayoutAssert.assertLaidOut(window, screen);
            assertThat(LayoutAssert.actions(window)).contains("action:ladder:ffa", "action:match:0", "action:match:9",
                    "action:page:next", "action:refresh");
        }
        VNode rendered = controller.render(model);
        LayoutAssert.assertFitsPacket(rendered, "a full page of matches in " + language);
        String text = LayoutAssert.allText(rendered);
        assertThat(text).doesNotContain("match-history-", "season-title");
        // The first season's matches sit under a band that names it.
        assertThat(text).contains(language.equals("en") ? "Season 1" : "Сезон 1");
        assertThat(LayoutAssert.dsl(rendered)).doesNotContain("uuid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en"})
    @DisplayName("a two-team match fits every screen, with the viewer's result and both teams")
    void teamMatch_isLaidOutForEveryScreen(String language) {
        session.localization = LayoutAssert.localization(language);
        crowdedHistory();
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model list = open(controller, null);

        MatchHistoryUiController.Model model = loadThrough(controller, list,
                new MatchHistoryUiController.Event.OpenMatch(1)).model();

        assertThat(model.open()).isNotNull();
        assertThat(model.open().participants()).hasSize(6);
        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            LayoutAssert.assertLaidOut(window, screen);
            assertThat(LayoutAssert.actions(window)).contains("action:player:0", "action:player:5", "action:list");
        }
        VNode rendered = controller.render(model);
        LayoutAssert.assertFitsPacket(rendered, "a two-team match in " + language);
        String text = LayoutAssert.allText(rendered);
        assertThat(text).doesNotContain("match-history-", "season-title");
        assertThat(text).contains("73%");
        assertThat(LayoutAssert.dsl(rendered)).doesNotContain("uuid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en"})
    @DisplayName("a round of 24 is shown a page of participants at a time and still fits")
    void bigRound_isPaged(String language) {
        session.localization = LayoutAssert.localization(language);
        crowdedHistory();
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model list = open(controller, FFA.id());
        assertThat(list.matches()).hasSize(1);

        MatchHistoryUiController.Model model = loadThrough(controller, list,
                new MatchHistoryUiController.Event.OpenMatch(0)).model();
        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            LayoutAssert.assertLaidOut(window, screen);
            assertThat(LayoutAssert.actions(window)).contains("action:player:11", "action:players:2")
                    .doesNotContain("action:player:12");
        }
        LayoutAssert.assertFitsPacket(controller.render(model), "a round of 24 in " + language);

        MatchHistoryUiController.Model second = controller.update(model,
                new MatchHistoryUiController.Event.ParticipantsPage(2), null).model();
        assertThat(second.participantsPage()).isEqualTo(2);
        assertThat(LayoutAssert.actions(controller.render(second))).contains("action:player:12", "action:player:23");
        assertThat(LayoutAssert.allText(controller.render(second))).doesNotContain("match-history-");
    }

    @Test
    @DisplayName("without matches the list says so and since when the history is kept")
    void emptyList_saysSo() {
        session.localization = LayoutAssert.localization("ru");
        matches.record(MatchRecord.of(MINI_PVP.id(), 2, "theirs", "v1", null, new MatchReport(
                Instant.parse("2026-10-01T10:00:00Z"), Instant.parse("2026-10-01T10:10:00Z"), null, null,
                List.of(MatchParticipant.counted("someone", "Someone", 1, 1, true, 1000, 10, "winner")))));
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model model = open(controller, null);

        assertThat(model.matches()).isEmpty();
        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            LayoutAssert.assertLaidOut(window, screen);
            assertThat(LayoutAssert.allText(window)).contains("01.10.2026");
            assertThat(LayoutAssert.actions(window)).doesNotContain("action:page:prev", "action:page:next");
        }
    }

    @Test
    @DisplayName("turning a page patches the list and the pager only, and the way back returns to the first page")
    void paging() {
        crowdedHistory();
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model first = open(controller, null);

        UpdateResult<MatchHistoryUiController.Model> next = loadThrough(controller, first,
                new MatchHistoryUiController.Event.NextPage());
        assertThat(next.model().page()).isEqualTo(2);
        assertThat(next.model().matches()).hasSize(2);
        assertThat(next.model().nextCursor()).isNull();
        assertThat(next.dirtySlots()).containsExactlyElementsOf(Screen.slots(
                MatchHistoryUiController.SLOT_LIST, MatchHistoryUiController.SLOT_PAGER));
        // The form line is the latest matches, whatever page is open.
        assertThat(next.model().summary().recent()).hasSize(MatchHistoryUiController.MATCHES_PER_PAGE);

        UpdateResult<MatchHistoryUiController.Model> back = loadThrough(controller, next.model(),
                new MatchHistoryUiController.Event.PrevPage());
        assertThat(back.model().page()).isEqualTo(1);
        assertThat(back.model().matches()).extracting(MatchRecord::matchId)
                .isEqualTo(first.matches().stream().map(MatchRecord::matchId).toList());
    }

    @Test
    @DisplayName("a match opens from the list, and the list comes back as it was without being read again")
    void openMatch_andBack() {
        crowdedHistory();
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model list = open(controller, null);

        MatchHistoryUiController.Model match = loadThrough(controller, list,
                new MatchHistoryUiController.Event.OpenMatch(2)).model();
        assertThat(match.open().matchId()).isEqualTo(list.matches().get(2).matchId());

        UpdateResult<MatchHistoryUiController.Model> back = controller.update(match,
                new MatchHistoryUiController.Event.BackToList(), null);
        assertThat(back.model().open()).isNull();
        assertThat(back.model().matches()).isEqualTo(list.matches());
        assertThat(mainThread).isEmpty();
    }

    @Test
    @DisplayName("a participant's profile opens by position, and its back button returns to the match")
    void inspectPlayer_opensProfile() {
        crowdedHistory();
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model list = open(controller, null);
        MatchHistoryUiController.Model match = loadThrough(controller, list,
                new MatchHistoryUiController.Event.OpenMatch(0)).model();
        MatchParticipant target = MatchHistoryUiController.participants(match.open()).get(2);
        Session online = mock(Session.class);
        online.data = new PlayerData(target.uuid(), true);
        when(sessionService.get(target.uuid())).thenReturn(online);

        controller.update(match, new MatchHistoryUiController.Event.InspectPlayer(2), null);

        verify(playerMenu).openProfileUi(session, online.data);
        assertThat(session.hasHistory()).isTrue();
    }

    @Test
    @DisplayName("a position outside the match opens nothing")
    void inspectPlayer_ignoresUnknownPosition() {
        crowdedHistory();
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model list = open(controller, null);

        controller.update(list, new MatchHistoryUiController.Event.InspectPlayer(0), null);
        controller.update(list, new MatchHistoryUiController.Event.OpenMatch(99), null);

        verify(playerMenu, never()).openProfileUi(any(), any());
        assertThat(mainThread).isEmpty();
    }

    @Test
    @DisplayName("a failed read keeps what is on screen and says it failed")
    void loadFailure_showsFeedback() {
        crowdedHistory();
        MatchHistoryUiController controller = controller();
        MatchHistoryUiController.Model list = open(controller, null);

        MatchHistoryUiController.Model failed = controller.update(list,
                new MatchHistoryUiController.Event.LoadFailed(), null).model();

        assertThat(failed.matches()).isEqualTo(list.matches());
        assertThat(failed.feedback()).contains("match-history-load-failed");
    }

    @Test
    @DisplayName("the buttons of the dialog are read back into events")
    void parseEvent() {
        MatchHistoryUiController controller = controller();
        assertThat(controller.parseEvent(result("action:ladder:ffa")))
                .isEqualTo(new MatchHistoryUiController.Event.SelectLadder("ffa"));
        assertThat(controller.parseEvent(result("action:match:3")))
                .isEqualTo(new MatchHistoryUiController.Event.OpenMatch(3));
        assertThat(controller.parseEvent(result("action:player:11")))
                .isEqualTo(new MatchHistoryUiController.Event.InspectPlayer(11));
        assertThat(controller.parseEvent(result("action:players:2")))
                .isEqualTo(new MatchHistoryUiController.Event.ParticipantsPage(2));
        assertThat(controller.parseEvent(result("action:list")))
                .isEqualTo(new MatchHistoryUiController.Event.BackToList());
        assertThat(controller.parseEvent(result("action:page:next")))
                .isEqualTo(new MatchHistoryUiController.Event.NextPage());
        assertThat(controller.parseEvent(result("action:player:someone")))
                .isEqualTo(new MatchHistoryUiController.Event.Close());
        assertThat(controller.parseEvent(null)).isEqualTo(new MatchHistoryUiController.Event.Close());
    }

    @Test
    @DisplayName("times are relative while they are recent and dates after that")
    void when_isRelative() {
        MatchHistoryUiController controller = controller();
        session.localization = LayoutAssert.localization("ru");
        var local = session.localization;
        assertThat(controller.when(NOW.minusSeconds(20), local)).isEqualTo("только что");
        assertThat(controller.when(NOW.minus(Duration.ofMinutes(5)), local)).isEqualTo("5 мин назад");
        assertThat(controller.when(NOW.minus(Duration.ofHours(3)), local)).isEqualTo("3 ч назад");
        assertThat(controller.when(NOW.minus(Duration.ofHours(30)), local)).isEqualTo("вчера");
        assertThat(controller.when(Instant.parse("2026-09-12T10:00:00Z"), local)).isEqualTo("12.09");
        assertThat(controller.when(Instant.parse("2025-09-12T10:00:00Z"), local)).isEqualTo("12.09.2025");
    }

    @Test
    @DisplayName("the menu opens on the tab of the mode this server hosts")
    void tabs_hostedFirst() {
        List<MatchHistoryUiController.LadderTab> tabs = menu.tabs(null);
        assertThat(tabs).extracting(MatchHistoryUiController.LadderTab::id).containsExactly("minipvp", "ffa");
        assertThat(tabs.getFirst().icon()).isEqualTo(Iconc.modePvp);
    }

    private static MenuResult result(String action) {
        return new MenuResult(action);
    }
}
