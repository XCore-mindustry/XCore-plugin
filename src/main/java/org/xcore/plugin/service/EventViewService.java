package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.types.ObjectId;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;

import java.util.List;
import java.util.Map;

@Singleton
public class EventViewService {

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;
    private final PlayerDataRepository playerDataRepository;

    @Inject
    public EventViewService(EventDataRepository eventDataRepository,
                            MapDataRepository mapDataRepository,
                            PlayerDataRepository playerDataRepository) {
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.playerDataRepository = playerDataRepository;
    }

    public EventData activeEvent() {
        return eventDataRepository.findActive().orElse(null);
    }

    public EventDetails details(EventData event) {
        if (event == null) {
            return new EventDetails(null, null, null);
        }
        return new EventDetails(event, findMap(event.map), findAuthor(event.author));
    }

    public EventData findById(ObjectId id) {
        return eventDataRepository.findById(id);
    }

    public EventPage page(int requestedPage, int perPage, Map<String, StatusEnum> filters) {
        int total = (int) eventDataRepository.count(filters);
        var pagination = CustomGatherers.calculatePagination(total, perPage);
        if (total == 0) {
            return new EventPage(List.of(), total, 1, pagination.totalPages());
        }
        int validPage = pagination.clampPage(requestedPage);
        int skip = (validPage - 1) * perPage;
        List<EventData> events = eventDataRepository.findPage(skip, perPage, filters);
        return new EventPage(events, total, validPage, pagination.totalPages());
    }

    private MapData findMap(ObjectId mapId) {
        if (mapId == null) {
            return null;
        }
        return mapDataRepository.findById(mapId);
    }

    private PlayerData findAuthor(ObjectId authorId) {
        if (authorId == null) {
            return null;
        }
        return playerDataRepository.findById(authorId);
    }

    public record EventDetails(EventData event, MapData map, PlayerData author) {}

    public record EventPage(List<EventData> events, int total, int page, int totalPages) {
        public boolean isEmpty() {
            return total == 0;
        }

        public boolean hasPrevious() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < totalPages;
        }
    }
}
