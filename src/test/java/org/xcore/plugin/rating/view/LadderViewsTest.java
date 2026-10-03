package org.xcore.plugin.rating.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.integration.profile.ProfileSection;
import org.xcore.plugin.integration.profile.ProfileSectionView;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.ladder.LadderStanding;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

class LadderViewsTest {
    private RatingWorld world;
    private Localization local;

    @BeforeEach
    void setUp() {
        world = new RatingWorld();
        local = RatingWorld.echo();
    }

    private static PlayerData player(String uuid) {
        PlayerData data = new PlayerData(uuid, true);
        data.nickname = uuid;
        return data;
    }

    @Test
    @DisplayName("progress carries the running season, the player's rank in it and their past seasons")
    void progress() {
        world.standing(1, "ace", 1700, 1750, 12, 9);
        world.standing(1, "bob", 1300, 1300, 10, 4);
        world.finishSeason();
        world.standing(2, "ace", 1400, 1420, 3, 2);
        world.standing(2, "bob", 1500, 1500, 3, 3);

        LadderProgress progress = world.views.progress(world.ladder, "ace");

        assertThat(progress.season().number()).isEqualTo(2);
        assertThat(progress.standing().rating()).isEqualTo(1400);
        assertThat(progress.rank()).isEqualTo(2L);
        assertThat(progress.everPlayed()).isTrue();
        assertThat(progress.history()).extracting(LadderStanding::season).containsExactly(1);
        assertThat(progress.history().getFirst().finalRank()).isEqualTo(1);
        assertThat(progress.history().getFirst().rating()).isEqualTo(1700);
    }

    @Test
    @DisplayName("a player who sat this season out is unranked but keeps their history")
    void progress_unplacedThisSeason() {
        world.standing(1, "ace", 1700, 1750, 12, 9);
        world.finishSeason();

        LadderProgress progress = world.views.progress(world.ladder, "ace");

        assertThat(progress.standing().placed()).isFalse();
        // Soft reset: half of the distance to the starting rating carries over.
        assertThat(progress.standing().rating()).isEqualTo(1350);
        assertThat(progress.rank()).isNull();
        assertThat(progress.everPlayed()).isTrue();
        assertThat(world.views.progress(world.ladder, "nobody").everPlayed()).isFalse();
    }

    @Test
    @DisplayName("the profile section words the standing, the season and the history")
    void profileSection() {
        world.standing(1, "ace", 1700, 1750, 12, 9);
        world.finishSeason();
        world.standing(2, "ace", 1400, 1420, 4, 3);

        ProfileSection section = world.views.profileSection(world.ladder, "X", 10, false)
                .withDetail((target, l) -> Optional.of("legacy " + target.uuid))
                .load(player("ace")).orElseThrow().render(local);

        assertThat(section.headline())
                .startsWith("ladder-profile-headline{icon=X, ladder=top-menu-category-duel, standing=ladder-profile-standing{")
                .contains("rating=1400");
        assertThat(section.details()).containsExactly(
                "ladder-profile-matches{matches=4}",
                "ladder-profile-wins{rate=75, wins=3}",
                "ladder-profile-rank{rank=1}",
                "ladder-profile-peak{rating=1420}",
                "legacy ace");
        assertThat(section.lines()).hasSize(3);
        assertThat(section.lines().get(1)).startsWith("ladder-profile-season{").contains("season=season-title{number=2}");
        assertThat(section.lines().get(2))
                .startsWith("ladder-profile-history{")
                .contains("rank=#1", "rating=1700", "season=season-title{number=1}");
    }

    @Test
    @DisplayName("a section is hidden from players who never played the ladder unless the server hosts the mode")
    void profileSection_unplayed() {
        PlayerData stranger = player("stranger");

        assertThat(world.views.profileSection(world.ladder, "X", 10, false).load(stranger)).isEmpty();

        Optional<ProfileSectionView> hosted = world.views.profileSection(world.ladder, "X", 10, true).load(stranger);
        assertThat(hosted).isPresent();
        assertThat(hosted.get().render(local).details()).containsExactly("ladder-profile-unplaced");
    }

    @Test
    @DisplayName("the season card states the deadline, the field, the viewer's standing and last season's winners")
    void card() {
        world.standing(1, "ace", 1700, 1750, 12, 9);
        world.standing(1, "bob", 1300, 1300, 10, 4);
        when(world.players.findByUuids(anyCollection())).thenReturn(List.of(player("ace"), player("bob")));
        world.finishSeason();
        world.standing(2, "bob", 1500, 1500, 3, 3);

        SeasonOverview overview = world.views.overview(world.ladder, "bob");
        SeasonCard card = world.views.card(overview, local);

        assertThat(overview.participants()).isEqualTo(1);
        assertThat(overview.previous().number()).isEqualTo(1);
        assertThat(card.title())
                .isEqualTo("season-menu-card-title{ladder=top-menu-category-duel, season=season-title{number=2}}");
        assertThat(card.lines().get(0)).startsWith("season-menu-ends{date=").contains("remaining=");
        assertThat(card.lines().get(1)).isEqualTo("season-menu-participants{count=1}");
        assertThat(card.lines().get(2)).startsWith("season-menu-you{standing=ladder-profile-standing{").contains("rating=1500");
        assertThat(card.podiumTitle()).isEqualTo("season-menu-previous{season=season-title{number=1}}");
        assertThat(card.podium()).hasSize(2);
        assertThat(card.podium().getFirst()).startsWith("season-menu-podium-entry{").contains("name=ace", "place=1", "rating=1700");
    }

    @Test
    @DisplayName("the first season has no winners to show and a closing one says so instead of a countdown")
    void card_firstSeasonAndClosing() {
        SeasonCard first = world.views.card(world.views.overview(world.ladder, "ace"), local);
        assertThat(first.podium()).isEmpty();
        assertThat(first.podiumTitle()).isEmpty();
        assertThat(first.lines().get(0)).startsWith("season-menu-ends{date=03.01.2027");

        assertThat(world.views.text().remaining(RatingWorld.START.plus(Duration.ofSeconds(3541)), local))
                .isEqualTo("player-menu-time-hours{value=1}");
    }
}
