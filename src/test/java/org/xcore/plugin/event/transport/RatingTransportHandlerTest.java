package org.xcore.plugin.event.transport;

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
    private RatingTransportHandler handler;

    @BeforeEach
    void setUp() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        handler = new RatingTransportHandler(network, config, lifecycle, ladders);
    }

    private static Season season(Instant endsAt, List<SeasonReschedule> history, SeasonStatus status) {
        return new Season("minipvp", 3, "", STARTS, endsAt, status, Set.of(), List.of(), null, history, 0, 2);
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
}
