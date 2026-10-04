package org.xcore.plugin.service.network;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonNotice;
import org.xcore.plugin.rating.season.SeasonPodiumEntry;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingAccountsMergeResponseV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingPrizeGrantUpdateResponseV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonEndedV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonEndingSoonV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonPrizesSetResponseV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonRescheduleResponseV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonRescheduledV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonStartedV1;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ActorRefV1ActorType;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;
import org.xcore.protocol.generated.shared.SeasonPodiumEntryV1;
import org.xcore.protocol.generated.shared.SeasonPrizeV1;
import org.xcore.protocol.generated.shared.SeasonPrizeV1Kind;
import org.xcore.protocol.generated.shared.SeasonRefV1;
import org.xcore.protocol.generated.shared.SeasonSummaryV1;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Translates seasons to and from the {@code rating} protocol family. */
public final class RatingProtocolMapper {
    private RatingProtocolMapper() {
    }

    public static RatingSeasonStartedV1 toStarted(Season previous, Season started, String server, Instant at) {
        return new RatingSeasonStartedV1(toSeasonRef(started), previous.number(), server, at.toString());
    }

    public static RatingSeasonEndingSoonV1 toEndingSoon(Season season, SeasonNotice notice, String server,
                                                        Instant at) {
        return new RatingSeasonEndingSoonV1(toSeasonRef(season), notice.key(), server, at.toString(),
                toPrizes(season.prizes()));
    }

    public static RatingSeasonEndedV1 toEnded(Season archived, String server, Instant at) {
        Objects.requireNonNull(archived.summary(), "an archived season has a summary");
        List<SeasonPodiumEntryV1> podium = archived.podium().stream()
                .map(entry -> toPodiumEntry(entry, archived.prizesFor(entry.place())))
                .toList();
        return new RatingSeasonEndedV1(
                toSeasonRef(archived),
                podium,
                new SeasonSummaryV1(Math.toIntExact(archived.summary().participants()), archived.summary().matches()),
                server,
                at.toString());
    }

    public static RatingSeasonRescheduledV1 toRescheduled(Season before, Season after, AuditActor actor,
                                                          @Nullable String reason, String server, Instant at) {
        return new RatingSeasonRescheduledV1(
                toSeasonRef(after),
                before.endsAt().toString(),
                toActorRef(actor),
                reason == null || reason.isBlank() ? null : reason.strip(),
                server,
                at.toString());
    }

    public static RatingSeasonRescheduleResponseV1 toRescheduleResponse(String server, Season season,
                                                                        Instant previousEndsAt, boolean ended) {
        return new RatingSeasonRescheduleResponseV1(server, toSeasonRef(season), previousEndsAt.toString(), ended);
    }

    public static RatingAccountsMergeResponseV1 toMergeResponse(String server, int standingsMerged) {
        return new RatingAccountsMergeResponseV1(server, standingsMerged);
    }

    public static RatingSeasonPrizesSetResponseV1 toPrizesResponse(String server, Season season) {
        List<SeasonPrizeV1> prizes = toPrizes(season.prizes());
        return new RatingSeasonPrizesSetResponseV1(server, toSeasonRef(season), prizes == null ? List.of() : prizes);
    }

    public static RatingPrizeGrantUpdateResponseV1 toGrantUpdateResponse(String server, int season, int place,
                                                                         int updated) {
        return new RatingPrizeGrantUpdateResponseV1(server, season, place, updated);
    }

    /** @return null for no prizes: the field is optional and absent means none */
    public static @Nullable List<SeasonPrizeV1> toPrizes(List<SeasonPrize> prizes) {
        if (prizes.isEmpty()) return null;
        return prizes.stream().map(RatingProtocolMapper::toPrize).toList();
    }

    public static SeasonPrizeV1 toPrize(SeasonPrize prize) {
        return new SeasonPrizeV1(prize.placeFrom(), prize.placeTo(),
                prize.kind() == PrizeKind.BADGE ? SeasonPrizeV1Kind.BADGE : SeasonPrizeV1Kind.CUSTOM,
                prize.value(), blankToNull(prize.description()));
    }

    public static SeasonPrize toPrize(SeasonPrizeV1 prize) {
        return new SeasonPrize(prize.placeFrom(), prize.placeTo(),
                prize.kind() == SeasonPrizeV1Kind.BADGE ? PrizeKind.BADGE : PrizeKind.CUSTOM,
                prize.value(), prize.description());
    }

    public static SeasonRefV1 toSeasonRef(Season season) {
        // A season is named by an administrator or not at all; the protocol wants a label.
        String name = season.name().isBlank() ? "Season " + season.number() : season.name();
        return new SeasonRefV1(season.ladderId(), season.number(), name,
                season.startsAt().toString(), season.endsAt().toString());
    }

    private static SeasonPodiumEntryV1 toPodiumEntry(SeasonPodiumEntry entry, List<SeasonPrize> prizes) {
        DiscordIdentityRefV1 discord = entry.discordLinked()
                ? new DiscordIdentityRefV1(entry.discordId(), blankToNull(entry.discordUsername()))
                : null;
        return new SeasonPodiumEntryV1(
                entry.place(),
                new PlayerRefV1(entry.uuid(), entry.pid() > 0 ? entry.pid() : null,
                        entry.nickname().isBlank() ? "Unknown" : entry.nickname(), null),
                discord,
                entry.rating(),
                entry.league(),
                entry.matches(),
                entry.wins(),
                toPrizes(prizes));
    }

    public static ActorRefV1 toActorRef(AuditActor actor) {
        ActorRefV1ActorType type = switch (actor.type == null ? AuditActorType.SYSTEM : actor.type) {
            case PLAYER_ADMIN -> ActorRefV1ActorType.PLAYER;
            case SERVER_CONSOLE -> ActorRefV1ActorType.SERVER;
            case DISCORD_USER -> ActorRefV1ActorType.DISCORD;
            case SYSTEM -> ActorRefV1ActorType.SYSTEM;
        };
        String name = actor.nameSnapshot == null || actor.nameSnapshot.isBlank() ? "Unknown" : actor.nameSnapshot;
        return new ActorRefV1(name, blankToNull(actor.discordId), type);
    }

    /** The audit actor behind an actor a peer sent along with a request. */
    public static AuditActor toAuditActor(ActorRefV1 actor) {
        boolean discord = actor.actorType() == ActorRefV1ActorType.DISCORD;
        String discordId = blankToNull(actor.actorDiscordId());
        AuditActorType type = switch (actor.actorType() == null ? ActorRefV1ActorType.UNKNOWN : actor.actorType()) {
            case DISCORD -> AuditActorType.DISCORD_USER;
            case PLAYER -> AuditActorType.PLAYER_ADMIN;
            case SERVER -> AuditActorType.SERVER_CONSOLE;
            case SYSTEM, UNKNOWN -> AuditActorType.SYSTEM;
        };
        return AuditActor.builder()
                .type(type)
                .id(discord && discordId != null ? discordId : actor.actorName())
                .nameSnapshot(actor.actorName())
                .discordId(discordId)
                .build();
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
