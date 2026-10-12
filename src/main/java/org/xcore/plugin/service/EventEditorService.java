package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;

/**
 * Writes events: what the editor of the events menu holds while a player fills it in, the rules
 * an event has to meet, and the save itself.
 */
@Singleton
public class EventEditorService {

    public static final int NAME_LENGTH = 24;
    public static final int DESCRIPTION_LENGTH = 1000;

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;

    @Inject
    public EventEditorService(EventDataRepository eventDataRepository, MapDataRepository mapDataRepository) {
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
    }

    /**
     * The map of a draft.
     *
     * @param id the stored map; {@code null} for one picked from the server's files, which is
     *           looked up, and stored if it is new, only when the event is saved
     */
    public record MapChoice(@Nullable ObjectId id, String name, String fileName, String author, String gameMode) {
        public static MapChoice of(MapData map) {
            return new MapChoice(map.id, map.name, map.fileName, map.author, map.gameMode);
        }
    }

    /**
     * An event as it is being written.
     *
     * @param id           the event being changed; {@code null} for a new one
     * @param plannedStart epoch millis, 0 for none
     * @param plannedEnd   epoch millis, 0 for none
     */
    public record Draft(@Nullable ObjectId id, String name, String description, @Nullable MapChoice map,
                        boolean temporary, boolean major, long plannedStart, long plannedEnd) {

        public Draft {
            name = clamp(name, NAME_LENGTH);
            description = clamp(description, DESCRIPTION_LENGTH);
            plannedStart = Math.max(0L, plannedStart);
            plannedEnd = Math.max(0L, plannedEnd);
        }

        /** A new event with nothing filled in. */
        public static Draft blank(@Nullable MapChoice map) {
            return new Draft(null, "", "", map, false, false, 0L, 0L);
        }

        /** The stored event as the editor opens it. */
        public static Draft of(EventData event, @Nullable MapData map) {
            MapChoice choice = map != null ? MapChoice.of(map)
                    : event.map != null ? new MapChoice(event.map, "", "", "", "") : null;
            return new Draft(event.id, event.name, event.description, choice,
                    event.isTemporary, event.isMajor, event.plannedStartTime, event.plannedEndTime);
        }

        public boolean isNew() {
            return id == null;
        }

        public Draft withTexts(String name, String description) {
            return new Draft(id, name, description, map, temporary, major, plannedStart, plannedEnd);
        }

        public Draft withMap(MapChoice map) {
            return new Draft(id, name, description, map, temporary, major, plannedStart, plannedEnd);
        }

        public Draft withTemporary(boolean temporary) {
            return new Draft(id, name, description, map, temporary, major, plannedStart, plannedEnd);
        }

        public Draft withMajor(boolean major) {
            return new Draft(id, name, description, map, temporary, major, plannedStart, plannedEnd);
        }

        public Draft withPlannedStart(long plannedStart) {
            return new Draft(id, name, description, map, temporary, major, plannedStart, plannedEnd);
        }

        public Draft withPlannedEnd(long plannedEnd) {
            return new Draft(id, name, description, map, temporary, major, plannedStart, plannedEnd);
        }

    }

    private static String clamp(@Nullable String text, int length) {
        String trimmed = text == null ? "" : text.strip();
        return trimmed.length() <= length ? trimmed : trimmed.substring(0, length);
    }

    /**
     * Who is writing the event, as far as the rules ask.
     *
     * @param id          the player's own id, {@code null} for one not stored yet
     * @param createMajor may make an event major, and change a major one
     * @param editOthers  may change an event of another author
     */
    public record Editor(@Nullable ObjectId id, boolean createMajor, boolean editOthers) {
    }

    /** Why an event was not saved. */
    public enum Problem {
        NO_MAP("event-ui-error-no-map"),
        NO_END("event-ui-error-no-end"),
        END_BEFORE_START("event-ui-error-end-before-start"),
        NOT_ALLOWED("event-ui-error-not-allowed"),
        GONE("event-ui-error-gone"),
        FAILED("event-ui-error-save-failed");

        private final String key;

        Problem(String key) {
            this.key = key;
        }

        /** The text that tells the player. */
        public String key() {
            return key;
        }
    }

    /** @param event the event as stored, {@code null} when {@code problem} is not */
    public record SaveResult(@Nullable EventData event, @Nullable Problem problem) {
        public boolean saved() {
            return problem == null;
        }
    }

    /**
     * An event can be changed until it starts: by its author, or by whoever may change the
     * events of others. A major one takes the right to make major events as well.
     */
    public static boolean canEdit(EventData event, Editor editor) {
        boolean owner = editor.id() != null && editor.id().equals(event.author);
        return !event.isFinished && !event.isActive
                && (!event.isMajor || editor.createMajor())
                && (owner || editor.editOthers());
    }

    /** What is wrong with {@code draft} as it stands; {@code null} when it can be saved. */
    public static @Nullable Problem validate(Draft draft) {
        if (draft.map() == null) {
            return Problem.NO_MAP;
        }
        // A temporary event is one that ends by itself, and it can only do so at a set time.
        if (draft.temporary() && draft.plannedEnd() <= 0) {
            return Problem.NO_END;
        }
        return endsBeforeStart(draft) ? Problem.END_BEFORE_START : null;
    }

    /** Whether both times of {@code draft} are set and the end does not come after the start. */
    public static boolean endsBeforeStart(Draft draft) {
        return draft.plannedStart() > 0 && draft.plannedEnd() > 0 && draft.plannedEnd() <= draft.plannedStart();
    }

    /**
     * Blocking. Stores {@code draft} as a new event of {@code editor}, or over the event it was
     * opened from. The stored event is read again first: it may have started, or been changed,
     * since the editor opened, and what the editor may do is decided by how it is now.
     *
     * @param fallbackName the name of an event whose name was left empty
     */
    public SaveResult save(Draft draft, Editor editor, String fallbackName) {
        Problem problem = validate(draft);
        if (problem != null) {
            return new SaveResult(null, problem);
        }
        if (draft.major() && !editor.createMajor()) {
            return new SaveResult(null, Problem.NOT_ALLOWED);
        }

        EventData event;
        if (draft.isNew()) {
            event = new EventData();
            event.author = editor.id();
        } else {
            event = eventDataRepository.findById(draft.id());
            if (event == null) {
                return new SaveResult(null, Problem.GONE);
            }
            if (!canEdit(event, editor)) {
                return new SaveResult(null, Problem.NOT_ALLOWED);
            }
        }

        MapChoice map = draft.map();
        ObjectId mapId = map.id();
        if (mapId == null) {
            MapData stored = mapDataRepository.findOrCreate(map.name(), map.fileName(), map.author(), map.gameMode());
            mapId = stored == null ? null : stored.id;
        }
        if (mapId == null) {
            return new SaveResult(null, Problem.FAILED);
        }

        event.name = draft.name().isBlank() ? clamp(fallbackName, NAME_LENGTH) : draft.name();
        event.description = draft.description();
        event.map = mapId;
        event.isTemporary = draft.temporary();
        event.isMajor = draft.major();
        event.plannedStartTime = draft.plannedStart();
        event.plannedEndTime = draft.plannedEnd();

        return eventDataRepository.save(event)
                ? new SaveResult(event, null)
                : new SaveResult(null, Problem.FAILED);
    }
}
