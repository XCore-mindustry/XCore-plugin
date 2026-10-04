package org.xcore.plugin.rating.view;

import mindustry.gen.Iconc;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopScope;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.ladder.LadderStore;
import org.xcore.plugin.rating.ladder.StandingPage;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.rating.season.SeasonStore;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

import static com.ospx.flubundle.Bundle.args;

/**
 * The {@code /top} category of a ladder: standings by rating, one scope per season. A past
 * season is the same leaderboard read with another season number.
 */
public final class LadderTopCategoryProvider implements TopCategoryProvider {
    private static final String NAME = "name";
    private static final String STARTS_AT = "startsAt";
    private static final String ENDS_AT = "endsAt";
    private static final String PRIZE_COUNT = "prizes";
    private static final String PRIZE_PREFIX = "prize.";

    private final String id;
    private final int priority;
    private final Ladder ladder;
    private final SeasonStore seasons;
    private final SeasonText text;
    @Nullable
    private final PlayerDataRepository players;

    LadderTopCategoryProvider(String id, int priority, Ladder ladder, SeasonStore seasons, SeasonText text,
                              @Nullable PlayerDataRepository players) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.priority = priority;
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.text = Objects.requireNonNull(text, "text");
        this.players = players;
    }

    @Override
    public String id() {
        return id;
    }

    /** The ladder this category shows. */
    public String ladderId() {
        return ladder.id();
    }

    @Override
    public String displayName(Localization local) {
        return local.t(ladder.definition().displayNameKey());
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public List<TopScope> scopes() {
        List<Season> known = seasons.list(ladder.id());
        if (known.size() < 2) {
            return List.of();
        }
        int current = ladder.currentSeason();
        List<TopScope> scopes = new ArrayList<>(known.size());
        for (Season season : known) {
            scopes.add(new TopScope(String.valueOf(season.number()), season.number() == current, Map.of(
                    NAME, season.name(),
                    STARTS_AT, String.valueOf(season.startsAt().toEpochMilli()),
                    ENDS_AT, String.valueOf(season.endsAt().toEpochMilli()))));
        }
        return scopes;
    }

    @Override
    public String formatScope(TopScope scope, Localization local) {
        String title = text.title(season(scope.id()), scope.attributes().get(NAME), local);
        Instant startsAt = instant(scope, STARTS_AT);
        Instant endsAt = instant(scope, ENDS_AT);
        if (startsAt == null || endsAt == null) {
            return title;
        }
        return scope.current()
                ? local.t("top-menu-scope-current", args("season", title, "remaining", text.remaining(endsAt, local)))
                : local.t("top-menu-scope-past", args(
                        "season", title, "from", text.date(startsAt), "to", text.date(endsAt)));
    }

    @Override
    public LeaderboardPage loadPage(LeaderboardPageRequest request) {
        int season = season(request.scopeId());
        int pageSize = Math.min(request.pageSize(), LadderStore.MAX_PAGE_SIZE);
        StandingPage page = ladder.top(season, pageSize, request.cursor());
        Map<String, PlayerData> profiles = profiles(page.standings());

        Season shown = seasons.find(ladder.id(), season).orElse(null);
        List<LeaderboardEntry> entries = new ArrayList<>(page.standings().size());
        int rank = (request.page() - 1) * pageSize + 1;
        for (LadderStanding standing : page.standings()) {
            PlayerData profile = profiles.get(standing.uuid());
            RatingLeague league = standing.league();
            Map<String, String> attributes = LeaderboardEntry.profileAttributes(profile);
            attributes.put("leagueIcon", league.icon());
            attributes.put("leagueName", league.name());
            if (shown != null) {
                putPrizes(attributes, shown.prizesFor(rank));
            }
            entries.add(new LeaderboardEntry(
                    standing.uuid(),
                    rank++,
                    profile != null ? profile.nickname : null,
                    String.valueOf(standing.rating()),
                    attributes,
                    ""
            ));
        }

        Integer selfRank = null;
        String selfValue = null;
        String viewerUuid = request.viewerData() != null ? request.viewerData().uuid : null;
        OptionalLong viewerRank = ladder.rankOf(season, viewerUuid);
        if (viewerRank.isPresent()) {
            selfRank = Math.toIntExact(viewerRank.getAsLong());
            selfValue = ladder.played(season, viewerUuid)
                    .map(standing -> String.valueOf(standing.rating()))
                    .orElse(null);
        }

        return new LeaderboardPage(request.page(), entries, page.hasNext(), page.nextCursor(),
                ladder.count(season), selfRank, selfValue);
    }

    @Override
    public String formatValue(LeaderboardEntry entry, Localization local) {
        String value = entry != null ? formatValue(entry.primaryValue(), local) : "-";
        if (entry == null || local == null) {
            return value;
        }
        String prizes = PrizeText.brief(prizesOf(entry.attributes()), local);
        return prizes.isEmpty() ? value : value + "  [gold]" + Iconc.star + " " + prizes + "[]";
    }

    /** Prizes ride on the row as flat attributes, because the row is built before the viewer's language is known. */
    private static void putPrizes(Map<String, String> attributes, List<SeasonPrize> prizes) {
        attributes.put(PRIZE_COUNT, String.valueOf(prizes.size()));
        for (int i = 0; i < prizes.size(); i++) {
            attributes.put(PRIZE_PREFIX + i + ".kind", prizes.get(i).kind().name());
            attributes.put(PRIZE_PREFIX + i + ".value", prizes.get(i).value());
            attributes.put(PRIZE_PREFIX + i + ".description", prizes.get(i).description());
        }
    }

    private static List<SeasonPrize> prizesOf(Map<String, String> attributes) {
        int count;
        try {
            count = Integer.parseInt(attributes.getOrDefault(PRIZE_COUNT, "0"));
        } catch (NumberFormatException e) {
            return List.of();
        }
        List<SeasonPrize> prizes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String value = attributes.get(PRIZE_PREFIX + i + ".value");
            String kind = attributes.get(PRIZE_PREFIX + i + ".kind");
            if (value == null || kind == null) {
                continue;
            }
            // Only the words matter here, so the places are a placeholder.
            prizes.add(new SeasonPrize(1, 1, PrizeKind.valueOf(kind), value,
                    attributes.getOrDefault(PRIZE_PREFIX + i + ".description", "")));
        }
        return prizes;
    }

    @Override
    public String formatValue(String primaryValue, Localization local) {
        try {
            long rating = Long.parseLong(primaryValue);
            Locale locale = local != null && local.getLocale() != null ? local.getLocale() : Locale.ROOT;
            return NumberFormat.getIntegerInstance(locale).format(rating) + " ELO";
        } catch (NumberFormatException e) {
            return "-";
        }
    }

    /** The season a scope id names; anything that is not a season so far means the running one. */
    private int season(@Nullable String scopeId) {
        int current = ladder.currentSeason();
        if (scopeId == null) {
            return current;
        }
        try {
            int season = Integer.parseInt(scopeId);
            return season >= Ladder.FIRST_SEASON && season <= current ? season : current;
        } catch (NumberFormatException e) {
            return current;
        }
    }

    @Nullable
    private static Instant instant(TopScope scope, String attribute) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(scope.attributes().get(attribute)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, PlayerData> profiles(List<LadderStanding> standings) {
        Map<String, PlayerData> profiles = new HashMap<>();
        if (players == null || standings.isEmpty()) {
            return profiles;
        }
        for (PlayerData profile : players.findByUuids(standings.stream().map(LadderStanding::uuid).toList())) {
            profiles.put(profile.uuid, profile);
        }
        return profiles;
    }
}
