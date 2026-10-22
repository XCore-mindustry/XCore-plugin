package org.xcore.plugin.gamemode.pvp.guide;

import com.ospx.flubundle.mindustry.ContentNames;
import mindustry.game.Team;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.ContentRef;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.Multiplier;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.Rating;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.TeamCount;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.RenderContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static org.xcore.plugin.ui.kit.Kit.GAP;

/**
 * The MiniPvP guide: the round as it stands, the rules this server plays by and how a round is
 * rated, on three tabs of short cards. Shown to newcomers when they join and to anyone through
 * {@code /pvp info}.
 */
public class MiniPvPGuideUiController
        implements UiController<MiniPvPGuideUiController.GuideModel, MiniPvPGuideUiController.GuideEvent> {

    /** The commands a MiniPvP player needs and the bundle key of what each one does. */
    private static final String[][] COMMANDS = {
            {"/pvp info", "pvp-guide-cmd-info"},
            {"/top", "pvp-guide-cmd-top"},
            {"/season", "pvp-guide-cmd-season"},
            {"/matches", "pvp-guide-cmd-matches"},
            {"/rtv", "pvp-guide-cmd-rtv"},
            {"/observer", "pvp-guide-cmd-observer"},
    };

    /** A tab of the guide; each has its own colour so the cards of a topic read as one group. */
    public enum Tab {
        ROUND(Accent.GOLD, Iconc.modePvp),
        RULES(Accent.BLUE, Iconc.bookOpen),
        RATING(Accent.PURPLE, Iconc.star);

        private final Accent accent;
        private final char glyph;

        Tab(Accent accent, char glyph) {
            this.accent = accent;
            this.glyph = glyph;
        }

        String key() {
            return "pvp-guide-tab-" + name().toLowerCase(Locale.ROOT);
        }

        String action() {
            return "action:tab:" + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A number worth seeing before reading anything: its value over a caption. */
    private record Stat(String value, String caption) {
    }

    private static List<Kit.Tile> tiles(RenderContext ctx, List<Stat> stats) {
        return stats.stream().map(stat -> Kit.Tile.number(stat.value(), ctx.t(stat.caption()))).toList();
    }

    /** Content icons in rows as long as {@code width} allows, then their names in the viewer's language. */
    private static void icons(RenderContext ctx, Ui.TableBuilder content, List<ContentRef> refs, float width) {
        Locale locale = ctx.locale();
        Kit.icons(content, refs.stream().map(ContentRef::region).toList(),
                refs.stream().map(ref -> ref.name(locale)).collect(Collectors.joining(", ")), width);
    }

    public record GuideModel(Tab tab, MiniPvPModeFacts facts) {
        GuideModel withTab(Tab next) {
            return new GuideModel(next, facts);
        }
    }

    public sealed interface GuideEvent {
        record SelectTab(Tab tab) implements GuideEvent {}
        record Close() implements GuideEvent {}
    }

    @Override
    public UpdateResult<GuideModel> update(GuideModel model, GuideEvent event, ControllerContext context) {
        return switch (event) {
            case GuideEvent.SelectTab(Tab tab) ->
                    tab == model.tab() ? UpdateResult.of(model) : UpdateResult.rerender(model.withTab(tab));
            case GuideEvent.Close() -> UpdateResult.close(model);
        };
    }

    @Override
    public GuideEvent parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled()) {
            return null;
        }
        String action = result.result.trim();
        if ("action:close".equals(action)) {
            return new GuideEvent.Close();
        }
        for (Tab tab : Tab.values()) {
            if (tab.action().equals(action)) {
                return new GuideEvent.SelectTab(tab);
            }
        }
        return null;
    }

    @Override
    public VNode render(GuideModel model, RenderContext ctx) {
        return Screen.each(screen -> window(model, screen, ctx));
    }

    /** The guide as one {@link Screen} sees it. */
    VNode window(GuideModel model, Screen screen, RenderContext ctx) {
        MiniPvPModeFacts facts = model.facts();
        Tab current = model.tab();
        float width = screen.width();

        List<Kit.Tab> tabs = new ArrayList<>();
        for (Tab tab : Tab.values()) {
            tabs.add(new Kit.Tab(tab.glyph, ctx.t(tab.key()), tab.action(), tab.accent, tab == current));
        }

        return Kit.window(window -> {
            window.add(Kit.header(width, "[accent]" + Iconc.modePvp + " MINIPVP[]  [white]"
                    + ctx.t("pvp-guide-title") + "[]")).row();
            window.add(Kit.tabs(width, "tabs", tabs)).row();
            window.add(Kit.line(width, current.accent)).row();

            Kit.body(window, screen, body -> {
                List<Stat> stats = new ArrayList<>();
                List<VNode> cards = new ArrayList<>();
                switch (current) {
                    case ROUND -> round(screen, facts, stats, cards, ctx);
                    case RULES -> rules(screen, facts, stats, cards, ctx);
                    case RATING -> rating(screen, facts.rating(), stats, cards, ctx);
                }
                // The introduction scrolls away with the cards instead of taking height from them.
                if (current == Tab.ROUND) {
                    body.add(Ui.table(intro -> {
                        intro.layout(l -> l.padBottom(GAP));
                        intro.add(Kit.text("[lightgray]" + ctx.t("pvp-guide-subtitle") + "[]", screen.cards()));
                    })).row();
                }
                Kit.tiles(body, screen, current.accent, tiles(ctx, stats));
                body.add(Kit.columns(screen, cards)).row();
                body.add(Kit.centered("[gray]" + Iconc.info + "[] [lightgray]" + ctx.t("pvp-guide-hint") + "[]",
                        screen.cards())).row();
            });
        });
    }

    // ------------------------------------------------------------------ tabs

    private void round(Screen screen, MiniPvPModeFacts facts, List<Stat> stats, List<VNode> cards, RenderContext ctx) {
        float width = screen.card();
        if (facts.map() != null) {
            int players = facts.teams().stream().mapToInt(TeamCount::players).sum();
            stats.add(new Stat(String.valueOf(facts.roundMinutes()), "pvp-guide-stat-minutes"));
            stats.add(new Stat(String.valueOf(facts.teams().size()), "pvp-guide-stat-teams"));
            stats.add(new Stat(String.valueOf(players), "pvp-guide-stat-players"));

            cards.add(Kit.card(width, Tab.ROUND.accent, ctx.t("pvp-guide-now-title"), (content, inner) -> {
                content.add(Kit.text(ctx.t("pvp-guide-now-map", Map.of("map", facts.map())), inner)).row();
                if (!facts.teams().isEmpty()) {
                    content.add(Kit.text(teams(ctx, facts.teams()), inner)).row();
                }
            }));
        }
        cards.add(card(ctx, width, Tab.ROUND, "pvp-guide-win", Map.of()));
        cards.add(card(ctx, width, Tab.ROUND, "pvp-guide-out", Map.of()));
        StringJoiner commands = new StringJoiner("\n");
        for (String[] command : COMMANDS) {
            commands.add("[accent]" + command[0] + "[]  [lightgray]" + ctx.t(command[1]) + "[]");
        }
        cards.add(Kit.card(width, Tab.ROUND.accent, ctx.t("pvp-guide-commands-title"),
                (content, inner) -> content.add(Kit.text(commands.toString(), inner)).row()));
    }

    private void rules(Screen screen, MiniPvPModeFacts facts, List<Stat> stats, List<VNode> cards, RenderContext ctx) {
        float width = screen.card();
        if (facts.polygonCoreProtection()) {
            cards.add(card(ctx, width, Tab.RULES, "pvp-guide-zone-polygon", Map.of()));
        } else if (facts.coreBuildRadius() > 0) {
            stats.add(new Stat(String.valueOf(facts.coreBuildRadius()), "pvp-guide-stat-zone"));
            cards.add(card(ctx, width, Tab.RULES, "pvp-guide-zone", Map.of("tiles", facts.coreBuildRadius())));
        }
        if (facts.unitCapChanged()) {
            stats.add(new Stat(String.valueOf(facts.unitCap()), "pvp-guide-stat-unitcap"));
            cards.add(card(ctx, width, Tab.RULES, facts.unitCapVariable() ? "pvp-guide-unitcap" : "pvp-guide-unitcap-fixed",
                    Map.of("cap", facts.unitCap())));
        }
        if (!facts.multipliers().isEmpty()) {
            StringJoiner lines = new StringJoiner("\n");
            for (Multiplier multiplier : facts.multipliers()) {
                lines.add(ctx.t("pvp-guide-rule-" + multiplier.key()) + ": [accent]" + multiplier.percent() + "%[]");
            }
            cards.add(Kit.card(width, Tab.RULES.accent, ctx.t("pvp-guide-multipliers-title"), (content, inner) -> {
                content.add(Kit.text(ctx.t("pvp-guide-multipliers-text"), inner)).row();
                content.add(Kit.text(lines.toString(), inner)).row();
            }));
        }
        if (!facts.bannedBlocks().isEmpty()) {
            stats.add(new Stat(String.valueOf(facts.bannedBlocks().size()), "pvp-guide-stat-banned"));
            cards.add(Kit.card(width, Tab.RULES.accent, ctx.t("pvp-guide-banned-blocks-title"),
                    (content, inner) -> icons(ctx, content, facts.bannedBlocks(), inner)));
        }
        if (!facts.bannedUnits().isEmpty()) {
            cards.add(Kit.card(width, Tab.RULES.accent, ctx.t("pvp-guide-banned-units-title"),
                    (content, inner) -> icons(ctx, content, facts.bannedUnits(), inner)));
        }
        List<String> other = new ArrayList<>();
        if (facts.fog()) other.add(ctx.t("pvp-guide-other-fog"));
        if (facts.coreCapture()) other.add(ctx.t("pvp-guide-other-capture"));
        if (!facts.reactorExplosions()) other.add(ctx.t("pvp-guide-other-reactors"));
        if (!other.isEmpty()) {
            cards.add(Kit.card(width, Tab.RULES.accent, ctx.t("pvp-guide-other-title"),
                    (content, inner) -> content.add(Kit.text(String.join("\n", other), inner)).row()));
        }
        if (cards.isEmpty()) {
            cards.add(card(ctx, width, Tab.RULES, "pvp-guide-vanilla", Map.of()));
        }
    }

    private void rating(Screen screen, Rating rating, List<Stat> stats, List<VNode> cards, RenderContext ctx) {
        float width = screen.card();
        // As text: a number would be grouped into "1 000" by the locale.
        String start = String.valueOf(rating.startingRating());
        stats.add(new Stat(start, "pvp-guide-stat-start"));
        stats.add(new Stat("/top", "pvp-guide-stat-top"));
        stats.add(new Stat("/matches", "pvp-guide-stat-matches"));

        cards.add(card(ctx, width, Tab.RATING, "pvp-guide-elo", Map.of("start", start)));
        cards.add(card(ctx, width, Tab.RATING, "pvp-guide-leave", Map.of("seconds", rating.leftAtOnceSeconds())));
        cards.add(card(ctx, width, Tab.RATING, "pvp-guide-unrated", Map.of(
                "seconds", rating.minimumSeconds(),
                "players", rating.minimumPlayers(),
                "winner", rating.winnerPercent(),
                "late", rating.lateJoinPercent())));
        cards.add(card(ctx, width, Tab.RATING, "pvp-guide-seasons", Map.of()));
    }

    // ------------------------------------------------------------------ pieces

    /** A card whose title and text are the bundle keys {@code <key>-title} and {@code <key>-text}. */
    private VNode card(RenderContext ctx, float width, Tab tab, String key, Map<String, Object> args) {
        String text = ctx.t(key + "-text", args);
        return Kit.card(width, tab.accent, ctx.t(key + "-title", args),
                (content, inner) -> content.add(Kit.text(text, inner)).row());
    }

    /** The teams in the round, each in its own colour, with the players on it. */
    private String teams(RenderContext ctx, List<TeamCount> teams) {
        Locale locale = ctx.locale();
        return teams.stream()
                .map(count -> {
                    Team team = Team.get(count.teamId());
                    return "[#" + team.color + "]" + ContentNames.vanilla().name(team, locale) + "[]  [lightgray]"
                            + ctx.t("pvp-guide-now-players", Map.of("count", count.players())) + "[]";
                })
                .collect(Collectors.joining("\n"));
    }
}
