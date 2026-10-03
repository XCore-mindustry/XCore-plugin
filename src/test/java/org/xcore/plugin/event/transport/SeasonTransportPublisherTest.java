package org.xcore.plugin.event.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonEvents;
import org.xcore.plugin.rating.season.SeasonLifecycleService;
import org.xcore.plugin.rating.season.SeasonNotice;
import org.xcore.plugin.rating.season.SeasonStatus;
import org.xcore.plugin.rating.season.SeasonSummary;
import org.xcore.plugin.service.NetworkService;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonEndedV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonEndingSoonV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonRescheduledV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonStartedV1;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SeasonTransportPublisherTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:30:00Z");

    private final SeasonLifecycleService lifecycle = mock(SeasonLifecycleService.class);
    private final NetworkService network = mock(NetworkService.class);
    private final TomlXcoreConfig config = new TomlXcoreConfig();
    private final SeasonTransportPublisher publisher;

    SeasonTransportPublisherTest() {
        config.server.name = "mini-pvp";
        publisher = new SeasonTransportPublisher(lifecycle, network, config, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Season season(int number, SeasonStatus status, SeasonSummary summary) {
        return new Season("minipvp", number, "", Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"), status, Set.of(), List.of(), summary, List.of(), 0, 1);
    }

    private Object posted() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(network).post(event.capture());
        return event.getValue();
    }

    @Test
    @DisplayName("it listens to the lifecycle once the server is up")
    void init_registers() {
        publisher.init();

        verify(lifecycle).addEvents(publisher);
        assertThat(publisher).isInstanceOf(SeasonEvents.class);
    }

    @Test
    @DisplayName("every season transition is posted as its protocol event, stamped with this server and time")
    void transitionsArePosted() {
        publisher.started(season(3, SeasonStatus.CLOSING, null), season(4, SeasonStatus.ACTIVE, null));
        var started = (RatingSeasonStartedV1) posted();
        assertThat(started.server()).isEqualTo("mini-pvp");
        assertThat(started.occurredAt()).isEqualTo(NOW.toString());
        assertThat(started.previousSeason()).isEqualTo(3);
    }

    @Test
    @DisplayName("ending soon, ended and rescheduled each map to their own event")
    void otherEvents() {
        NetworkService each = mock(NetworkService.class);
        var local = new SeasonTransportPublisher(lifecycle, each, config, Clock.fixed(NOW, ZoneOffset.UTC));
        Season active = season(4, SeasonStatus.ACTIVE, null);

        local.noticeDue(active, new SeasonNotice("24h", Duration.ofHours(24)));
        local.ended(season(3, SeasonStatus.ARCHIVED, new SeasonSummary(10, 20)));
        local.rescheduled(active, active, AuditActor.builder().type(AuditActorType.SERVER_CONSOLE)
                .nameSnapshot("Console").build(), null);

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(each, org.mockito.Mockito.times(3)).post(events.capture());
        assertThat(events.getAllValues()).hasSize(3);
        assertThat(events.getAllValues().get(0)).isInstanceOf(RatingSeasonEndingSoonV1.class);
        assertThat(events.getAllValues().get(1)).isInstanceOf(RatingSeasonEndedV1.class);
        assertThat(events.getAllValues().get(2)).isInstanceOf(RatingSeasonRescheduledV1.class);
    }
}
