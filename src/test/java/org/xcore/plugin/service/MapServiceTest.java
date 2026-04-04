package org.xcore.plugin.service;

import arc.files.Fi;
import arc.struct.StringMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteNewWaveFactory;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapServiceTest {

    private Maps originalMaps;

    @AfterEach
    void tearDown() {
        Vars.maps = originalMaps;
    }

    @Test
    @DisplayName("resolveNextMap returns active event map even when same map repeats")
    void resolveNextMapReturnsActiveEventMap() {
        originalMaps = Vars.maps;

        Maps maps = mock(Maps.class);
        Vars.maps = maps;

        Map eventMap = new Map(
                new Fi("event-map.msav"),
                100,
                100,
                StringMap.of("name", "Event Map", "author", "Author"),
                true
        );

        when(maps.customMaps()).thenReturn(Seq.with(eventMap));

        EventDataRepository eventDataRepository = mock(EventDataRepository.class);
        MapDataRepository mapDataRepository = mock(MapDataRepository.class);
        MapData eventMapData = new MapData("Event Map", "event-map.msav", "Author", "pvp");
        EventData eventData = new EventData("Event", new ObjectId(), new ObjectId());
        eventData.id = new ObjectId();
        eventData.isActive = true;

        when(eventDataRepository.findActive()).thenReturn(Optional.of(eventData));
        when(mapDataRepository.findById(eventData.map)).thenReturn(eventMapData);

        Config config = new Config();
        config.server = "event";

        MapService service = new MapService(
                eventDataRepository,
                mapDataRepository,
                mock(SessionService.class),
                config,
                new GlobalConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                mock(GameStateService.class)
        );

        Map resolved = service.resolveNextMap(Gamemode.pvp, eventMap);

        assertThat(resolved).isSameAs(eventMap);
        verify(maps, never()).getNextMap(Gamemode.pvp, eventMap);
    }

    @Test
    @DisplayName("resolveNextMap falls back to Mindustry map rotation outside events")
    void resolveNextMapFallsBackToRotation() {
        originalMaps = Vars.maps;

        Maps maps = mock(Maps.class);
        Vars.maps = maps;

        Map previous = mock(Map.class);
        Map next = mock(Map.class);
        when(maps.getNextMap(Gamemode.pvp, previous)).thenReturn(next);

        Config config = new Config();
        config.server = "mini-pvp";

        MapService service = new MapService(
                mock(EventDataRepository.class),
                mock(MapDataRepository.class),
                mock(SessionService.class),
                config,
                new GlobalConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                mock(GameStateService.class)
        );

        Map resolved = service.resolveNextMap(Gamemode.pvp, previous);

        assertThat(resolved).isSameAs(next);
        verify(maps).getNextMap(Gamemode.pvp, previous);
    }

    @Test
    @DisplayName("findPersistedMap prefers exact persisted identity over fuzzy name matching")
    void findPersistedMapPrefersExactPersistedIdentity() {
        originalMaps = Vars.maps;

        Maps maps = mock(Maps.class);
        Vars.maps = maps;

        Map wrongSubstringMatch = new Map(
                new Fi("tower-defense.msav"),
                100,
                100,
                StringMap.of("name", "Tower Defense", "author", "Wrong Author"),
                true
        );
        Map exactPersistedMap = new Map(
                new Fi("tower.msav"),
                120,
                120,
                StringMap.of("name", "Tower", "author", "Correct Author"),
                true
        );

        when(maps.customMaps()).thenReturn(Seq.with(wrongSubstringMatch, exactPersistedMap));

        MapService service = new MapService(
                mock(EventDataRepository.class),
                mock(MapDataRepository.class),
                mock(SessionService.class),
                new Config(),
                new GlobalConfig(),
                mock(VoteService.class),
                mock(VoteNewWaveFactory.class),
                mock(VoteRtvFactory.class),
                mock(GameStateService.class)
        );

        MapData persisted = new MapData("Tower", "tower.msav", "Correct Author", "pvp");

        assertThat(service.findMap("Tower")).isSameAs(wrongSubstringMatch);
        assertThat(service.findPersistedMap(persisted)).isSameAs(exactPersistedMap);
    }
}
