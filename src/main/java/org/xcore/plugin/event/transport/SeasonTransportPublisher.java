package org.xcore.plugin.event.transport;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonEvents;
import org.xcore.plugin.rating.season.SeasonLifecycleService;
import org.xcore.plugin.rating.season.SeasonNotice;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.RatingProtocolMapper;

import java.time.Clock;
import java.time.Instant;

/**
 * Tells the rest of the network about season transitions. {@link SeasonEvents} fires on the
 * one server that performed each transition, so each event is published once unless a
 * crash made the lifecycle's reconcile pass repeat it; receivers deduplicate.
 */
@Singleton
public class SeasonTransportPublisher implements SeasonEvents {
    private final SeasonLifecycleService lifecycle;
    private final NetworkService network;
    private final TomlXcoreConfig config;
    private final Clock clock;

    @Inject
    public SeasonTransportPublisher(SeasonLifecycleService lifecycle, NetworkService network, TomlXcoreConfig config) {
        this(lifecycle, network, config, Clock.systemUTC());
    }

    SeasonTransportPublisher(SeasonLifecycleService lifecycle, NetworkService network, TomlXcoreConfig config,
                             Clock clock) {
        this.lifecycle = lifecycle;
        this.network = network;
        this.config = config;
        this.clock = clock;
    }

    @PostConstruct
    public void init() {
        lifecycle.addEvents(this);
    }

    @Override
    public void started(@Nullable Season previous, Season season) {
        network.post(RatingProtocolMapper.toStarted(previous, season, server(), now()));
    }

    @Override
    public void noticeDue(Season season, SeasonNotice notice) {
        network.post(RatingProtocolMapper.toEndingSoon(season, notice, server(), now()));
    }

    @Override
    public void ended(Season archived) {
        network.post(RatingProtocolMapper.toEnded(archived, server(), now()));
    }

    @Override
    public void rescheduled(Season before, Season after, AuditActor actor, @Nullable String reason) {
        network.post(RatingProtocolMapper.toRescheduled(before, after, actor, reason, server(), now()));
    }

    private String server() {
        return config.server.name;
    }

    private Instant now() {
        return clock.instant();
    }
}
