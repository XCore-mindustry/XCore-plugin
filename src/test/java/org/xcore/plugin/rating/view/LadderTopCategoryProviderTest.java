package org.xcore.plugin.rating.view;

import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.integration.top.TopScope;
import org.xcore.plugin.rating.RatingLeague;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LadderTopCategoryProviderTest {
    private RatingWorld world;
    private LadderTopCategoryProvider provider;

    @BeforeEach
    void setUp() {
        world = new RatingWorld();
        provider = world.views.topCategory("DUEL", 20, world.ladder);

        world.standing(1, "u1", 1500, 1500, 10, 8);
        world.standing(1, "u2", 1200, 1250, 10, 5);
        world.standing(1, "u3", 900, 1000, 10, 2);
    }

    private static PlayerData profile(String uuid, int pid, String nickname) {
        PlayerData data = new PlayerData(uuid, true);
        data.pid = pid;
        data.nickname = nickname;
        return data;
    }

    @Test
    @DisplayName("identity comes from the ladder definition")
    void identity() {
        Localization local = mock(Localization.class);
        when(local.t("top-menu-category-duel")).thenReturn("Duel");

        assertThat(provider.id()).isEqualTo("DUEL");
        assertThat(provider.priority()).isEqualTo(20);
        assertThat(provider.displayName(local)).isEqualTo("Duel");
    }

    @Test
    @DisplayName("loadPage ranks standings, joins profiles in one query and reports the viewer")
    void loadPage_firstPage() {
        when(world.players.findByUuids(anyCollection()))
                .thenReturn(List.of(profile("u1", 11, "Alpha"), profile("u2", 12, "Beta")));

        LeaderboardPage page = provider.loadPage(
                new LeaderboardPageRequest("DUEL", 1, 2, null, profile("u3", 13, "Gamma")));

        assertThat(page.entries()).extracting(LeaderboardEntry::playerUuid).containsExactly("u1", "u2");
        assertThat(page.entries()).extracting(LeaderboardEntry::rank).containsExactly(1, 2);
        assertThat(page.entries()).extracting(LeaderboardEntry::displayName).containsExactly("Alpha", "Beta");
        assertThat(page.entries()).extracting(LeaderboardEntry::primaryValue).containsExactly("1500", "1200");
        assertThat(page.entries().getFirst().attributes())
                .containsEntry("pid", "11")
                .containsEntry("leagueName", RatingLeague.fromRating(1500).name());
        assertThat(page.hasNext()).isTrue();
        assertThat(page.totalEntries()).isEqualTo(3L);
        assertThat(page.selfRank()).isEqualTo(3);
        assertThat(page.selfPrimaryValue()).isEqualTo("900");
    }

    @Test
    @DisplayName("loadPage continues from the cursor and keeps counting ranks")
    void loadPage_secondPage() {
        when(world.players.findByUuids(anyCollection())).thenReturn(List.of());
        LeaderboardPage first = provider.loadPage(new LeaderboardPageRequest("DUEL", 1, 2, null, null));

        LeaderboardPage second = provider.loadPage(
                new LeaderboardPageRequest("DUEL", 2, 2, first.nextCursor(), null));

        assertThat(second.entries()).extracting(LeaderboardEntry::playerUuid).containsExactly("u3");
        assertThat(second.entries().getFirst().rank()).isEqualTo(3);
        assertThat(second.entries().getFirst().displayName()).isEmpty();
        assertThat(second.hasNext()).isFalse();
        assertThat(second.selfRank()).isNull();
    }

    @Test
    @DisplayName("a viewer without a rated match has no rank")
    void loadPage_unplacedViewer() {
        when(world.players.findByUuids(anyCollection())).thenReturn(List.of());

        LeaderboardPage page = provider.loadPage(
                new LeaderboardPageRequest("DUEL", 1, 10, null, profile("stranger", 99, "Stranger")));

        assertThat(page.selfRank()).isNull();
        assertThat(page.selfPrimaryValue()).isNull();
    }

    @Test
    @DisplayName("a ladder in its first season offers no season switcher")
    void scopes_firstSeason() {
        assertThat(provider.scopes()).isEmpty();
    }

    @Test
    @DisplayName("once a season is over the scopes list every season, the running one first")
    void scopes_afterSeasonEnd() {
        world.finishSeason();

        List<TopScope> scopes = provider.scopes();

        assertThat(scopes).extracting(TopScope::id).containsExactly("2", "1");
        assertThat(scopes).extracting(TopScope::current).containsExactly(true, false);

        Localization local = RatingWorld.echo();
        assertThat(provider.formatScope(scopes.get(0), local))
                .startsWith("top-menu-scope-current{")
                .contains("season=season-title{number=2}");
        assertThat(provider.formatScope(scopes.get(1), local))
                .isEqualTo("top-menu-scope-past{from=03.10.2026, season=season-title{number=1}, to=03.01.2027}");
    }

    @Test
    @DisplayName("a past season is read through its scope; without one the page is the running season")
    void loadPage_pastSeason() {
        world.finishSeason();
        world.standing(2, "u2", 1100, 1100, 1, 1);
        PlayerData viewer = profile("u3", 13, "Gamma");

        LeaderboardPage past = provider.loadPage(new LeaderboardPageRequest("DUEL", 1, 10, null, viewer, "1"));
        LeaderboardPage running = provider.loadPage(new LeaderboardPageRequest("DUEL", 1, 10, null, viewer));

        assertThat(past.entries()).extracting(LeaderboardEntry::playerUuid).containsExactly("u1", "u2", "u3");
        assertThat(past.entries()).extracting(LeaderboardEntry::primaryValue).containsExactly("1500", "1200", "900");
        assertThat(past.totalEntries()).isEqualTo(3L);
        assertThat(past.selfRank()).isEqualTo(3);
        assertThat(past.selfPrimaryValue()).isEqualTo("900");

        assertThat(running.entries()).extracting(LeaderboardEntry::playerUuid).containsExactly("u2");
        assertThat(running.totalEntries()).isEqualTo(1L);
        assertThat(running.selfRank()).isNull();
    }

    @Test
    @DisplayName("a scope that names no season so far falls back to the running season")
    void loadPage_unknownScope() {
        world.finishSeason();
        world.standing(2, "u2", 1100, 1100, 1, 1);

        for (String scope : new String[]{"7", "0", "latest"}) {
            LeaderboardPage page = provider.loadPage(new LeaderboardPageRequest("DUEL", 1, 10, null, null, scope));
            assertThat(page.entries()).extracting(LeaderboardEntry::playerUuid).containsExactly("u2");
        }
    }

    @Test
    @DisplayName("ratings are written with the unit and digit grouping of the viewer's locale")
    void formatValue() {
        assertThat(provider.formatValue("1642", null)).isEqualTo("1,642 ELO");
        assertThat(provider.formatValue("", null)).isEqualTo("-");
    }

    @Test
    @DisplayName("rows on prized places carry the prize, which is worded in the viewer's language")
    void prizesOnRows() {
        world.lifecycle.addPrize("duel", new SeasonPrize(1, 1, PrizeKind.CUSTOM, "Nitro", "1 month of Nitro"),
                AuditActor.builder().type(AuditActorType.SERVER_CONSOLE).id("console").nameSnapshot("Console").build());
        world.lifecycle.addPrize("duel", new SeasonPrize(1, 2, PrizeKind.CUSTOM, "Sticker", ""),
                AuditActor.builder().type(AuditActorType.SERVER_CONSOLE).id("console").nameSnapshot("Console").build());
        when(world.players.findByUuids(anyCollection())).thenReturn(List.of());

        LeaderboardPage page = provider.loadPage(new LeaderboardPageRequest("DUEL", 1, 3, null, null));
        Localization local = RatingWorld.echo();

        String first = provider.formatValue(page.entries().get(0), local);
        assertThat(first).startsWith("1,500 ELO").contains("1 month of Nitro +1");
        assertThat(provider.formatValue(page.entries().get(1), local)).contains("Sticker").doesNotContain("+");
        assertThat(provider.formatValue(page.entries().get(2), local)).isEqualTo("900 ELO");
    }

    @Test
    @DisplayName("prizes follow podium places, which skip players with too few matches")
    void prizesFollowPodiumPlaces() {
        AuditActor console = AuditActor.builder()
                .type(AuditActorType.SERVER_CONSOLE).id("console").nameSnapshot("Console").build();
        world.lifecycle.addPrize("duel", new SeasonPrize(1, 1, PrizeKind.CUSTOM, "Nitro", ""), console);
        world.lifecycle.addPrize("duel", new SeasonPrize(2, 2, PrizeKind.CUSTOM, "Sticker", ""), console);
        // Tops the leaderboard, but has not played enough to stand on the podium.
        world.standing(1, "u0", 1900, 1900, 3, 3);
        Localization local = RatingWorld.echo();

        LeaderboardPage running = provider.loadPage(new LeaderboardPageRequest("DUEL", 1, 4, null, null));

        assertThat(running.entries()).extracting(LeaderboardEntry::playerUuid).containsExactly("u0", "u1", "u2", "u3");
        assertThat(provider.formatValue(running.entries().get(0), local)).isEqualTo("1,900 ELO");
        assertThat(provider.formatValue(running.entries().get(1), local)).contains("Nitro");
        assertThat(provider.formatValue(running.entries().get(2), local)).contains("Sticker");
        assertThat(provider.formatValue(running.entries().get(3), local)).isEqualTo("900 ELO");

        world.finishSeason();
        LeaderboardPage archived = provider.loadPage(new LeaderboardPageRequest("DUEL", 1, 4, null, null, "1"));

        assertThat(provider.formatValue(archived.entries().get(0), local)).isEqualTo("1,900 ELO");
        assertThat(provider.formatValue(archived.entries().get(1), local)).contains("Nitro");
        assertThat(provider.formatValue(archived.entries().get(2), local)).contains("Sticker");
    }
}
