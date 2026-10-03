package org.xcore.plugin.rating.ladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.idempotency.InMemoryIdempotencyLedger;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.RatingPolicy;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LadderTopCategoryProviderTest {
    private InMemoryLadderStore store;
    private PlayerDataRepository players;
    private LadderTopCategoryProvider provider;

    @BeforeEach
    void setUp() {
        store = new InMemoryLadderStore();
        Ladder ladder = new LadderService(store, new InMemoryIdempotencyLedger())
                .register(new LadderDefinition("duel", "top-menu-category-duel", RatingPolicy.teamEloV1()));
        players = mock(PlayerDataRepository.class);
        provider = new LadderTopCategoryProvider("DUEL", 20, ladder, players);

        store.put(new LadderStanding("duel", 1, "u1", 1500, 1500, 10, 8, Map.of()));
        store.put(new LadderStanding("duel", 1, "u2", 1200, 1250, 10, 5, Map.of()));
        store.put(new LadderStanding("duel", 1, "u3", 900, 1000, 10, 2, Map.of()));
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
        when(players.findByUuids(anyCollection()))
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
        when(players.findByUuids(anyCollection())).thenReturn(List.of());
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
        when(players.findByUuids(anyCollection())).thenReturn(List.of());

        LeaderboardPage page = provider.loadPage(
                new LeaderboardPageRequest("DUEL", 1, 10, null, profile("stranger", 99, "Stranger")));

        assertThat(page.selfRank()).isNull();
        assertThat(page.selfPrimaryValue()).isNull();
    }
}
