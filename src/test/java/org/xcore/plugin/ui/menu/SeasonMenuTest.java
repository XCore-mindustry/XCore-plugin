package org.xcore.plugin.ui.menu;

import org.xcore.plugin.rating.season.SeasonPrizes;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Localizer;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.InMemoryLadderStore;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.season.InMemorySeasonStore;
import org.xcore.plugin.rating.season.SeasonFinalizer;
import org.xcore.plugin.rating.season.SeasonLifecycleService;
import org.xcore.plugin.rating.season.SeasonAnnouncer;
import org.xcore.plugin.rating.season.SeasonResolver;
import org.xcore.plugin.rating.season.SeasonSchedule;
import org.xcore.plugin.rating.view.LadderViews;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MindustryMenuGateway;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.runtime.UpdateResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeasonMenuTest {
    private static final Instant START = Instant.parse("2026-10-03T12:00:00Z");

    private InMemorySeasonStore seasons;
    private InMemoryLadderStore standings;
    private LadderService ladders;
    private Ladder duel;
    private Ladder brawl;
    private SeasonAnnouncer announcer;
    private TopCategoryRegistry topCategories;
    private TopMenu topMenu;
    private MindustryMenuGateway gateway;
    private SeasonMenu seasonMenu;
    private Session session;
    private LadderViews views;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        seasons = new InMemorySeasonStore();
        standings = new InMemoryLadderStore();
        TomlSecretsConfig config = new TomlSecretsConfig();
        SeasonResolver resolver = new SeasonResolver();
        SeasonSchedule schedule = SeasonSchedule.from(config.rating.seasons);
        InMemoryIdempotencyLedger ledger = new InMemoryIdempotencyLedger();
        Clock clock = Clock.fixed(START, ZoneOffset.UTC);
        SeasonLifecycleService lifecycle = new SeasonLifecycleService(seasons, resolver, schedule,
                new SeasonFinalizer(standings, mock(PlayerDataRepository.class), schedule), SeasonPrizes.NONE, ledger,
                mock(AuditService.class), config, new Async(InlineStorageExecutor.create(), Runnable::run), clock);
        ladders = new LadderService(standings, ledger, lifecycle);
        duel = ladders.register(new LadderDefinition("duel", "top-menu-category-duel", RatingPolicy.teamEloV1()));
        brawl = ladders.register(new LadderDefinition("brawl", "top-menu-category-brawl", RatingPolicy.teamEloV1()));

        views = new LadderViews(seasons, resolver, schedule, mock(PlayerDataRepository.class), clock);
        announcer = mock(SeasonAnnouncer.class);
        topCategories = new TopCategoryRegistry();
        topCategories.register(views.topCategory("DUEL", 10, duel));
        topMenu = mock(TopMenu.class);

        SessionService sessionService = mock(SessionService.class);
        gateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        MenuService menuService = new MenuService(sessionProvider, gateway);

        seasonMenu = new SeasonMenu(ladders, announcer, views, topCategories, topMenu, menuService, sessionService,
                new Async(InlineStorageExecutor.create(), Runnable::run));

        Bundle bundle = mock(Bundle.class);
        Localizer localizer = mock(Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("viewer-1", true);
        data.nickname = "Viewer";
        session = new Session(new TomlSecretsConfig(), bundle, menuService, mock(PlayerDataRepository.class), player, data);
        when(sessionService.get("viewer-1")).thenReturn(session);
    }

    @Test
    @DisplayName("only the ladders of the modes this server hosts are shown")
    void load_showsFollowedLadders() {
        when(announcer.followed()).thenReturn(List.of(duel.definition()));
        standings.put(new LadderStanding("duel", 1, "viewer-1", 1300, 1300, 4, 3, Map.of()));

        SeasonUiController.SeasonModel model = seasonMenu.load("viewer-1");

        assertThat(model.ladders()).hasSize(1);
        SeasonUiController.LadderEntry entry = model.ladders().getFirst();
        assertThat(entry.overview().progress().ladder().id()).isEqualTo("duel");
        assertThat(entry.overview().participants()).isEqualTo(1);
        assertThat(entry.overview().progress().rank()).isEqualTo(1L);
        assertThat(entry.topCategoryId()).isEqualTo("DUEL");
    }

    @Test
    @DisplayName("a server that hosts no rated mode lists every ladder; one without a /top category gets no button")
    void load_fallsBackToAllLadders() {
        when(announcer.followed()).thenReturn(List.of());

        SeasonUiController.SeasonModel model = seasonMenu.load("viewer-1");

        assertThat(model.ladders()).extracting(e -> e.overview().progress().ladder().id())
                .containsExactlyInAnyOrder("duel", "brawl");
        assertThat(model.ladders().stream().filter(e -> e.topCategoryId() == null)
                .map(e -> e.overview().progress().ladder().id())).containsExactly("brawl");
    }

    @Test
    @DisplayName("open reads the seasons off the game thread and mounts the dialog")
    void open_mountsDialog() {
        when(announcer.followed()).thenReturn(List.of(duel.definition()));

        seasonMenu.open(session);

        assertThat(session.activeUiSession()).isNotNull();
        assertThat(session.activeUiSession().model()).isInstanceOf(SeasonUiController.SeasonModel.class);
        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("the dialog lists a card per ladder with a button to its leaderboard, and says so when there are none")
    void render_cardsAndEmptyState() {
        when(announcer.followed()).thenReturn(List.of(duel.definition()));
        SeasonUiController controller = new SeasonUiController(seasonMenu, views, session);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);

        String dsl = UiDslWriter.write(compiler.compile(controller.render(seasonMenu.load("viewer-1"))));
        assertThat(dsl).contains("action:top:DUEL", "action:close");

        String empty = UiDslWriter.write(compiler.compile(controller.render(new SeasonUiController.SeasonModel(List.of()))));
        assertThat(empty).contains("season-menu-empty").doesNotContain("action:top:");
    }

    @Test
    @DisplayName("choosing a leaderboard opens /top there and leaves a way back")
    void update_openTop() {
        SeasonUiController controller = new SeasonUiController(seasonMenu, views, session);
        SeasonUiController.SeasonModel model = new SeasonUiController.SeasonModel(List.of());

        assertThat(controller.parseEvent(new MenuResult("action:top:DUEL")))
                .isEqualTo(new SeasonUiController.SeasonEvent.OpenTop("DUEL"));
        assertThat(controller.parseEvent(new MenuResult("action:close")))
                .isInstanceOf(SeasonUiController.SeasonEvent.Close.class);
        assertThat(controller.parseEvent(new MenuResult((String) null)))
                .isInstanceOf(SeasonUiController.SeasonEvent.Close.class);

        UpdateResult<SeasonUiController.SeasonModel> result =
                controller.update(model, new SeasonUiController.SeasonEvent.OpenTop("DUEL"), null);

        assertThat(result.close()).isFalse();
        assertThat(session.hasHistory()).isTrue();
        verify(topMenu).openTopUi(session, "DUEL");
    }
}
