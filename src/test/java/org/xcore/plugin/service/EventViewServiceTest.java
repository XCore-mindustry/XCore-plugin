package org.xcore.plugin.service;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventViewServiceTest {

    private EventDataRepository eventDataRepository;
    private MapDataRepository mapDataRepository;
    private PlayerDataRepository playerDataRepository;
    private EventViewService eventViewService;

    @BeforeEach
    void setUp() {
        eventDataRepository = mock(EventDataRepository.class);
        mapDataRepository = mock(MapDataRepository.class);
        playerDataRepository = mock(PlayerDataRepository.class);
        eventViewService = new EventViewService(eventDataRepository, mapDataRepository, playerDataRepository);
    }

    @Test
    @DisplayName("activeEvent returns active event or null")
    void activeEvent_returnsActiveOrNull() {
        EventData active = new EventData();
        when(eventDataRepository.findActive()).thenReturn(Optional.of(active), Optional.empty());

        assertThat(eventViewService.activeEvent()).isEqualTo(active);
        assertThat(eventViewService.activeEvent()).isNull();
    }

    @Test
    @DisplayName("details resolves map and author")
    void details_resolvesMapAndAuthor() {
        ObjectId mapId = new ObjectId();
        ObjectId authorId = new ObjectId();
        EventData event = new EventData();
        event.map = mapId;
        event.author = authorId;
        MapData map = new MapData();
        PlayerData author = new PlayerData("author-uuid", true);
        when(mapDataRepository.findById(mapId)).thenReturn(map);
        when(playerDataRepository.findById(authorId)).thenReturn(author);

        EventViewService.EventDetails details = eventViewService.details(event);

        assertThat(details.event()).isEqualTo(event);
        assertThat(details.map()).isEqualTo(map);
        assertThat(details.author()).isEqualTo(author);
    }

    @Test
    @DisplayName("details tolerates null event and missing references")
    void details_toleratesNulls() {
        EventData event = new EventData();

        assertThat(eventViewService.details(null).event()).isNull();
        EventViewService.EventDetails details = eventViewService.details(event);
        assertThat(details.event()).isEqualTo(event);
        assertThat(details.map()).isNull();
        assertThat(details.author()).isNull();
    }

    @Test
    @DisplayName("page clamps requested page and loads repository slice")
    void page_clampsAndLoadsSlice() {
        Map<String, StatusEnum> filters = Map.of("active", StatusEnum.Active);
        EventData event = new EventData();
        when(eventDataRepository.count(filters)).thenReturn(25L);
        when(eventDataRepository.findPage(20, 10, filters)).thenReturn(List.of(event));

        EventViewService.EventPage page = eventViewService.page(5, 10, filters);

        assertThat(page.events()).containsExactly(event);
        assertThat(page.total()).isEqualTo(25);
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("empty page returns page one and skips slice query")
    void page_emptySkipsFindPage() {
        when(eventDataRepository.count(isNull(Map.class))).thenReturn(0L);

        EventViewService.EventPage page = eventViewService.page(1, 10, null);

        assertThat(page.events()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalPages()).isZero();
        assertThat(page.isEmpty()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isFalse();
        verify(eventDataRepository, never()).findPage(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any());
    }
}
