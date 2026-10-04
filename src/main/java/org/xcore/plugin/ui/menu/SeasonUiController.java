package org.xcore.plugin.ui.menu;

import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.view.LadderViews;
import org.xcore.plugin.rating.view.SeasonCard;
import org.xcore.plugin.rating.view.SeasonOverview;
import org.xcore.plugin.session.Session;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.responsive.DialogMetrics;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.List;

/**
 * The {@code /season} dialog: one card per rating ladder with its current season, the time it
 * has left, the viewer's own standing and the winners of the season before.
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
        DialogMetrics metrics = DialogMetrics.standard();
        Localization local = session != null ? session.locale() : null;

        return Ui.table(root -> {
            root.background("pane");
            root.margin(8f);
            root.layout(l -> l.growX().maxWidth(metrics.maxDialogWidth()).pad(4f));

            root.add(Ui.table(h -> {
                h.layout(l -> l.growX().padBottom(4f));
                h.label(Text.join(Text.raw("[gold]" + Iconc.star + "[] [white]"), Text.t("season-menu-title"), Text.raw("[]")),
                        l -> l.align("left").growX());
                h.button(Text.raw(" [scarlet]" + Iconc.cancel + "[] "), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.size(32f)));
            })).row();

            root.image("whiteui", l -> l.growX().height(2f).padBottom(4f).color("3b4252")).row();

            root.pane(pane -> {
                pane.layout(l -> l.growX().growY().maxHeight(metrics.maxBodyHeight()));
                pane.table(body -> {
                    body.layout(l -> l.growX());
                    if (model.ladders().isEmpty() || local == null) {
                        body.add(Ui.table(empty -> {
                            empty.background("button");
                            empty.margin(14f);
                            empty.layout(l -> l.growX());
                            empty.label(Text.join(Text.raw("[gray]" + Iconc.info + " "), Text.t("season-menu-empty"), Text.raw("[]")),
                                    l -> l.align("center").growX());
                        })).row();
                        return;
                    }
                    for (LadderEntry entry : model.ladders()) {
                        body.add(Ui.table(card -> renderCard(card, entry, local))).row();
                    }
                });
            }).row();
        });
    }

    private void renderCard(Ui.TableBuilder card, LadderEntry entry, Localization local) {
        SeasonCard text = views.card(entry.overview(), local);

        card.background("button");
        card.margin(10f);
        card.layout(l -> l.growX().padBottom(6f));

        card.label(Text.raw("[accent]" + text.title() + "[]"), l -> l.align("left").growX().padBottom(4f)).row();
        for (String line : text.lines()) {
            if (!line.isBlank()) {
                card.label(Text.raw(line), l -> l.align("left").growX().padBottom(2f)).row();
            }
        }
        if (!text.prizes().isEmpty()) {
            card.label(Text.raw(text.prizeTitle()), l -> l.align("left").growX().padTop(6f).padBottom(2f)).row();
            for (String line : text.prizes()) {
                card.label(Text.raw(line), l -> l.align("left").growX().padBottom(2f)).row();
            }
        }
        if (!text.podium().isEmpty()) {
            card.label(Text.raw(text.podiumTitle()), l -> l.align("left").growX().padTop(6f).padBottom(2f)).row();
            for (String line : text.podium()) {
                card.label(Text.raw(line), l -> l.align("left").growX().padBottom(2f)).row();
            }
        }
        if (entry.topCategoryId() != null) {
            card.button(Text.join(Text.raw("[accent]" + Iconc.list + "[] "), Text.t("season-menu-open-top")),
                    "action:top:" + entry.topCategoryId(), b -> b
                            .style("cleart")
                            .layout(l -> l.growX().height(36f).padTop(6f)));
        }
    }
}
