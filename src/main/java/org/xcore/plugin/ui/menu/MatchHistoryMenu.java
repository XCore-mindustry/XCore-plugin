package org.xcore.plugin.ui.menu;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.season.SeasonAnnouncer;
import org.xcore.plugin.rating.view.LadderProgress;
import org.xcore.plugin.rating.view.LadderViews;
import org.xcore.plugin.rating.view.MatchPresenter;
import org.xcore.plugin.rating.view.MatchPresenters;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.xcore.plugin.ui.kit.Texts.t;

/**
 * Entry point of the match history ({@code /matches}): a player's own rated matches on every
 * ladder this server knows, the ladders of the modes it hosts first.
 */
@Singleton
public class MatchHistoryMenu {

    private final LadderService ladders;
    private final SeasonAnnouncer announcer;
    private final LadderViews views;
    private final MatchPresenters presenters;
    private final Provider<PlayerMenu> playerMenu;
    private final MenuService menuService;
    private final SessionService sessionService;
    private final Async async;
    private final Clock clock;

    @Inject
    public MatchHistoryMenu(LadderService ladders,
                            SeasonAnnouncer announcer,
                            LadderViews views,
                            MatchPresenters presenters,
                            Provider<PlayerMenu> playerMenu,
                            MenuService menuService,
                            SessionService sessionService,
                            Async async) {
        this(ladders, announcer, views, presenters, playerMenu, menuService, sessionService, async, Clock.systemUTC());
    }

    public MatchHistoryMenu(LadderService ladders,
                            SeasonAnnouncer announcer,
                            LadderViews views,
                            MatchPresenters presenters,
                            Provider<PlayerMenu> playerMenu,
                            MenuService menuService,
                            SessionService sessionService,
                            Async async,
                            Clock clock) {
        this.ladders = ladders;
        this.announcer = announcer;
        this.views = views;
        this.presenters = presenters;
        this.playerMenu = playerMenu;
        this.menuService = menuService;
        this.sessionService = sessionService;
        this.async = async;
        this.clock = clock;
    }

    /** {@code /matches}: a fresh start, so the dialog offers no way back to a menu closed long ago. */
    public void matches(String uuid) {
        Session session = sessionService.get(uuid);
        if (session == null) return;
        session.clearHistory();
        open(session, null);
    }

    /**
     * Opens the player's own matches once the first page has been read off the game thread.
     *
     * @param ladderId the tab to open on; {@code null} for the mode this server hosts
     */
    public void open(Session session, @Nullable String ladderId) {
        if (session == null || session.player == null || session.data == null) return;
        session.clear();

        String owner = session.data.uuid;
        var controller = controller(session);
        var query = controller.query(ladderId);
        long requested = session.nextUiVersion();
        async.supply(() -> controller.fetch(query, owner)).thenMain((data, error) -> {
            if (session.uiVersion() != requested) {
                return; // The player opened something else while this was loading.
            }
            if (error != null) {
                Log.err("Failed to load the matches of " + owner, error);
                if (session.locale() != null) {
                    session.locale().send("match-history-load-failed");
                }
                return;
            }
            menuService.openUi(session, controller, controller.model(data), true);
        });
    }

    MatchHistoryUiController controller(Session session) {
        return new MatchHistoryUiController(this, async, session, clock);
    }

    /** Shows {@code model} again as it was, for the way back from a menu opened out of it. */
    void reopen(Session session, MatchHistoryUiController controller, MatchHistoryUiController.Model model) {
        if (session == null || session.player == null) return;
        session.clear();
        menuService.openUi(session, controller, model, true);
    }

    /** Opens the profile of a participant; its back button runs {@code back}. */
    void inspect(Session session, String uuid, Runnable back) {
        PlayerMenu profiles = playerMenu.get();
        if (profiles == null || session.player == null) return;
        Session online = sessionService.get(uuid);
        if (online != null && online.data != null) {
            session.pushHistory(back);
            profiles.openProfileUi(session, online.data);
            return;
        }
        async.onMainForPlayer(session.player, sessionService.getOrLoadFromDbAsync(uuid), (player, loaded) -> {
            PlayerData target = loaded;
            if (target == null) {
                if (session.locale() != null) {
                    session.locale().send("error-player-not-found");
                }
                return;
            }
            session.pushHistory(back);
            profiles.openProfileUi(session, target);
        });
    }

    /** The ladders as tabs: those of the modes this server hosts first, the rest by name. */
    List<MatchHistoryUiController.LadderTab> tabs(Localization local) {
        List<Ladder> hosted = new ArrayList<>();
        for (LadderDefinition followed : announcer.followed()) {
            ladders.find(followed.id()).ifPresent(hosted::add);
        }
        List<Ladder> rest = new ArrayList<>(ladders.all());
        rest.removeAll(hosted);
        rest.sort(Comparator.comparing(Ladder::id));
        List<MatchHistoryUiController.LadderTab> tabs = new ArrayList<>();
        for (Ladder ladder : hosted) {
            tabs.add(tab(ladder, local));
        }
        for (Ladder ladder : rest) {
            tabs.add(tab(ladder, local));
        }
        return tabs;
    }

    private MatchHistoryUiController.LadderTab tab(Ladder ladder, Localization local) {
        return new MatchHistoryUiController.LadderTab(ladder.id(), t(local, ladder.definition().displayNameKey()),
                presenters.of(ladder.id()).icon());
    }

    @Nullable
    Ladder ladder(String ladderId) {
        return ladders.find(ladderId).orElse(null);
    }

    MatchPresenter presenter(String ladderId) {
        return presenters.of(ladderId);
    }

    /** Blocking. */
    LadderProgress progress(Ladder ladder, String uuid) {
        return views.progress(ladder, uuid);
    }
}
