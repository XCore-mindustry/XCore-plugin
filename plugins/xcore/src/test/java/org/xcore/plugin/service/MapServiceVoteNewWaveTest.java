package org.xcore.plugin.service;

import arc.Application;
import arc.Core;
import mindustry.Vars;
import mindustry.core.Logic;
import mindustry.core.GameState;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.enums.Feature;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteNewWave;
import org.xcore.plugin.vote.VoteNewWaveFactory;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapServiceVoteNewWaveTest {

    private Logic originalLogic;
    private GameState originalState;

    @BeforeEach
    void setUp() {
        Core.app = mock(Application.class);
        originalLogic = Vars.logic;
        originalState = Vars.state;
        Vars.logic = mock(Logic.class);
        Vars.state = new GameState();
    }

    @AfterEach
    void tearDown() {
        Vars.logic = originalLogic;
        Vars.state = originalState;
        Core.app = null;
    }

    @Test
    @DisplayName("startNewWaveSession blocks when waves are disabled")
    void startNewWaveSessionBlocksWhenWavesDisabled() {
        SessionService sessionService = mock(SessionService.class);
        Session session = mock(Session.class);
        Localization localization = mock(Localization.class);
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("player-1");
        when(sessionService.get("player-1")).thenReturn(session);
        when(session.locale()).thenReturn(localization);

        MapService service = new MapService(
                mock(EventDataRepository.class),
                mock(MapDataRepository.class),
                sessionService,
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                mock(GameStateService.class)
        );

        Vars.state.rules.waves = false;

        service.startNewWaveSession(player, false);

        verify(localization).send(eq("error-wave-vote-unavailable"));
    }

    @Test
    @DisplayName("startNewWaveSession blocks when feature is disabled")
    void startNewWaveSessionBlocksWhenFeatureDisabled() {
        SessionService sessionService = mock(SessionService.class);
        Session session = mock(Session.class);
        Localization localization = mock(Localization.class);
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn("player-1");
        when(sessionService.get("player-1")).thenReturn(session);
        when(session.locale()).thenReturn(localization);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.runtime.disabledFeatures.add(Feature.VNW.key());

        MapService service = new MapService(
                mock(EventDataRepository.class),
                mock(MapDataRepository.class),
                sessionService,
                config,
                new TomlSecretsConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                mock(GameStateService.class)
        );

        Vars.state.rules.waves = true;

        service.startNewWaveSession(player, false);

        verify(localization).send(eq("error-feature-disabled"));
    }

    @Test
    @DisplayName("forced startNewWaveSession skips wave immediately")
    void forcedStartNewWaveSessionSkipsImmediately() {
        SessionService sessionService = mock(SessionService.class);
        Session session = mock(Session.class);
        Localization localization = mock(Localization.class);
        Player player = mock(Player.class);
        VoteService voteService = mock(VoteService.class);
        when(player.uuid()).thenReturn("player-1");
        when(player.coloredName()).thenReturn("[accent]Admin");
        when(sessionService.get("player-1")).thenReturn(session);
        when(session.locale()).thenReturn(localization);
        when(voteService.isVoting()).thenReturn(false);

        MapService service = new MapService(
                mock(EventDataRepository.class),
                mock(MapDataRepository.class),
                sessionService,
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                voteService,
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                mock(GameStateService.class)
        );

        Vars.state.rules.waves = true;

        service.startNewWaveSession(player, true);

        verify(Vars.logic).skipWave();
        verify(sessionService).broadcast(eq("notification-admin-wave-skip"), anyMap());
    }

    @Test
    @DisplayName("startNewWaveSession creates and starts vote when allowed")
    void startNewWaveSessionCreatesVoteWhenAllowed() {
        SessionService sessionService = mock(SessionService.class);
        Session session = mock(Session.class);
        Localization localization = mock(Localization.class);
        Player player = mock(Player.class);
        VoteService voteService = mock(VoteService.class);
        VoteNewWaveFactory voteFactory = mock(VoteNewWaveFactory.class);
        VoteNewWave vote = mock(VoteNewWave.class);
        when(player.uuid()).thenReturn("player-1");
        when(sessionService.get("player-1")).thenReturn(session);
        when(session.locale()).thenReturn(localization);
        when(voteFactory.create(7)).thenReturn(vote);

        MapService service = new MapService(
                mock(EventDataRepository.class),
                mock(MapDataRepository.class),
                sessionService,
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                voteService,
                voteFactory,
                mock(VoteRtvFactory.class),
                mock(GameStateService.class)
        );

        Vars.state.rules.waves = true;
        Vars.state.wave = 7;

        service.startNewWaveSession(player, false);

        verify(voteFactory).create(7);
        verify(voteService).startVote(vote);
        verify(vote).vote(player, 1);
        verify(Vars.logic, never()).skipWave();
    }
}
