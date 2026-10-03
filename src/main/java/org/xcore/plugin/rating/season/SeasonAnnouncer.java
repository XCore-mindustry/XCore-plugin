package org.xcore.plugin.rating.season;

import arc.Events;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import mindustry.gen.Player;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerProfileUiController;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.ospx.flubundle.Bundle.args;

/**
 * Tells players in game that a season is about to end or that a new one has begun.
 * Only ladders a mode asked to {@link #follow} are announced, so a server stays quiet about
 * modes it does not host.
 */
@Singleton
public class SeasonAnnouncer implements SeasonObserver {
    private final SeasonLifecycleService lifecycle;
    private final SeasonResolver resolver;
    private final SeasonSchedule schedule;
    private final SessionService sessions;
    private final PlayerDisplayRefreshService displays;
    private final Clock clock;
    private final Map<String, LadderDefinition> followed = new ConcurrentHashMap<>();

    @Inject
    public SeasonAnnouncer(SeasonLifecycleService lifecycle,
                           SeasonResolver resolver,
                           SeasonSchedule schedule,
                           SessionService sessions,
                           PlayerDisplayRefreshService displays) {
        this(lifecycle, resolver, schedule, sessions, displays, Clock.systemUTC());
    }

    SeasonAnnouncer(SeasonLifecycleService lifecycle,
                    SeasonResolver resolver,
                    SeasonSchedule schedule,
                    SessionService sessions,
                    PlayerDisplayRefreshService displays,
                    Clock clock) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.displays = Objects.requireNonNull(displays, "displays");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @PostConstruct
    public void init() {
        lifecycle.addObserver(this);
        Events.on(EventType.PlayerJoin.class, event -> greet(event.player));
    }

    /** Announces this ladder's seasons to the players of this server. */
    public void follow(LadderDefinition ladder) {
        followed.put(ladder.id(), ladder);
    }

    /** The ladders this server announces, i.e. the ones of the modes it hosts. */
    public List<LadderDefinition> followed() {
        return followed.values().stream().sorted(Comparator.comparing(LadderDefinition::id)).toList();
    }

    @Override
    public void seasonChanged(SeasonChange change) {
        LadderDefinition ladder = followed.get(change.season().ladderId());
        switch (change) {
            case SeasonChange.Started started -> {
                // League icons were drawn from the previous season's standings.
                displays.refreshAll();
                if (ladder != null) {
                    for (Session session : sessions.getAllCachedSnapshot()) {
                        announceStart(session, ladder, started);
                    }
                }
            }
            case SeasonChange.NoticeDue due -> {
                if (ladder != null && due.season().active()) {
                    for (Session session : sessions.getAllCachedSnapshot()) {
                        announceEnding(session, ladder, due.season());
                    }
                }
            }
        }
    }

    /** Reminds a joining player of every followed season that is about to end. */
    public void greet(Player player) {
        Optional<Duration> soon = schedule.firstNotice().map(SeasonNotice::lead);
        if (soon.isEmpty() || followed.isEmpty()) {
            return;
        }
        Session session = sessions.get(player);
        followed.values().stream()
                .sorted(Comparator.comparing(LadderDefinition::id))
                .forEach(ladder -> resolver.current(ladder.id())
                        .filter(Season::active)
                        .filter(season -> season.remaining(clock.instant()).compareTo(soon.get()) <= 0)
                        .ifPresent(season -> announceEnding(session, ladder, season)));
    }

    private void announceStart(Session session, LadderDefinition ladder, SeasonChange.Started started) {
        if (!reachable(session)) {
            return;
        }
        Localization locale = session.locale();
        locale.send("season-started", args(
                "ladder", locale.format(ladder.displayNameKey()),
                "previous", started.previous().number(),
                "number", started.season().number()));
        locale.send("season-reset-" + schedule.reset().mode().name().toLowerCase());
    }

    private void announceEnding(Session session, LadderDefinition ladder, Season season) {
        if (!reachable(session)) {
            return;
        }
        Localization locale = session.locale();
        // Rounded up, so that the "1h" notice does not read "59m".
        int minutes = (int) Math.max(1, season.remaining(clock.instant()).plusSeconds(59).toMinutes());
        locale.send("season-ending-soon", args(
                "ladder", locale.format(ladder.displayNameKey()),
                "number", season.number(),
                "remaining", PlayerProfileUiController.formatDuration(minutes, locale)));
    }

    private static boolean reachable(Session session) {
        return session != null && session.player != null && session.player.con != null && session.locale() != null;
    }
}
