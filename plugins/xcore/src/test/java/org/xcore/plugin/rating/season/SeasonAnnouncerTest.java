package org.xcore.plugin.rating.season;

import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonAnnouncerTest {
    private static final LadderDefinition DUEL =
            new LadderDefinition("duel", "duel-name", new RatingPolicy(1000, 100, 32, 2));
    private static final Instant START = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant END = Instant.parse("2027-01-03T00:00:00Z");

    private SeasonResolver resolver;
    private SessionService sessions;
    private PlayerDisplayRefreshService displays;
    private MutableClock clock;
    private Localization locale;
    private Player player;
    private SeasonAnnouncer announcer;

    @BeforeEach
    void setUp() {
        resolver = new SeasonResolver();
        sessions = mock(SessionService.class);
        displays = mock(PlayerDisplayRefreshService.class);
        clock = new MutableClock(START);

        locale = mock(Localization.class);
        when(locale.format("duel-name")).thenReturn("Duel");
        when(locale.t(anyString(), anyMap())).thenAnswer(call ->
                call.<Map<String, Object>>getArgument(1).get("value") + call.<String>getArgument(0)
                        .replace("player-menu-time-", "").substring(0, 1));
        player = mock(Player.class);
        player.con = mock(NetConnection.class);
        Session session = mock(Session.class);
        session.player = player;
        when(session.locale()).thenReturn(locale);
        when(sessions.getAllCachedSnapshot()).thenReturn(List.of(session));
        when(sessions.get(player)).thenReturn(session);

        announcer = new SeasonAnnouncer(mock(SeasonLifecycleService.class), resolver,
                SeasonSchedule.from(new TomlSecretsConfig().rating.seasons), sessions, displays, clock);
    }

    private static Season season(int number, Instant startsAt, Instant endsAt) {
        return Season.opening("duel", number, startsAt, endsAt);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sent(String key) {
        ArgumentCaptor<Map<String, Object>> args = ArgumentCaptor.forClass(Map.class);
        verify(locale).send(eq(key), args.capture());
        return args.getValue();
    }

    @Test
    @DisplayName("a ladder nobody follows is not announced, but league icons are still refreshed on rollover")
    void unfollowedLadder_staysQuiet() {
        Season first = season(1, START, END);

        announcer.seasonChanged(new SeasonChange.NoticeDue(first, Set.of("7d")));
        announcer.seasonChanged(new SeasonChange.Started(first.close(), season(2, END, END.plusSeconds(60))));

        verify(locale, never()).send(anyString(), anyMap());
        verify(locale, never()).send(anyString());
        verify(displays).refreshAll();
    }

    @Test
    @DisplayName("a due notice tells players how long the season has left, rounded up to the minute")
    void notice_announcesRemainingTime() {
        announcer.follow(DUEL);
        clock.set(END.minus(Duration.ofHours(1)).plusSeconds(20));

        announcer.seasonChanged(new SeasonChange.NoticeDue(season(1, START, END), Set.of("24h", "1h")));

        assertThat(sent("season-ending-soon"))
                .containsEntry("ladder", "Duel")
                .containsEntry("number", 1)
                .containsEntry("remaining", "1h");
    }

    @Test
    @DisplayName("a rollover announces the new season and how ratings were reset")
    void rollover_announcesNewSeason() {
        announcer.follow(DUEL);

        announcer.seasonChanged(new SeasonChange.Started(season(1, START, END).close(),
                season(2, END, END.plus(Duration.ofDays(90)))));

        assertThat(sent("season-started"))
                .containsEntry("ladder", "Duel")
                .containsEntry("previous", 1)
                .containsEntry("number", 2);
        verify(locale).send("season-reset-soft");
        verify(displays).refreshAll();
    }

    @Test
    @DisplayName("a joining player is reminded only once the first notice threshold has been reached")
    void greet_remindsWhenEndingSoon() {
        announcer.follow(DUEL);
        resolver.update(List.of(season(1, START, END)));

        clock.set(END.minus(Duration.ofDays(8)));
        announcer.greet(player);
        verify(locale, never()).send(anyString(), anyMap());

        clock.set(END.minus(Duration.ofDays(2)).minus(Duration.ofHours(3)));
        announcer.greet(player);
        assertThat(sent("season-ending-soon")).containsEntry("remaining", "2d 3h");
    }

    @Test
    @DisplayName("a season that is already closing is not advertised as ending soon")
    void greet_ignoresClosingSeason() {
        announcer.follow(DUEL);
        resolver.update(List.of(season(1, START, END).close()));
        clock.set(END.plusSeconds(30));

        announcer.greet(player);

        verify(locale, never()).send(anyString(), anyMap());
    }
}
