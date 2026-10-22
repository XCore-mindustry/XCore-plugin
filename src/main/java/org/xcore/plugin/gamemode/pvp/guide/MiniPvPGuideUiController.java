package org.xcore.plugin.gamemode.pvp.guide;

import com.ospx.flubundle.mindustry.ContentNames;
import mindustry.game.Team;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.ContentRef;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.Multiplier;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.Rating;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.TeamCount;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
import org.xcore.ui.Ui;
import org.xcore.ui.UiLayout;
import org.xcore.ui.VImage;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.xcore.plugin.ui.kit.Kit.BAND_MARGIN;
import static org.xcore.plugin.ui.kit.Kit.GAP;
import static org.xcore.plugin.ui.kit.Texts.t;

/**
 * The MiniPvP guide: the round as it stands, the rules this server plays by and how a round is
 * rated, on three tabs of short cards. Shown to newcomers when they join and to anyone through
 * {@code /pvp info}.
 */
public class MiniPvPGuideUiController
        implements UiController<MiniPvPGuideUiController.GuideModel, MiniPvPGuideUiController.GuideEvent> {

    private static final float ROW_ICON = 32f;
    private static final float TILE_ICON = 26f;
    /** A caption is a couple of short words; a tile narrower than this would break them apart. */
    private static final float TILE_MIN = 140f;

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

    public record GuideModel(Tab tab, MiniPvPModeFacts facts) {
        GuideModel withTab(Tab next) {
            return new GuideModel(next, facts);
        }
    }

    public sealed interface GuideEvent {
        record SelectTab(Tab tab) implements GuideEvent {}
        record Close() implements GuideEvent {}
    }

    private final Localization local;

    public MiniPvPGuideUiController(Localization local) {
        this.local = local;
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
    public VNode render(GuideModel model) {
        return Screen.each(screen -> window(model, screen));
    }

    /** The guide as one {@link Screen} sees it. */
    VNode window(GuideModel model, Screen screen) {
        MiniPvPModeFacts facts = model.facts();
        Tab current = model.tab();
        float width = screen.width();

        List<Kit.Tab> tabs = new ArrayList<>();
        for (Tab tab : Tab.values()) {
            tabs.add(new Kit.Tab(tab.glyph, t(local, tab.key()), tab.action(), tab.accent, tab == current));
        }

        return Kit.window(window -> {
            window.add(Kit.header(width, "[accent]" + Iconc.modePvp + " MINIPVP[]  [white]"
                    + t(local, "pvp-guide-title") + "[]")).row();
            window.add(Kit.tabs(width, "tabs", tabs)).row();
            window.add(Kit.line(width, current.accent)).row();

            Kit.body(window, screen, body -> {
                List<Stat> stats = new ArrayList<>();
                List<VNode> cards = new ArrayList<>();
                switch (current) {
                    case ROUND -> round(screen, facts, stats, cards);
                    case RULES -> rules(screen, facts, stats, cards);
                    case RATING -> rating(screen, facts.rating(), stats, cards);
                }
                // The introduction scrolls away with the cards instead of taking height from them.
                if (current == Tab.ROUND) {
                    body.add(Ui.table(intro -> {
                        intro.layout(l -> l.padBottom(GAP));
                        intro.add(Kit.text("[lightgray]" + t(local, "pvp-guide-subtitle") + "[]", screen.cards()));
                    })).row();
                }
                tiles(body, screen, current, stats);
                body.add(Kit.columns(screen, cards)).row();
                body.add(Kit.centered("[gray]" + Iconc.info + "[] [lightgray]" + t(local, "pvp-guide-hint") + "[]",
                        screen.cards())).row();
            });
        });
    }

    // ------------------------------------------------------------------ tabs

    private void round(Screen screen, MiniPvPModeFacts facts, List<Stat> stats, List<VNode> cards) {
        float width = screen.card();
        if (facts.map() != null) {
            int players = facts.teams().stream().mapToInt(TeamCount::players).sum();
            stats.add(new Stat(String.valueOf(facts.roundMinutes()), "pvp-guide-stat-minutes"));
            stats.add(new Stat(String.valueOf(facts.teams().size()), "pvp-guide-stat-teams"));
            stats.add(new Stat(String.valueOf(players), "pvp-guide-stat-players"));

            cards.add(Kit.card(width, Tab.ROUND.accent, t(local, "pvp-guide-now-title"), (content, inner) -> {
                content.add(Kit.text(t(local, "pvp-guide-now-map", Map.of("map", facts.map())), inner)).row();
                if (!facts.teams().isEmpty()) {
                    content.add(Kit.text(teams(facts.teams()), inner)).row();
                }
            }));
        }
        cards.add(card(width, Tab.ROUND, "pvp-guide-win", Map.of()));
        cards.add(card(width, Tab.ROUND, "pvp-guide-out", Map.of()));
        StringJoiner commands = new StringJoiner("\n");
        for (String[] command : COMMANDS) {
            commands.add("[accent]" + command[0] + "[]  [lightgray]" + t(local, command[1]) + "[]");
        }
        cards.add(Kit.card(width, Tab.ROUND.accent, t(local, "pvp-guide-commands-title"),
                (content, inner) -> content.add(Kit.text(commands.toString(), inner)).row()));
    }

    private void rules(Screen screen, MiniPvPModeFacts facts, List<Stat> stats, List<VNode> cards) {
        float width = screen.card();
        if (facts.polygonCoreProtection()) {
            cards.add(card(width, Tab.RULES, "pvp-guide-zone-polygon", Map.of()));
        } else if (facts.coreBuildRadius() > 0) {
            stats.add(new Stat(String.valueOf(facts.coreBuildRadius()), "pvp-guide-stat-zone"));
            cards.add(card(width, Tab.RULES, "pvp-guide-zone", Map.of("tiles", facts.coreBuildRadius())));
        }
        if (facts.unitCapChanged()) {
            stats.add(new Stat(String.valueOf(facts.unitCap()), "pvp-guide-stat-unitcap"));
            cards.add(card(width, Tab.RULES, facts.unitCapVariable() ? "pvp-guide-unitcap" : "pvp-guide-unitcap-fixed",
                    Map.of("cap", facts.unitCap())));
        }
        if (!facts.multipliers().isEmpty()) {
            StringJoiner lines = new StringJoiner("\n");
            for (Multiplier multiplier : facts.multipliers()) {
                lines.add(t(local, "pvp-guide-rule-" + multiplier.key()) + ": [accent]" + multiplier.percent() + "%[]");
            }
            cards.add(Kit.card(width, Tab.RULES.accent, t(local, "pvp-guide-multipliers-title"), (content, inner) -> {
                content.add(Kit.text(t(local, "pvp-guide-multipliers-text"), inner)).row();
                content.add(Kit.text(lines.toString(), inner)).row();
            }));
        }
        if (!facts.bannedBlocks().isEmpty()) {
            stats.add(new Stat(String.valueOf(facts.bannedBlocks().size()), "pvp-guide-stat-banned"));
            cards.add(Kit.card(width, Tab.RULES.accent, t(local, "pvp-guide-banned-blocks-title"),
                    (content, inner) -> icons(content, facts.bannedBlocks(), inner)));
        }
        if (!facts.bannedUnits().isEmpty()) {
            cards.add(Kit.card(width, Tab.RULES.accent, t(local, "pvp-guide-banned-units-title"),
                    (content, inner) -> icons(content, facts.bannedUnits(), inner)));
        }
        List<String> other = new ArrayList<>();
        if (facts.fog()) other.add(t(local, "pvp-guide-other-fog"));
        if (facts.coreCapture()) other.add(t(local, "pvp-guide-other-capture"));
        if (!facts.reactorExplosions()) other.add(t(local, "pvp-guide-other-reactors"));
        if (!other.isEmpty()) {
            cards.add(Kit.card(width, Tab.RULES.accent, t(local, "pvp-guide-other-title"),
                    (content, inner) -> content.add(Kit.text(String.join("\n", other), inner)).row()));
        }
        if (cards.isEmpty()) {
            cards.add(card(width, Tab.RULES, "pvp-guide-vanilla", Map.of()));
        }
    }

    private void rating(Screen screen, Rating rating, List<Stat> stats, List<VNode> cards) {
        float width = screen.card();
        // As text: a number would be grouped into "1 000" by the locale.
        String start = String.valueOf(rating.startingRating());
        stats.add(new Stat(start, "pvp-guide-stat-start"));
        stats.add(new Stat("/top", "pvp-guide-stat-top"));
        stats.add(new Stat("/matches", "pvp-guide-stat-matches"));

        cards.add(card(width, Tab.RATING, "pvp-guide-elo", Map.of("start", start)));
        cards.add(card(width, Tab.RATING, "pvp-guide-leave", Map.of("seconds", rating.leftAtOnceSeconds())));
        cards.add(card(width, Tab.RATING, "pvp-guide-unrated", Map.of(
                "seconds", rating.minimumSeconds(),
                "players", rating.minimumPlayers(),
                "winner", rating.winnerPercent(),
                "late", rating.lateJoinPercent())));
        cards.add(card(width, Tab.RATING, "pvp-guide-seasons", Map.of()));
    }

    // ------------------------------------------------------------------ pieces

    /** A card whose title and text are the bundle keys {@code <key>-title} and {@code <key>-text}. */
    private VNode card(float width, Tab tab, String key, Map<String, Object> args) {
        String text = t(local, key + "-text", args);
        return Kit.card(width, tab.accent, t(local, key + "-title", args),
                (content, inner) -> content.add(Kit.text(text, inner)).row());
    }

    /** The teams in the round, each in its own colour, with the players on it. */
    private String teams(List<TeamCount> teams) {
        Locale locale = local != null ? local.getLocale() : Locale.ENGLISH;
        return teams.stream()
                .map(count -> {
                    Team team = Team.get(count.teamId());
                    return "[#" + team.color + "]" + ContentNames.vanilla().name(team, locale) + "[]  [lightgray]"
                            + t(local, "pvp-guide-now-players", Map.of("count", count.players())) + "[]";
                })
                .collect(Collectors.joining("\n"));
    }

    /** The key numbers of a tab as a strip of tiles above its cards. */
    private void tiles(Ui.TableBuilder body, Screen screen, Tab tab, List<Stat> stats) {
        if (stats.isEmpty()) return;
        // A caption wraps between words only, so its longest word sets how narrow a tile may be.
        float widest = TILE_MIN - 2f * BAND_MARGIN;
        for (Stat stat : stats) {
            widest = Math.max(widest, TextWidth.of(stat.value()));
            for (String word : t(local, stat.caption()).split("\\s+")) {
                widest = Math.max(widest, TextWidth.of(word));
            }
        }
        float tileMin = widest + 2f * BAND_MARGIN;
        int perRow = Math.clamp((int) ((screen.cards() + GAP) / (tileMin + GAP)), 1, 4);
        for (int from = 0; from < stats.size(); from += perRow) {
            List<Stat> rowStats = stats.subList(from, Math.min(stats.size(), from + perRow));
            float tileWidth = (screen.cards() - (rowStats.size() - 1) * GAP) / rowStats.size();
            body.add(Ui.table(row -> {
                row.layout(l -> l.padBottom(GAP));
                for (int i = 0; i < rowStats.size(); i++) {
                    boolean last = i == rowStats.size() - 1;
                    row.add(tile(tab, rowStats.get(i), tileWidth, last ? 0f : GAP));
                }
            })).row();
        }
    }

    private VNode tile(Tab tab, Stat stat, float width, float padRight) {
        float inner = width - 2f * BAND_MARGIN;
        return Ui.table(tile -> {
            tile.background("whiteui");
            tile.margin(BAND_MARGIN);
            tile.layout(l -> l.width(width).fillY().padRight(padRight).color(tab.accent.band()));
            tile.add(Kit.centered("[#" + tab.accent.color() + "]" + stat.value() + "[]", inner)).row();
            tile.add(Kit.centered("[lightgray]" + t(local, stat.caption()) + "[]", inner)).row();
        });
    }

    /** Content icons in rows as long as {@code width} allows, then their names in the viewer's language. */
    private void icons(Ui.TableBuilder content, List<ContentRef> refs, float width) {
        int perRow = Math.max(1, (int) (width / (ROW_ICON + 4f)));
        for (int from = 0; from < refs.size(); from += perRow) {
            List<ContentRef> rowRefs = refs.subList(from, Math.min(refs.size(), from + perRow));
            content.add(Ui.table(row -> {
                row.layout(l -> l.expandX().align("left").padTop(4f));
                for (ContentRef ref : rowRefs) {
                    row.add(icon(ref.region(), ROW_ICON, l -> l.padRight(4f)));
                }
            })).row();
        }
        Locale locale = local != null ? local.getLocale() : Locale.ENGLISH;
        String names = refs.stream().map(ref -> ref.name(locale)).collect(Collectors.joining(", "));
        content.add(Ui.table(row -> {
            row.layout(l -> l.padTop(2f));
            row.add(Kit.text("[lightgray]" + names + "[]", width));
        })).row();
    }

    /** A square icon that keeps the proportions of its sprite; units are rarely square. */
    private static VNode icon(String region, float size, Consumer<UiLayout.Builder> layout) {
        return Ui.image(region, l -> {
            l.scaling(VImage.SCALING_FIT).width(size).height(size);
            layout.accept(l);
        });
    }
}
