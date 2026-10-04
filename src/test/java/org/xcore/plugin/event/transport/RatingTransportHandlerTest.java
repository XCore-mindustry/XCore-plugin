package org.xcore.plugin.event.transport;

import static org.mockito.ArgumentMatchers.anyInt;
import org.xcore.protocol.generated.messages.rating.RatingSeasonPrizesSetRequestV1Operation;
import org.xcore.protocol.generated.messages.rating.RatingPrizeGrantUpdateRequestV1Status;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingPrizeGrantUpdateResponseV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingPrizeGrantUpdateRequestV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonPrizesSetResponseV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonPrizesSetRequestV1;
import org.xcore.protocol.generated.shared.SeasonPrizeV1Kind;
import org.xcore.protocol.generated.shared.SeasonPrizeV1;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.prize.PrizeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonLifecycleService;
import org.xcore.plugin.rating.season.SeasonReschedule;
import org.xcore.plugin.rating.season.SeasonStatus;
import org.xcore.plugin.service.NetworkService;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingAccountsMergeRequestV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingAccountsMergeResponseV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonRescheduleRequestV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonRescheduleResponseV1;
import org.xcore.protocol.generated.messages.rating.RatingSeasonRescheduleRequestV1Operation;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ActorRefV1ActorType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RatingTransportHandlerTest {
    private static final Instant STARTS = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-10-01T00:00:00Z");
    private static final ActorRefV1 ADMIN = new ActorRefV1("Admin", "222", ActorRefV1ActorType.DISCORD);

    private final NetworkService network = mock(NetworkService.class);
    private final SeasonLifecycleService lifecycle = mock(SeasonLifecycleService.class);
    private final LadderService ladders = mock(LadderService.class);
    private final PrizeService prizes = mock(PrizeService.class);
    private RatingTransportHandler handler;

    @BeforeEach
    void setUp() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        handler = new RatingTransportHandler(network, config, lifecycle, ladders, prizes);
    }

    private static Season season(Instant endsAt, List<SeasonReschedule> history, SeasonStatus status) {
        return new Season("minipvp", 3, "", STARTS, endsAt, status, Set.of(), List.of(), null, history, List.of(), 0, 2);
    }

    private static RatingSeasonRescheduleRequestV1 request(String server, RatingSeasonRescheduleRequestV1Operation op,
                                                           Integer extendSeconds, String endsAt) {
        return new RatingSeasonRescheduleRequestV1(server, "minipvp", op, extendSeconds, endsAt, ADMIN, "Tournament");
    }

    private AuditActor discordActor() {
        return org.mockito.ArgumentMatchers.argThat(actor ->
                actor.type == AuditActorType.DISCORD_USER && "222".equals(actor.discordId));
    }

    @Test
    @DisplayName("extend moves the end by the requested seconds and answers with the old and new end")
    void extend() {
        Instant moved = ENDS.plus(Duration.ofSeconds(1209600));
        when(lifecycle.extend(eq("minipvp"), any(), discordActor(), eq("Tournament"))).thenReturn(
                season(moved, List.of(new SeasonReschedule(ENDS, moved, "discord_user:222", STARTS, "Tournament")),
                        SeasonStatus.ACTIVE));

        var request = request("mini-pvp", RatingSeasonRescheduleRequestV1Operation.EXTEND, 1209600, null);
        handler.reschedule(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UnaryOperator<Instant>> change = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(lifecycle).extend(eq("minipvp"), change.capture(), any(), any());
        assertThat(change.getValue().apply(ENDS)).isEqualTo(moved);

        ArgumentCaptor<RatingSeasonRescheduleResponseV1> response = ArgumentCaptor.forClass(
                RatingSeasonRescheduleResponseV1.class);
        verify(network).respond(eq(request), response.capture());
        assertThat(response.getValue().previousEndsAt()).isEqualTo(ENDS.toString());
        assertThat(response.getValue().season().endsAt()).isEqualTo(moved.toString());
        assertThat(response.getValue().ended()).isFalse();
    }

    @Test
    @DisplayName("set-end moves the end to the given moment")
    void setEnd() {
        Instant target = Instant.parse("2026-11-01T00:00:00Z");
        when(lifecycle.reschedule(eq("minipvp"), eq(target), discordActor(), eq("Tournament"))).thenReturn(
                season(target, List.of(new SeasonReschedule(ENDS, target, "discord_user:222", STARTS, "")),
                        SeasonStatus.ACTIVE));

        handler.reschedule(request("mini-pvp", RatingSeasonRescheduleRequestV1Operation.SET_END, null,
                target.toString()));

        verify(network).respond(any(), any(RatingSeasonRescheduleResponseV1.class));
    }

    @Test
    @DisplayName("end-now ends the season and says so")
    void endNow() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        when(lifecycle.endNow(eq("minipvp"), discordActor(), eq("Tournament"))).thenReturn(
                season(now, List.of(new SeasonReschedule(ENDS, now, "discord_user:222", now, "")),
                        SeasonStatus.CLOSING));

        handler.reschedule(request("mini-pvp", RatingSeasonRescheduleRequestV1Operation.END_NOW, null, null));

        ArgumentCaptor<RatingSeasonRescheduleResponseV1> response = ArgumentCaptor.forClass(
                RatingSeasonRescheduleResponseV1.class);
        verify(network).respond(any(), response.capture());
        assertThat(response.getValue().ended()).isTrue();
        assertThat(response.getValue().previousEndsAt()).isEqualTo(ENDS.toString());
    }

    @Test
    @DisplayName("a request for another server is left to that server")
    void ignoresOtherServers() {
        handler.reschedule(request("hexed", RatingSeasonRescheduleRequestV1Operation.END_NOW, null, null));
        handler.merge(new RatingAccountsMergeRequestV1("hexed", "a", "b"));

        verify(lifecycle, never()).endNow(anyString(), any(), any());
        verify(ladders, never()).mergePlayer(any(), anyString(), anyString());
        verify(network, never()).respond(any(), any());
        verify(network, never()).respondError(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("a refused change is answered as an error with the reason, not as a success")
    void rejectionsAreErrors() {
        when(lifecycle.endNow(anyString(), any(), any()))
                .thenThrow(new SeasonException("Ladder 'minipvp' has no running season"));

        var end = request("mini-pvp", RatingSeasonRescheduleRequestV1Operation.END_NOW, null, null);
        handler.reschedule(end);
        verify(network).respondError(end, "REJECTED", "Ladder 'minipvp' has no running season");

        var missing = request("mini-pvp", RatingSeasonRescheduleRequestV1Operation.EXTEND, null, null);
        handler.reschedule(missing);
        verify(network).respondError(eq(missing), eq("REJECTED"), anyString());

        var garbled = request("mini-pvp", RatingSeasonRescheduleRequestV1Operation.SET_END, null, "tomorrow");
        handler.reschedule(garbled);
        verify(network).respondError(eq(garbled), eq("REJECTED"), anyString());

        verify(network, never()).respond(any(), any());
    }

    @Test
    @DisplayName("an unexpected failure is answered without leaking its message")
    void failuresAreOpaque() {
        when(lifecycle.endNow(anyString(), any(), any())).thenThrow(new IllegalStateException("mongo://secret"));

        var end = request("mini-pvp", RatingSeasonRescheduleRequestV1Operation.END_NOW, null, null);
        handler.reschedule(end);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(network).respondError(eq(end), eq("FAILED"), message.capture());
        assertThat(message.getValue()).doesNotContain("secret");
    }

    @Test
    @DisplayName("merging accounts folds the standings and reports how many moved")
    void merge() {
        when(ladders.mergePlayer(isNull(), eq("uuid-old"), eq("uuid-new"))).thenReturn(3);

        var request = new RatingAccountsMergeRequestV1("mini-pvp", "uuid-old", "uuid-new");
        handler.merge(request);

        ArgumentCaptor<RatingAccountsMergeResponseV1> response = ArgumentCaptor.forClass(
                RatingAccountsMergeResponseV1.class);
        verify(network).respond(eq(request), response.capture());
        assertThat(response.getValue().standingsMerged()).isEqualTo(3);
    }

    @Test
    @DisplayName("a failed merge is reported as an error")
    void mergeFailure() {
        when(ladders.mergePlayer(any(), anyString(), anyString())).thenThrow(new IllegalStateException("boom"));

        var request = new RatingAccountsMergeRequestV1("mini-pvp", "uuid-old", "uuid-new");
        handler.merge(request);

        verify(network).respondError(eq(request), eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("the handler subscribes to both requests")
    void registers() {
        handler.registerListeners();

        verify(network).subscribe(eq(RatingSeasonRescheduleRequestV1.class), any());
        verify(network).subscribe(eq(RatingAccountsMergeRequestV1.class), any());
    }

    private static RatingSeasonPrizesSetRequestV1 prizeRequest(RatingSeasonPrizesSetRequestV1Operation op,
                                                                SeasonPrizeV1 prize, Integer from, Integer to) {
        return new RatingSeasonPrizesSetRequestV1("mini-pvp", "minipvp", op, prize, from, to, ADMIN);
    }

    @Test
    @DisplayName("adding a prize stores it and answers with the season's prizes")
    void setPrizes_add() {
        SeasonPrize prize = new SeasonPrize(1, 3, PrizeKind.CUSTOM, "Nitro", "");
        when(lifecycle.addPrize(eq("minipvp"), eq(prize), discordActor())).thenReturn(
                season(ENDS, List.of(), SeasonStatus.ACTIVE).withPrizes(List.of(prize)));

        handler.setPrizes(prizeRequest(RatingSeasonPrizesSetRequestV1Operation.ADD,
                new SeasonPrizeV1(1, 3, SeasonPrizeV1Kind.CUSTOM, "Nitro", null), null, null));

        ArgumentCaptor<RatingSeasonPrizesSetResponseV1> response = ArgumentCaptor.forClass(
                RatingSeasonPrizesSetResponseV1.class);
        verify(network).respond(any(), response.capture());
        assertThat(response.getValue().prizes()).hasSize(1);
        assertThat(response.getValue().season().season()).isEqualTo(3);
    }

    @Test
    @DisplayName("removing prizes without an end place clears just that place")
    void setPrizes_remove() {
        when(lifecycle.removePrizes(eq("minipvp"), eq(2), eq(2), discordActor())).thenReturn(
                season(ENDS, List.of(), SeasonStatus.ACTIVE));

        handler.setPrizes(prizeRequest(RatingSeasonPrizesSetRequestV1Operation.REMOVE, null, 2, null));

        ArgumentCaptor<RatingSeasonPrizesSetResponseV1> response = ArgumentCaptor.forClass(
                RatingSeasonPrizesSetResponseV1.class);
        verify(network).respond(any(), response.capture());
        assertThat(response.getValue().prizes()).isEmpty();
    }

    @Test
    @DisplayName("a prize request that is incomplete or refused is rejected, not failed")
    void setPrizes_rejections() {
        handler.setPrizes(prizeRequest(RatingSeasonPrizesSetRequestV1Operation.ADD, null, null, null));
        handler.setPrizes(prizeRequest(RatingSeasonPrizesSetRequestV1Operation.REMOVE, null, null, null));
        when(lifecycle.addPrize(any(), any(), any())).thenThrow(new SeasonException("Badge 'x' was not found"));
        handler.setPrizes(prizeRequest(RatingSeasonPrizesSetRequestV1Operation.ADD,
                new SeasonPrizeV1(1, 1, SeasonPrizeV1Kind.BADGE, "x", null), null, null));

        verify(network, org.mockito.Mockito.times(3)).respondError(any(), eq("REJECTED"), anyString());
        verify(network, never()).respond(any(), any());
    }

    @Test
    @DisplayName("an unexpected failure while changing prizes is reported as failed without leaking details")
    void setPrizes_failure() {
        when(lifecycle.addPrize(any(), any(), any())).thenThrow(new IllegalStateException("mongo://secret"));

        handler.setPrizes(prizeRequest(RatingSeasonPrizesSetRequestV1Operation.ADD,
                new SeasonPrizeV1(1, 1, SeasonPrizeV1Kind.BADGE, "veteran", null), null, null));

        verify(network).respondError(any(), eq("FAILED"), org.mockito.ArgumentMatchers.argThat(m -> !m.contains("secret")));
    }

    @Test
    @DisplayName("marking a place delivered answers with how many grants changed")
    void updateGrants() {
        when(prizes.markDelivered(eq("minipvp"), eq(2), eq(5), discordActor(), eq("code sent"))).thenReturn(2);

        handler.updateGrants(new RatingPrizeGrantUpdateRequestV1("mini-pvp", "minipvp", 2, 5,
                RatingPrizeGrantUpdateRequestV1Status.DELIVERED, ADMIN, "code sent"));

        ArgumentCaptor<RatingPrizeGrantUpdateResponseV1> response = ArgumentCaptor.forClass(
                RatingPrizeGrantUpdateResponseV1.class);
        verify(network).respond(any(), response.capture());
        assertThat(response.getValue().updated()).isEqualTo(2);
        assertThat(response.getValue().place()).isEqualTo(5);
    }

    @Test
    @DisplayName("marking a place nobody is waiting on is rejected; other servers' requests are ignored")
    void updateGrants_rejectionAndOtherServer() {
        when(prizes.markDelivered(any(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new SeasonException("No prize of season minipvp:2 is waiting at place 5"));
        var mine = new RatingPrizeGrantUpdateRequestV1("mini-pvp", "minipvp", 2, 5,
                RatingPrizeGrantUpdateRequestV1Status.DELIVERED, ADMIN, null);
        var other = new RatingPrizeGrantUpdateRequestV1("hexed", "minipvp", 2, 5,
                RatingPrizeGrantUpdateRequestV1Status.DELIVERED, ADMIN, null);

        handler.updateGrants(other);
        verify(network, never()).respondError(any(), any(), any());

        handler.updateGrants(mine);
        verify(network).respondError(eq(mine), eq("REJECTED"), anyString());
    }
}
