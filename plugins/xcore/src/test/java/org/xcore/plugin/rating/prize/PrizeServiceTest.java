package org.xcore.plugin.rating.prize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.rating.season.InMemorySeasonStore;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonPodiumEntry;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.rating.season.SeasonStatus;
import org.xcore.plugin.rating.season.SeasonSummary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrizeServiceTest {
    private static final Instant NOW = Instant.parse("2027-01-03T00:05:00Z");
    private static final AuditActor ADMIN = AuditActor.builder()
            .type(AuditActorType.DISCORD_USER).id("222").nameSnapshot("Admin").build();

    private static final SeasonPrize CHAMPION = new SeasonPrize(1, 1, PrizeKind.BADGE, "season-champion", "");
    private static final SeasonPrize NITRO = new SeasonPrize(1, 3, PrizeKind.CUSTOM, "Discord Nitro", "1 month");

    private final InMemoryPrizeGrantRepository grants = new InMemoryPrizeGrantRepository();
    private final InMemorySeasonStore seasons = new InMemorySeasonStore();
    private final List<String> delivered = new ArrayList<>();
    private PrizeOutcome badgeOutcome;
    private RuntimeException badgeFailure;
    private PrizeService service;

    @BeforeEach
    void setUp() {
        badgeOutcome = PrizeOutcome.granted("unlocked");
        badgeFailure = null;
        PrizeHandler badge = new PrizeHandler() {
            @Override
            public PrizeKind kind() {
                return PrizeKind.BADGE;
            }

            @Override
            public void validate(SeasonPrize prize) {
                if (!prize.value().equals("season-champion")) throw new SeasonException("Badge not found");
            }

            @Override
            public PrizeOutcome deliver(PrizeGrant grant) {
                delivered.add(grant.playerUuid() + ":" + grant.value());
                if (badgeFailure != null) throw badgeFailure;
                return badgeOutcome;
            }
        };
        service = new PrizeService(grants, seasons, List.of(badge, new CustomPrizeHandler()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SeasonPodiumEntry entry(int place, String uuid) {
        return new SeasonPodiumEntry(place, uuid, place, uuid, 1500 - place, "GOLD", 20, 10, "", "");
    }

    private static Season archived(SeasonPrize... prizes) {
        return new Season("minipvp", 1, "", Instant.parse("2026-10-03T12:00:00Z"), Instant.parse("2027-01-03T00:00:00Z"),
                SeasonStatus.ARCHIVED, java.util.Set.of(),
                List.of(entry(1, "ace"), entry(2, "bob"), entry(4, "dan")), new SeasonSummary(5, 9),
                List.of(), List.of(prizes), 9, 3);
    }

    @Test
    @DisplayName("every podium place gets a grant for each prize that covers it")
    void award_createsGrantsPerPlace() {
        service.award(archived(CHAMPION, NITRO));

        assertThat(grants.findBySeason("minipvp:1")).extracting(PrizeGrant::id).containsExactly(
                "minipvp:1:1:ace:0", "minipvp:1:1:ace:1", "minipvp:1:2:bob:1");
        // Fourth place is covered by nothing.
        assertThat(grants.findBySeason("minipvp:1")).noneMatch(grant -> grant.playerUuid().equals("dan"));
    }

    @Test
    @DisplayName("automatic prizes are given at once, custom ones wait for a person")
    void award_deliversAutomaticPrizes() {
        service.award(archived(CHAMPION, NITRO));

        assertThat(delivered).containsExactly("ace:season-champion");
        List<PrizeGrant> found = grants.findBySeason("minipvp:1");
        assertThat(found.get(0).status()).isEqualTo(PrizeStatus.GRANTED);
        assertThat(found.get(0).grantedBy()).isEqualTo("system");
        assertThat(found.get(0).note()).isEqualTo("unlocked");
        assertThat(found.get(1).status()).isEqualTo(PrizeStatus.PENDING);
        assertThat(found.get(2).status()).isEqualTo(PrizeStatus.PENDING);
        assertThat(found.get(1).description()).isEqualTo("1 month");
    }

    @Test
    @DisplayName("awarding twice neither duplicates grants nor delivers a prize again")
    void award_isIdempotent() {
        Season season = archived(CHAMPION, NITRO);
        service.award(season);
        service.award(season);

        assertThat(grants.findBySeason("minipvp:1")).hasSize(3);
        assertThat(delivered).hasSize(1);
    }

    @Test
    @DisplayName("a prize the handler cannot give is marked failed and not retried")
    void award_keepsPermanentFailures() {
        badgeOutcome = PrizeOutcome.failed("Player ace no longer exists");
        Season season = archived(CHAMPION);

        service.award(season);
        service.award(season);

        PrizeGrant grant = grants.findBySeason("minipvp:1").getFirst();
        assertThat(grant.status()).isEqualTo(PrizeStatus.FAILED);
        assertThat(grant.note()).contains("no longer exists");
        assertThat(delivered).hasSize(1);
    }

    @Test
    @DisplayName("a transient failure leaves the grant pending, still delivers the rest and is retried")
    void award_retriesTransientFailures() {
        badgeFailure = new IllegalStateException("db down");
        Season season = archived(new SeasonPrize(1, 2, PrizeKind.BADGE, "season-champion", ""));

        assertThatThrownBy(() -> service.award(season)).isSameAs(badgeFailure);

        assertThat(delivered).containsExactly("ace:season-champion", "bob:season-champion");
        assertThat(grants.findBySeason("minipvp:1")).allMatch(grant -> grant.status() == PrizeStatus.PENDING);

        badgeFailure = null;
        service.award(season);

        assertThat(grants.findBySeason("minipvp:1")).allMatch(grant -> grant.status() == PrizeStatus.GRANTED);
    }

    @Test
    @DisplayName("a season without prizes creates nothing")
    void award_withoutPrizes() {
        service.award(archived());

        assertThat(grants.findBySeason("minipvp:1")).isEmpty();
    }

    @Test
    @DisplayName("a prize is validated by the handler of its kind")
    void validate_delegatesToHandler() {
        service.validate(CHAMPION);
        service.validate(NITRO);

        assertThatThrownBy(() -> service.validate(new SeasonPrize(1, 1, PrizeKind.BADGE, "nope", "")))
                .isInstanceOf(SeasonException.class);
    }

    @Test
    @DisplayName("a kind without a handler is refused")
    void unknownKind() {
        PrizeService bare = new PrizeService(grants, seasons, List.of(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> bare.validate(NITRO)).isInstanceOf(SeasonException.class);
        assertThatThrownBy(() -> new PrizeService(grants, seasons,
                List.of(new CustomPrizeHandler(), new CustomPrizeHandler()), Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a person delivering a place settles its waiting grants, with who and why")
    void markDelivered_settlesPlace() {
        seasons.create(archived(CHAMPION, NITRO));
        service.award(archived(CHAMPION, NITRO));

        int changed = service.markDelivered("minipvp", 1, 2, null, ADMIN, " code sent ");

        assertThat(changed).isEqualTo(1);
        PrizeGrant grant = grants.findBySeason("minipvp:1").stream()
                .filter(each -> each.place() == 2).findFirst().orElseThrow();
        assertThat(grant.status()).isEqualTo(PrizeStatus.DELIVERED);
        assertThat(grant.grantedBy()).isEqualTo("discord_user:222");
        assertThat(grant.note()).isEqualTo("code sent");
        assertThat(grant.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("delivering a place with nothing waiting, or an unknown season, is refused")
    void markDelivered_refusals() {
        seasons.create(archived(CHAMPION, NITRO));
        service.award(archived(CHAMPION, NITRO));
        service.markDelivered("minipvp", 1, 2, null, ADMIN, null);

        // First place's badge was already granted by the plugin: nothing is waiting except the Nitro.
        assertThat(service.markDelivered("minipvp", 1, 1, null, ADMIN, null)).isEqualTo(1);
        assertThatThrownBy(() -> service.markDelivered("minipvp", 1, 2, null, ADMIN, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("waiting");
        assertThatThrownBy(() -> service.markDelivered("minipvp", 9, 1, null, ADMIN, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("delivering to one player leaves the other places waiting")
    void markDelivered_singlePlayer() {
        Season season = archived(NITRO);
        seasons.create(season);
        service.award(season);
        assertThat(service.markDelivered("minipvp", 1, 2, 2, ADMIN, "sent")).isEqualTo(1);

        assertThat(grants.findBySeason("minipvp:1")).filteredOn(grant -> grant.place() == 2)
                .extracting(PrizeGrant::status).containsOnly(PrizeStatus.DELIVERED);
        assertThat(grants.findBySeason("minipvp:1")).filteredOn(grant -> grant.place() == 1)
                .extracting(PrizeGrant::status).containsOnly(PrizeStatus.PENDING);
    }

    @Test
    @DisplayName("a player who did not stand on that place cannot be marked as delivered to")
    void markDelivered_wrongPlayer() {
        Season season = archived(NITRO);
        seasons.create(season);
        service.award(season);

        assertThatThrownBy(() -> service.markDelivered("minipvp", 1, 2, 1, ADMIN, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("Player #1 is not at place 2");
        assertThatThrownBy(() -> service.markDelivered("minipvp", 1, 8, 8, ADMIN, null))
                .isInstanceOf(SeasonException.class).hasMessageContaining("not at place 8");
        assertThat(grants.findBySeason("minipvp:1")).extracting(PrizeGrant::status).containsOnly(PrizeStatus.PENDING);
    }
}
