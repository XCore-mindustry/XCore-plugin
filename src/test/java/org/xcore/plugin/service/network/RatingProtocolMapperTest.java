package org.xcore.plugin.service.network;

import org.xcore.protocol.generated.shared.SeasonPrizeV1Kind;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.rating.season.PrizeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonNotice;
import org.xcore.plugin.rating.season.SeasonPodiumEntry;
import org.xcore.plugin.rating.season.SeasonStatus;
import org.xcore.plugin.rating.season.SeasonSummary;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ActorRefV1ActorType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RatingProtocolMapperTest {
    private static final Instant STARTS = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant AT = Instant.parse("2026-10-01T00:30:00Z");

    private static Season season(int number, String name, SeasonStatus status, List<SeasonPodiumEntry> podium,
                                 SeasonSummary summary) {
        return new Season("minipvp", number, name, STARTS, ENDS, status, Set.of(), podium, summary,
                List.of(), List.of(), 0, 1);
    }

    @Test
    @DisplayName("a season without a name is labelled by its number")
    void seasonRef_fallsBackToNumberedName() {
        var named = RatingProtocolMapper.toSeasonRef(season(3, "Summer", SeasonStatus.ACTIVE, List.of(), null));
        var unnamed = RatingProtocolMapper.toSeasonRef(season(3, "", SeasonStatus.ACTIVE, List.of(), null));

        assertThat(named.name()).isEqualTo("Summer");
        assertThat(unnamed.name()).isEqualTo("Season 3");
        assertThat(unnamed.ladder()).isEqualTo("minipvp");
        assertThat(unnamed.startsAt()).isEqualTo("2026-07-01T00:00:00Z");
        assertThat(unnamed.endsAt()).isEqualTo("2026-10-01T00:00:00Z");
    }

    @Test
    @DisplayName("the ended event carries the podium with Discord identities only where linked")
    void ended_mapsPodium() {
        Season archived = season(3, "", SeasonStatus.ARCHIVED, List.of(
                new SeasonPodiumEntry(1, "uuid-1", 101, "Alice", 1820, "DIAMOND", 64, 47, "111", "alice"),
                new SeasonPodiumEntry(2, "uuid-2", -1, "", 1744, "PLATINUM", 58, 38, "", "")),
                new SeasonSummary(312, 4120));

        var event = RatingProtocolMapper.toEnded(archived, "mini-pvp", AT);

        assertThat(event.server()).isEqualTo("mini-pvp");
        assertThat(event.occurredAt()).isEqualTo("2026-10-01T00:30:00Z");
        assertThat(event.summary().participants()).isEqualTo(312);
        assertThat(event.summary().matches()).isEqualTo(4120);
        assertThat(event.podium()).hasSize(2);
        var first = event.podium().get(0);
        assertThat(first.player().playerPid()).isEqualTo(101);
        assertThat(first.discord().discordId()).isEqualTo("111");
        assertThat(first.discord().discordUsername()).isEqualTo("alice");
        var second = event.podium().get(1);
        assertThat(second.discord()).isNull();
        assertThat(second.player().playerPid()).isNull();
        assertThat(second.player().playerName()).isEqualTo("Unknown");
        assertThat(event.toPayload()).containsKey("podium");
    }

    @Test
    @DisplayName("started and ending-soon events name the season and what led to them")
    void startedAndEndingSoon() {
        Season previous = season(3, "", SeasonStatus.CLOSING, List.of(), null);
        Season started = season(4, "", SeasonStatus.ACTIVE, List.of(), null);

        assertThat(RatingProtocolMapper.toStarted(previous, started, "mini-pvp", AT).previousSeason()).isEqualTo(3);
        var soon = RatingProtocolMapper.toEndingSoon(started, new SeasonNotice("7d", Duration.ofDays(7)),
                "mini-pvp", AT);
        assertThat(soon.notice()).isEqualTo("7d");
        assertThat(soon.season().season()).isEqualTo(4);
    }

    @Test
    @DisplayName("a reschedule keeps the old end, the actor and drops a blank reason")
    void rescheduled() {
        Season before = season(3, "", SeasonStatus.ACTIVE, List.of(), null);
        Season after = new Season("minipvp", 3, "", STARTS, ENDS.plus(Duration.ofDays(14)), SeasonStatus.ACTIVE,
                Set.of(), List.of(), null, List.of(), List.of(), 0, 2);
        AuditActor actor = AuditActor.builder().type(AuditActorType.DISCORD_USER).id("222")
                .nameSnapshot("Admin").discordId("222").build();

        var withReason = RatingProtocolMapper.toRescheduled(before, after, actor, " Tournament ", "mini-pvp", AT);
        var blank = RatingProtocolMapper.toRescheduled(before, after, actor, "  ", "mini-pvp", AT);

        assertThat(withReason.previousEndsAt()).isEqualTo("2026-10-01T00:00:00Z");
        assertThat(withReason.season().endsAt()).isEqualTo("2026-10-15T00:00:00Z");
        assertThat(withReason.reason()).isEqualTo("Tournament");
        assertThat(blank.reason()).isNull();
        assertThat(withReason.actor().actorType()).isEqualTo(ActorRefV1ActorType.DISCORD);
        assertThat(withReason.actor().actorDiscordId()).isEqualTo("222");
    }

    @Test
    @DisplayName("actors survive the trip between audit and protocol form")
    void actorsRoundTrip() {
        AuditActor discord = RatingProtocolMapper.toAuditActor(
                new ActorRefV1("Admin", "222", ActorRefV1ActorType.DISCORD));
        assertThat(discord.type).isEqualTo(AuditActorType.DISCORD_USER);
        assertThat(discord.id).isEqualTo("222");
        assertThat(discord.discordId).isEqualTo("222");
        assertThat(discord.nameSnapshot).isEqualTo("Admin");

        AuditActor anonymous = RatingProtocolMapper.toAuditActor(new ActorRefV1("someone", null, null));
        assertThat(anonymous.type).isEqualTo(AuditActorType.SYSTEM);
        assertThat(anonymous.id).isEqualTo("someone");

        ActorRefV1 console = RatingProtocolMapper.toActorRef(AuditActor.builder()
                .type(AuditActorType.SERVER_CONSOLE).id("console").nameSnapshot("Console").build());
        assertThat(console.actorType()).isEqualTo(ActorRefV1ActorType.SERVER);
        assertThat(console.actorDiscordId()).isNull();
    }

    @Test
    @DisplayName("prizes ride on the ending-soon event and on the podium places they cover")
    void prizes_rideOnEvents() {
        SeasonPrize champion = new SeasonPrize(1, 1, PrizeKind.BADGE, "season-champion", "");
        SeasonPrize podium = new SeasonPrize(1, 3, PrizeKind.CUSTOM, "Nitro", "One month");
        Season running = season(3, "", SeasonStatus.ACTIVE, List.of(), null).withPrizes(List.of(champion, podium));

        var soon = RatingProtocolMapper.toEndingSoon(running, new SeasonNotice("7d", Duration.ofDays(7)),
                "mini-pvp", AT);
        assertThat(soon.prizes()).hasSize(2);
        assertThat(soon.prizes().get(0).kind()).isEqualTo(SeasonPrizeV1Kind.BADGE);
        assertThat(soon.prizes().get(0).description()).isNull();
        assertThat(soon.prizes().get(1).placeTo()).isEqualTo(3);
        assertThat(soon.prizes().get(1).description()).isEqualTo("One month");

        Season archived = season(3, "", SeasonStatus.ARCHIVED, List.of(
                new SeasonPodiumEntry(1, "uuid-1", 101, "Alice", 1820, "DIAMOND", 64, 47, "", ""),
                new SeasonPodiumEntry(2, "uuid-2", 102, "Bob", 1744, "PLATINUM", 58, 38, "", ""),
                new SeasonPodiumEntry(4, "uuid-4", 104, "Dan", 1500, "GOLD", 40, 20, "", "")),
                new SeasonSummary(10, 20)).withPrizes(List.of(champion, podium));
        var ended = RatingProtocolMapper.toEnded(archived, "mini-pvp", AT);
        assertThat(ended.podium().get(0).prizes()).hasSize(2);
        assertThat(ended.podium().get(1).prizes()).hasSize(1);
        assertThat(ended.podium().get(2).prizes()).isNull();
    }

    @Test
    @DisplayName("a season without prizes sends none and prizes survive the trip back from the wire")
    void prizes_roundTrip() {
        var plain = RatingProtocolMapper.toEndingSoon(season(3, "", SeasonStatus.ACTIVE, List.of(), null),
                new SeasonNotice("7d", Duration.ofDays(7)), "mini-pvp", AT);
        assertThat(plain.prizes()).isNull();
        assertThat(plain.toPayload()).doesNotContainKey("prizes");

        SeasonPrize prize = new SeasonPrize(2, 4, PrizeKind.CUSTOM, "Sticker pack", "Mailed");
        assertThat(RatingProtocolMapper.toPrize(RatingProtocolMapper.toPrize(prize))).isEqualTo(prize);
    }
}
