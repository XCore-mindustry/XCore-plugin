package org.xcore.plugin.ui.menu;

import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.view.LadderViews;
import org.xcore.plugin.rating.view.SeasonCard;
import org.xcore.plugin.rating.view.SeasonOverview;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.xcore.plugin.ui.kit.Kit.GAP;
import static org.xcore.plugin.ui.kit.Texts.locale;
import static org.xcore.plugin.ui.kit.Texts.t;

/**
 * The {@code /season} dialog: one card per rating ladder with its current season, the time it
 * has left, the viewer's own standing and the winners of the season before, laid out once per
 * {@link Screen}.
 */
public class SeasonUiController implements UiController<SeasonUiController.SeasonModel, SeasonUiController.SeasonEvent> {

    private final SeasonMenu seasonMenu;
    private final LadderViews views;
    private final Session session;

    public SeasonUiController(SeasonMenu seasonMenu, LadderViews views, Session session) {
        this.seasonMenu = seasonMenu;
        this.views = views;
        this.session = session;
    }

    /**
     * @param overview      the ladder's season as read from storage
     * @param topCategoryId the {@code /top} category showing this ladder, {@code null} when it has none
     */
    public record LadderEntry(SeasonOverview overview, @Nullable String topCategoryId) {}

    public record SeasonModel(List<LadderEntry> ladders) {
        public SeasonModel {
            ladders = ladders == null ? List.of() : List.copyOf(ladders);
        }
    }

    public sealed interface SeasonEvent {
        record OpenTop(String categoryId) implements SeasonEvent {}
        record Close() implements SeasonEvent {}
    }

    @Override
    public SeasonModel initialModel(Object context) {
        return context instanceof SeasonModel model ? model : new SeasonModel(List.of());
    }

    @Override
    public UpdateResult<SeasonModel> update(SeasonModel model, SeasonEvent event, ControllerContext context) {
        return switch (event) {
            case SeasonEvent.OpenTop(String categoryId) -> {
                if (seasonMenu != null && session != null) {
                    session.pushHistory(() -> seasonMenu.open(session));
                    seasonMenu.openTop(session, categoryId);
                }
                yield UpdateResult.of(model);
            }
            case SeasonEvent.Close() -> UpdateResult.close(model);
        };
    }

    @Override
    public SeasonEvent parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled() || result.result == null) {
            return new SeasonEvent.Close();
        }
        String res = result.result.trim();
        if (res.startsWith("action:top:")) {
            return new SeasonEvent.OpenTop(res.substring("action:top:".length()));
        }
        return new SeasonEvent.Close();
    }

    @Override
    public VNode render(SeasonModel model) {
        return Screen.each(screen -> window(model, screen));
    }

    /** The window as one {@link Screen} sees it. */
    VNode window(SeasonModel model, Screen screen) {
        float width = screen.width();
        Localization local = locale(session);

        return Kit.window(window -> {
            window.add(Kit.header(width, "[gold]" + Iconc.star + "[] [white]"
                    + t(local, "season-menu-title") + "[]")).row();
            window.add(Kit.line(width, Accent.GOLD)).row();

            Kit.body(window, screen, body -> {
                if (model.ladders().isEmpty() || local == null) {
                    body.add(Kit.note(screen.cards(), t(local, "season-menu-empty"))).row();
                    return;
                }
                List<VNode> cards = new ArrayList<>();
                for (int i = 0; i < model.ladders().size(); i++) {
                    cards.add(ladderCard(model.ladders().get(i), Accent.at(i), screen.card(), local));
                }
                body.add(Kit.columns(screen, cards)).row();
            });
        });
    }

    /** A ladder's season: where it stands and where the viewer does, its prizes, the winners before, and the way to its leaderboard. */
    private VNode ladderCard(LadderEntry entry, Accent accent, float width, Localization local) {
        SeasonCard text = views.card(entry.overview(), local);

        return Kit.card(width, accent, Iconc.star + " " + text.title(), (content, inner) -> {
            content.add(Kit.text(lines(text.lines()), inner)).row();
            if (!text.prizes().isEmpty()) {
                content.add(section(text.prizeTitle(), text.prizes(), inner)).row();
            }
            if (!text.podium().isEmpty()) {
                content.add(section(text.podiumTitle(), text.podium(), inner)).row();
            }
            if (entry.topCategoryId() != null) {
                content.add(Ui.table(open -> {
                    open.layout(l -> l.padTop(GAP));
                    open.add(Kit.button(new Kit.Action("[accent]" + Iconc.list + "[] " + t(local, "season-menu-open-top"),
                            "action:top:" + entry.topCategoryId()), inner));
                })).row();
            }
        });
    }

    /** A heading and what is under it, set off from the lines above. */
    private static VNode section(String title, List<String> lines, float width) {
        return Ui.table(section -> {
            section.layout(l -> l.padTop(GAP));
            section.add(Kit.text(title + "\n" + lines(lines), width));
        });
    }

    private static String lines(List<String> lines) {
        return lines.stream().filter(line -> !line.isBlank()).collect(Collectors.joining("\n"));
    }
}
