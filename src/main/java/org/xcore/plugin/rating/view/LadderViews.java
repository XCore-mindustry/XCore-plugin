package org.xcore.plugin.rating.view;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.prize.PrizeGrant;
import org.xcore.plugin.rating.prize.PrizeGrantRepository;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.rating.season.SeasonResolver;
import org.xcore.plugin.rating.season.SeasonSchedule;
import org.xcore.plugin.rating.season.SeasonStore;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

import static com.ospx.flubundle.Bundle.args;

/**
 * Everything players are shown about a ladder and its seasons: the {@code /top} category,
 * the profile section and the {@code /season} overview. A mode builds these for its ladder
 * and registers them where it wants them.
 */
@Singleton
public class LadderViews {
    /** Past seasons listed in a profile; the full archive is in {@code /top}. */
    static final int HISTORY_SEASONS = 3;
    /** Winners of the previous season named in {@code /season}; the full table is in {@code /top}. */
    static final int PODIUM_PLACES = 3;
    /** Prizes listed for one player; older ones are in the Discord archive. */
    static final int PRIZES_SHOWN = 4;
    private static final String DETAIL_SEPARATOR = "  [darkgray]|[]  ";

    private final SeasonStore seasons;
    private final SeasonResolver resolver;
    private final SeasonSchedule schedule;
    @Nullable
    private final PlayerDataRepository players;
    private final PrizeGrantRepository grants;
    private final SeasonText text;
    private final LadderProgressText progressText;

    @Inject
    public LadderViews(SeasonStore seasons, SeasonResolver resolver, SeasonSchedule schedule,
                       PlayerDataRepository players, PrizeGrantRepository grants) {
        this(seasons, resolver, schedule, players, grants, Clock.systemUTC());
    }

    public LadderViews(SeasonStore seasons, SeasonResolver resolver, SeasonSchedule schedule,
                       @Nullable PlayerDataRepository players, PrizeGrantRepository grants, Clock clock) {
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.players = players;
        this.grants = Objects.requireNonNull(grants, "grants");
        this.text = new SeasonText(schedule.zone(), clock);
        this.progressText = new LadderProgressText(text);
    }

    public SeasonText text() {
        return text;
    }

    public SeasonSchedule schedule() {
        return schedule;
    }

    /** The ladder's {@code /top} category, one scope per season. */
    public LadderTopCategoryProvider topCategory(String id, int priority, Ladder ladder) {
        return new LadderTopCategoryProvider(id, priority, ladder, seasons, text, players);
    }

    /**
     * The ladder's block in the player profile.
     *
     * @param icon         glyph shown before the ladder name
     * @param showUnplayed whether players who never played on the ladder get the block too
     */
    public LadderProfileSection profileSection(Ladder ladder, String icon, int priority, boolean showUnplayed) {
        return new LadderProfileSection(this, ladder, icon, priority, showUnplayed);
    }

    /** Blocking. */
    public LadderProgress progress(Ladder ladder, String uuid) {
        LadderStanding standing = ladder.fetchStanding(uuid);
        OptionalLong rank = standing.placed() ? ladder.rankOf(standing.season(), uuid) : OptionalLong.empty();
        return new LadderProgress(
                ladder.definition(),
                resolver.current(ladder.id()).orElse(null),
                standing,
                rank.isPresent() ? rank.getAsLong() : null,
                ladder.history(uuid, HISTORY_SEASONS),
                prizesOf(ladder, uuid));
    }

    /** The player's most recent prizes on the ladder. Prizes are a courtesy: a failed read shows none. */
    private List<PrizeGrant> prizesOf(Ladder ladder, String uuid) {
        try {
            String prefix = ladder.id() + ":";
            return grants.findByPlayer(uuid).stream()
                    .filter(grant -> grant.seasonId().startsWith(prefix))
                    .sorted(Comparator.comparingInt(PrizeGrant::seasonNumber).reversed()
                            .thenComparingInt(PrizeGrant::prizeIndex))
                    .limit(PRIZES_SHOWN)
                    .toList();
        } catch (RuntimeException e) {
            Log.err("Failed to read the prizes of " + uuid + " on ladder " + ladder.id(), e);
            return List.of();
        }
    }

    /** Blocking. The ladder's season as {@code /season} shows it to the player {@code uuid}. */
    public SeasonOverview overview(Ladder ladder, String uuid) {
        LadderProgress progress = progress(ladder, uuid);
        int season = progress.standing().season();
        Season previous = season > Ladder.FIRST_SEASON ? seasons.find(ladder.id(), season - 1).orElse(null) : null;
        return new SeasonOverview(progress, ladder.count(season), previous);
    }

    /** Words {@code overview} for its viewer. Touches no storage. */
    public SeasonCard card(SeasonOverview overview, Localization local) {
        LadderProgress progress = overview.progress();
        Season season = progress.season();
        String ladderName = local.t(progress.ladder().displayNameKey());

        List<String> lines = new ArrayList<>();
        if (season != null) {
            lines.add(season.active()
                    ? local.t("season-menu-ends", args(
                            "remaining", text.remaining(season, local), "date", text.date(season.endsAt())))
                    : local.t("season-menu-closing"));
        }
        lines.add(local.t("season-menu-participants", args("count", overview.participants())));
        lines.add(local.t("season-menu-you", args("standing", progressText.standing(progress, local))));
        lines.add(String.join(DETAIL_SEPARATOR, progressText.details(progress, local)));
        lines.add(progressText.leagueProgress(progress, local));
        lines.addAll(progressText.prizes(progress, local));

        Season previous = overview.previous();
        List<String> podium = new ArrayList<>();
        if (previous != null) {
            previous.podium().stream().limit(PODIUM_PLACES).forEach(entry -> {
                RatingLeague league = RatingLeague.fromRating(entry.rating());
                String line = local.t("season-menu-podium-entry", args(
                        "place", entry.place(),
                        "name", entry.nickname(),
                        "league", league.icon() + " " + local.t(league.localizationKey()),
                        "rating", entry.rating()));
                List<SeasonPrize> won = previous.prizesFor(entry.place());
                podium.add(won.isEmpty() ? line
                        : line + local.t("season-menu-podium-prizes", args("prizes", PrizeText.labels(won, local))));
            });
        }
        List<String> prizes = season == null ? List.of()
                : season.prizes().stream().map(prize -> PrizeText.entry(prize, local)).toList();
        return new SeasonCard(
                season != null
                        ? local.t("season-menu-card-title", args("ladder", ladderName, "season", text.title(season, local)))
                        : ladderName,
                lines,
                podium.isEmpty() ? "" : local.t("season-menu-previous", args("season", text.title(previous, local))),
                podium,
                prizes.isEmpty() ? "" : local.t("season-menu-prizes"),
                prizes);
    }
}
