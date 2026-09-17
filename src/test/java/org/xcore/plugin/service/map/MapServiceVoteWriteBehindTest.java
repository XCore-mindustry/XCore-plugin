package org.xcore.plugin.service.map;

import com.ospx.flubundle.Bundle;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.GameStateService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteNewWaveFactory;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Vote path: shared state mutates immediately, MongoDB writes run behind. */
class MapServiceVoteWriteBehindTest {
    @Test
    @SuppressWarnings("unchecked")
    void handleReputationMutatesSharedStateAndPersistsAsynchronously() {
        var sessionService = mock(SessionService.class);
        var mapRepository = mock(MapDataRepository.class);
        var playerRepository = mock(PlayerDataRepository.class);

        var session = new Session(new TomlSecretsConfig(), mock(Bundle.class),
                null, playerRepository, null, new PlayerData("voter", true));
        session.data.mapVotes = new HashMap<>();
        session.localization = mock(org.xcore.plugin.localization.Localization.class);
        when(sessionService.get(anyString())).thenReturn(session);
        doAnswer(inv -> {
            session.data.mapVotes.put(inv.getArgument(1), inv.getArgument(2));
            return true;
        }).when(sessionService).putMapVote(any(), any(), anyBoolean());

        var map = new MapData("Arena", "arena.msav", "Author", "survival");
        map.id = new ObjectId();
        when(mapRepository.isReadOnly()).thenReturn(false);
        var persisted = new CompletableFuture<Boolean>();
        when(mapRepository.applyVoteAsync(eq(map.id), anyInt(), anyDouble(), anyInt(), anyInt())).thenReturn(persisted);

        var service = new MapService(
                mock(org.xcore.plugin.database.repository.EventDataRepository.class),
                mapRepository,
                sessionService,
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                new GameStateService()
        );

        service.handleReputation(mindustry.gen.Player.create(), true, map);

        // Shared state is updated immediately on the tick thread...
        assertThat(map.like).isEqualTo(1);
        assertThat(map.reputation).isPositive();
        assertThat(session.data.mapVotes).containsEntry(map.id.toString(), true);
        // ...while persistence is dispatched reactively.
        verify(mapRepository, never()).applyVote(any(), anyInt(), anyDouble(), anyInt(), anyInt());
        verify(mapRepository).applyVoteAsync(eq(map.id), anyInt(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void handleReputationDoesNotThrowWhenWriteBehindFails() {
        var sessionService = mock(SessionService.class);
        var mapRepository = mock(MapDataRepository.class);
        var playerRepository = mock(PlayerDataRepository.class);

        var session = new Session(new TomlSecretsConfig(), mock(Bundle.class),
                null, playerRepository, null, new PlayerData("voter", true));
        session.data.mapVotes = new HashMap<>();
        session.localization = mock(org.xcore.plugin.localization.Localization.class);
        when(sessionService.get(anyString())).thenReturn(session);

        var map = new MapData("Arena", "arena.msav", "Author", "survival");
        map.id = new ObjectId();
        when(mapRepository.applyVoteAsync(eq(map.id), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("mongo timeout")));

        var service = new MapService(
                mock(org.xcore.plugin.database.repository.EventDataRepository.class),
                mapRepository,
                sessionService,
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                new GameStateService()
        );

        // Should complete without throwing exception
        service.handleReputation(mindustry.gen.Player.create(), true, map);

        verify(mapRepository).applyVoteAsync(eq(map.id), anyInt(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void handleReputationByPlayerResolvesMapAsynchronouslyWithoutBlockingTick() {
        var sessionService = mock(SessionService.class);
        var mapRepository = mock(MapDataRepository.class);
        var playerRepository = mock(PlayerDataRepository.class);

        var session = new Session(new TomlSecretsConfig(), mock(Bundle.class),
                null, playerRepository, null, new PlayerData("voter", true));
        session.data.mapVotes = new HashMap<>();
        session.localization = mock(org.xcore.plugin.localization.Localization.class);
        when(sessionService.get(anyString())).thenReturn(session);

        var existingData = new MapData("Arena", "arena.msav", "Author", "survival");
        existingData.id = new ObjectId();
        when(mapRepository.findExistingAsync(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(existingData));
        when(mapRepository.applyVoteAsync(eq(existingData.id), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(true));

        var service = new MapService(
                mock(org.xcore.plugin.database.repository.EventDataRepository.class),
                mapRepository,
                sessionService,
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                new GameStateService()
        );

        var oldState = mindustry.Vars.state;
        mindustry.Vars.state = new mindustry.core.GameState();
        var map = new mindustry.maps.Map(new arc.files.Fi("arena.msav"), 10, 10,
                arc.struct.StringMap.of("name", "Arena", "author", "Author"), true);
        mindustry.Vars.state.map = map;
        var rules = new mindustry.game.Rules();
        mindustry.Vars.state.rules = rules;

        try {
            service.handleReputation(mindustry.gen.Player.create(), true);

            // Verified: findOrCreate is NEVER invoked synchronously on tick!
            verify(mapRepository, never()).findOrCreate(anyString(), anyString(), anyString(), anyString());
            verify(mapRepository).findExistingAsync(eq("Arena"), eq("arena.msav"), eq("Author"), anyString());
        } finally {
            mindustry.Vars.state = oldState;
        }
    }

    @Test
    void handleReputationWithRevocationDecreasesStatsAndRemovesVoteFromSession() {
        var sessionService = mock(SessionService.class);
        var mapRepository = mock(MapDataRepository.class);
        var playerRepository = mock(PlayerDataRepository.class);

        var session = new Session(new TomlSecretsConfig(), mock(Bundle.class),
                null, playerRepository, null, new PlayerData("voter", true));
        session.data.mapVotes = new HashMap<>();
        session.localization = mock(org.xcore.plugin.localization.Localization.class);
        when(sessionService.get(anyString())).thenReturn(session);

        var map = new MapData("Arena", "arena.msav", "Author", "survival");
        map.id = new ObjectId();
        map.like = 5;
        map.reputation = 10;
        session.data.mapVotes.put(map.id.toString(), true);

        when(mapRepository.applyVoteAsync(eq(map.id), eq(-1), anyDouble(), eq(-1), eq(0)))
                .thenReturn(CompletableFuture.completedFuture(true));

        var service = new MapService(
                mock(org.xcore.plugin.database.repository.EventDataRepository.class),
                mapRepository,
                sessionService,
                new TomlXcoreConfig(),
                new TomlSecretsConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                new GameStateService()
        );

        service.handleReputation(mindustry.gen.Player.create(), true, true, map);

        assertThat(map.like).isEqualTo(4);
        assertThat(map.reputation).isEqualTo(9);
        assertThat(session.data.mapVotes).doesNotContainKey(map.id.toString());
        verify(mapRepository).applyVoteAsync(eq(map.id), eq(-1), anyDouble(), eq(-1), eq(0));
    }
}
