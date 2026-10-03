package org.xcore.plugin.ui.menu;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.season.SeasonAnnouncer;
import org.xcore.plugin.rating.view.LadderTopCategoryProvider;
import org.xcore.plugin.rating.view.LadderViews;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;

import java.util.ArrayList;
import java.util.List;

/** Entry point of the {@code /season} dialog. */
@Singleton
public class SeasonMenu {

    private final LadderService ladders;
    private final SeasonAnnouncer announcer;
    private final LadderViews views;
    private final TopCategoryRegistry topCategories;
    private final TopMenu topMenu;
    private final MenuService menuService;
    private final SessionService sessionService;
    private final Async async;

    @Inject
    public SeasonMenu(LadderService ladders,
                      SeasonAnnouncer announcer,
                      LadderViews views,
                      TopCategoryRegistry topCategories,
                      TopMenu topMenu,
                      MenuService menuService,
                      SessionService sessionService,
                      Async async) {
        this.ladders = ladders;
        this.announcer = announcer;
        this.views = views;
        this.topCategories = topCategories;
        this.topMenu = topMenu;
        this.menuService = menuService;
        this.sessionService = sessionService;
        this.async = async;
    }

    public void season(String uuid) {
        open(sessionService.get(uuid));
    }

    /** Opens the dialog once the seasons have been read off the game thread. */
    public void open(Session session) {
        if (session == null || session.player == null || session.data == null) return;
        session.clear();

        String viewer = session.data.uuid;
        var controller = new SeasonUiController(this, views, session);
        async.supply(() -> load(viewer)).thenMain((model, error) -> {
            if (error != null) {
                Log.err("Failed to load seasons for " + viewer, error);
                return;
            }
            menuService.openUi(session, controller, model);
        });
    }

    public void openTop(Session session, String categoryId) {
        topMenu.openTopUi(session, categoryId);
    }

    /**
     * Blocking. The ladders of the modes this server hosts, or every ladder of the network on
     * a server that hosts no rated mode.
     */
    SeasonUiController.SeasonModel load(String viewerUuid) {
        List<SeasonUiController.LadderEntry> entries = new ArrayList<>();
        for (Ladder ladder : shown()) {
            try {
                entries.add(new SeasonUiController.LadderEntry(
                        views.overview(ladder, viewerUuid), topCategoryOf(ladder.id())));
            } catch (Exception e) {
                // One ladder failing to load must not hide the others.
                Log.err("Failed to load the season of ladder " + ladder.id(), e);
            }
        }
        return new SeasonUiController.SeasonModel(entries);
    }

    private List<Ladder> shown() {
        List<Ladder> hosted = new ArrayList<>();
        for (LadderDefinition followed : announcer.followed()) {
            ladders.find(followed.id()).ifPresent(hosted::add);
        }
        return hosted.isEmpty() ? ladders.all() : hosted;
    }

    private String topCategoryOf(String ladderId) {
        for (TopCategoryProvider provider : topCategories.all()) {
            if (provider instanceof LadderTopCategoryProvider category && category.ladderId().equals(ladderId)) {
                return category.id();
            }
        }
        return null;
    }
}
