package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;

@Singleton
public class EventEditorService {

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;
    private final PlayerDataRepository playerDataRepository;

    @Inject
    public EventEditorService(EventDataRepository eventDataRepository,
                              MapDataRepository mapDataRepository,
                              PlayerDataRepository playerDataRepository) {
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.playerDataRepository = playerDataRepository;
    }

    public EventData initializeDraft(Session session, MapData map) {
        EventData draft = session.getDraft(EventData.class);
        draft.author = session.data.id;
        if (map != null) {
            draft.map = map.id;
        }
        return draft;
    }

    public void resetName(EventData draft) {
        draft.name = new EventData().name;
    }

    public void updateName(EventData draft, String name) {
        draft.name = name;
    }

    public void updateDescription(EventData draft, String description) {
        draft.description = description;
    }

    public void toggleTemporary(EventData draft) {
        draft.isTemporary = !draft.isTemporary;
    }

    public void toggleMajor(EventData draft) {
        draft.isMajor = !draft.isMajor;
    }

    public void updatePlannedStartTime(EventData draft, String input) {
        updatePlannedStartTime(draft, parseTime(input));
    }

    public void updatePlannedEndTime(EventData draft, String input) {
        updatePlannedEndTime(draft, parseTime(input));
    }

    public void updatePlannedStartTime(EventData draft, long value) {
        draft.plannedStartTime = Math.max(0L, value);
    }

    public void updatePlannedEndTime(EventData draft, long value) {
        draft.plannedEndTime = Math.max(0L, value);
    }

    public boolean saveDraft(Session session) {
        EventData draft = session.getDraft(EventData.class);
        if (draft.map == null) {
            session.locale().send("error-no-map");
            return false;
        }
        eventDataRepository.save(draft);
        session.clearDraft(EventData.class);
        return true;
    }

    public void cancelDraft(Session session) {
        session.clearDraft(EventData.class);
    }

    public MapData selectMapForDraft(Session session, String name, String fileName, String author, String gameMode) {
        MapData data = mapDataRepository.findOrCreate(name, fileName, author, gameMode);
        EventData draft = session.getDraft(EventData.class);
        draft.map = data.id;
        return data;
    }

    public MapData findMapForDraft(EventData draft) {
        if (draft == null || draft.map == null) {
            return null;
        }
        return mapDataRepository.findById(draft.map);
    }

    public PlayerData findAuthorForDraft(EventData draft) {
        if (draft == null || draft.author == null) {
            return null;
        }
        return playerDataRepository.findById(draft.author);
    }

    public long parseTime(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        try {
            if (input.startsWith("+")) {
                long now = System.currentTimeMillis();
                String valueStr = input.substring(1, input.length() - 1);
                char unit = input.charAt(input.length() - 1);
                long value = Long.parseLong(valueStr);
                return now + switch (unit) {
                    case 'm' -> value * 60_000L;
                    case 'h' -> value * 3_600_000L;
                    case 'd' -> value * 86_400_000L;
                    default -> 0;
                };
            }
            return Long.parseLong(input);
        } catch (Exception e) {
            return 0;
        }
    }
}
