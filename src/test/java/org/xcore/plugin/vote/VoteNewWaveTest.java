package org.xcore.plugin.vote;

import arc.Application;
import arc.Core;
import mindustry.entities.EntityGroup;
import mindustry.Vars;
import mindustry.core.Logic;
import mindustry.core.GameState;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.session.SessionService;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoteNewWaveTest {

    private Logic originalLogic;
    private GameState originalState;
    private EntityGroup<Player> originalPlayers;

    @BeforeEach
    void setUp() {
        Core.app = mock(Application.class);
        originalLogic = Vars.logic;
        originalState = Vars.state;
        originalPlayers = Groups.player;
        Vars.logic = mock(Logic.class);
        Vars.state = new GameState();
        Groups.player = new EntityGroup<>(Player.class, false, false);
    }

    @AfterEach
    void tearDown() {
        Vars.logic = originalLogic;
        Vars.state = originalState;
        Groups.player = originalPlayers;
        Core.app = null;
    }

    @Test
    @DisplayName("success skips wave and ends active vote when source wave is current")
    void successSkipsWaveAndEndsVote() {
        SessionService sessionService = mock(SessionService.class);
        VoteService voteService = mock(VoteService.class);
        VoteNewWave vote = new VoteNewWave(5, testConfig(), sessionService, voteService);
        vote.end.cancel();
        Vars.state.wave = 5;

        vote.success();

        verify(voteService).endVote();
        verify(Vars.logic).skipWave();
        verify(sessionService).broadcast(eq("vnw-success"), anyMap());
    }

    @Test
    @DisplayName("success does not skip wave when current wave already advanced")
    void successDoesNotSkipWaveWhenObsolete() {
        SessionService sessionService = mock(SessionService.class);
        VoteService voteService = mock(VoteService.class);
        VoteNewWave vote = new VoteNewWave(5, testConfig(), sessionService, voteService);
        vote.end.cancel();
        Vars.state.wave = 6;

        vote.success();

        verify(voteService).endVote();
        verify(Vars.logic, never()).skipWave();
        verify(sessionService).broadcast(eq("vnw-obsolete"), anyMap());
    }

    @Test
    @DisplayName("vote broadcast includes the next wave number")
    void voteBroadcastIncludesTargetWave() {
        SessionService sessionService = mock(SessionService.class);
        VoteService voteService = mock(VoteService.class);
        VoteNewWave vote = new VoteNewWave(3, testConfig(), sessionService, voteService);
        vote.end.cancel();

        Player player = mock(Player.class);
        player.id = 123;
        when(player.coloredName()).thenReturn("[accent]Tester");
        Groups.player.add(player);

        vote.vote(player, 1);

        verify(sessionService).broadcast(eq("vnw-vote"), anyMap());
    }

    private static TomlSecretsConfig testConfig() {
        var config = new TomlSecretsConfig();
        config.moderation.votekick.voteDurationSeconds = 10_000.0f;
        return config;
    }
}
