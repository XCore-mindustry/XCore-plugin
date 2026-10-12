package org.xcore.plugin.service;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.EventEditorService.Draft;
import org.xcore.plugin.service.EventEditorService.Editor;
import org.xcore.plugin.service.EventEditorService.MapChoice;
import org.xcore.plugin.service.EventEditorService.Problem;
import org.xcore.plugin.service.EventEditorService.SaveResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventEditorServiceTest {

    private static final long START = 1_800_000_000_000L;
    private static final long HOUR = 3_600_000L;

    private EventDataRepository eventDataRepository;
    private MapDataRepository mapDataRepository;
    private EventEditorService service;
    private ObjectId author;
    private MapChoice storedMap;

    @BeforeEach
    void setUp() {
        eventDataRepository = mock(EventDataRepository.class);
        mapDataRepository = mock(MapDataRepository.class);
        service = new EventEditorService(eventDataRepository, mapDataRepository);
        author = new ObjectId();
        storedMap = new MapChoice(new ObjectId(), "Caldera", "caldera.msav", "ospx", "pvp");
        when(eventDataRepository.save(any(EventData.class))).thenReturn(true);
    }

    private Editor player() {
        return new Editor(author, false, false);
    }

    private Draft draft() {
        return new Draft(null, "Турнир", "Командный турнир", storedMap, false, false, 0L, 0L);
    }

    private EventData stored(ObjectId owner) {
        EventData event = new EventData();
        event.id = new ObjectId();
        event.author = owner;
        event.name = "Old";
        when(eventDataRepository.findById(event.id)).thenReturn(event);
        return event;
    }

    @Test
    @DisplayName("a draft keeps its texts within the lengths the fields allow")
    void draft_clampsTexts() {
        Draft draft = new Draft(null, "  " + "n".repeat(60) + "  ", "d".repeat(2000), null, false, false, -5L, -1L);

        assertThat(draft.name()).hasSize(EventEditorService.NAME_LENGTH);
        assertThat(draft.description()).hasSize(EventEditorService.DESCRIPTION_LENGTH);
        assertThat(draft.plannedStart()).isZero();
        assertThat(draft.plannedEnd()).isZero();
        assertThat(draft.isNew()).isTrue();
    }

    @Test
    @DisplayName("a stored event opens as a draft with its map and its times")
    void draft_ofStoredEvent() {
        EventData event = stored(author);
        event.isTemporary = true;
        event.isMajor = true;
        event.plannedStartTime = START;
        event.plannedEndTime = START + HOUR;
        MapData map = new MapData("Caldera", "caldera.msav", "ospx", "pvp");
        map.id = new ObjectId();
        event.map = map.id;

        Draft draft = Draft.of(event, map);

        assertThat(draft.isNew()).isFalse();
        assertThat(draft.id()).isEqualTo(event.id);
        assertThat(draft.map()).isEqualTo(new MapChoice(map.id, "Caldera", "caldera.msav", "ospx", "pvp"));
        assertThat(draft.temporary()).isTrue();
        assertThat(draft.major()).isTrue();
        assertThat(draft.plannedStart()).isEqualTo(START);
        assertThat(draft.plannedEnd()).isEqualTo(START + HOUR);
        // A map whose record is gone is still the event's map: saving must not lose it.
        assertThat(Draft.of(event, null).map().id()).isEqualTo(map.id);
    }

    @Test
    @DisplayName("an event needs a map, a temporary one an end, and the end comes after the start")
    void validate_namesWhatIsWrong() {
        assertThat(EventEditorService.validate(draft())).isNull();
        assertThat(EventEditorService.validate(Draft.blank(null))).isEqualTo(Problem.NO_MAP);
        assertThat(EventEditorService.validate(draft().withTemporary(true))).isEqualTo(Problem.NO_END);
        assertThat(EventEditorService.validate(draft().withTemporary(true).withPlannedEnd(START))).isNull();
        assertThat(EventEditorService.validate(draft().withPlannedStart(START).withPlannedEnd(START)))
                .isEqualTo(Problem.END_BEFORE_START);
        assertThat(EventEditorService.validate(draft().withPlannedStart(START).withPlannedEnd(START + HOUR))).isNull();
    }

    @Test
    @DisplayName("an event is changed by its author, or by whoever may change the events of others, until it starts")
    void canEdit_followsOwnershipAndState() {
        EventData event = stored(author);
        Editor stranger = new Editor(new ObjectId(), false, false);
        Editor staff = new Editor(new ObjectId(), false, true);

        assertThat(EventEditorService.canEdit(event, player())).isTrue();
        assertThat(EventEditorService.canEdit(event, stranger)).isFalse();
        assertThat(EventEditorService.canEdit(event, staff)).isTrue();
        assertThat(EventEditorService.canEdit(event, new Editor(null, false, false))).isFalse();

        event.isMajor = true;
        assertThat(EventEditorService.canEdit(event, player())).isFalse();
        assertThat(EventEditorService.canEdit(event, new Editor(author, true, false))).isTrue();

        event.isMajor = false;
        event.isActive = true;
        assertThat(EventEditorService.canEdit(event, staff)).isFalse();
        event.isActive = false;
        event.isFinished = true;
        assertThat(EventEditorService.canEdit(event, player())).isFalse();
    }

    @Test
    @DisplayName("a new event is stored as its author's, with everything the draft holds")
    void save_newEvent() {
        Draft draft = draft().withTemporary(true).withPlannedStart(START).withPlannedEnd(START + HOUR);

        SaveResult result = service.save(draft, player(), "Fallback");

        assertThat(result.saved()).isTrue();
        EventData event = result.event();
        assertThat(event.author).isEqualTo(author);
        assertThat(event.name).isEqualTo("Турнир");
        assertThat(event.description).isEqualTo("Командный турнир");
        assertThat(event.map).isEqualTo(storedMap.id());
        assertThat(event.isTemporary).isTrue();
        assertThat(event.isMajor).isFalse();
        assertThat(event.plannedStartTime).isEqualTo(START);
        assertThat(event.plannedEndTime).isEqualTo(START + HOUR);
        verify(eventDataRepository).save(event);
        verify(mapDataRepository, never()).findOrCreate(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("an event without a name gets the fallback one, cut to the length of a name")
    void save_usesFallbackName() {
        SaveResult result = service.save(draft().withTexts("   ", ""), player(), "Событие игрока " + "x".repeat(40));

        assertThat(result.saved()).isTrue();
        assertThat(result.event().name).startsWith("Событие игрока").hasSize(EventEditorService.NAME_LENGTH);
        assertThat(result.event().description).isEmpty();
    }

    @Test
    @DisplayName("a map picked from the server's files gets its record when the event is saved")
    void save_storesPickedMap() {
        MapData created = new MapData("Caldera", "caldera.msav", "ospx", "pvp");
        created.id = new ObjectId();
        when(mapDataRepository.findOrCreate("Caldera", "caldera.msav", "ospx", "pvp")).thenReturn(created);

        SaveResult result = service.save(draft().withMap(new MapChoice(null, "Caldera", "caldera.msav", "ospx", "pvp")),
                player(), "Fallback");

        assertThat(result.saved()).isTrue();
        assertThat(result.event().map).isEqualTo(created.id);
    }

    @Test
    @DisplayName("a draft that cannot be saved is not stored, and the result says why")
    void save_refusesInvalidDraft() {
        assertThat(service.save(Draft.blank(null), player(), "Fallback").problem()).isEqualTo(Problem.NO_MAP);
        assertThat(service.save(draft().withMajor(true), player(), "Fallback").problem()).isEqualTo(Problem.NOT_ALLOWED);

        verify(eventDataRepository, never()).save(any(EventData.class));
    }

    @Test
    @DisplayName("whoever may make major events saves one")
    void save_majorEvent() {
        SaveResult result = service.save(draft().withMajor(true), new Editor(author, true, false), "Fallback");

        assertThat(result.saved()).isTrue();
        assertThat(result.event().isMajor).isTrue();
    }

    @Test
    @DisplayName("a change is written over the stored event, whose author stays")
    void save_existingEvent() {
        EventData event = stored(author);
        Draft draft = Draft.of(event, null).withTexts("New name", "New text").withMap(storedMap);

        SaveResult result = service.save(draft, new Editor(new ObjectId(), false, true), "Fallback");

        assertThat(result.saved()).isTrue();
        assertThat(result.event()).isSameAs(event);
        assertThat(event.name).isEqualTo("New name");
        assertThat(event.description).isEqualTo("New text");
        assertThat(event.author).isEqualTo(author);
        assertThat(event.map).isEqualTo(storedMap.id());
    }

    @Test
    @DisplayName("the stored event is read again: one that has started, or is gone, is not changed")
    void save_rechecksStoredEvent() {
        EventData event = stored(author);
        Draft draft = Draft.of(event, null).withMap(storedMap);

        event.isActive = true;
        assertThat(service.save(draft, player(), "Fallback").problem()).isEqualTo(Problem.NOT_ALLOWED);

        event.isActive = false;
        assertThat(service.save(draft, new Editor(new ObjectId(), false, false), "Fallback").problem())
                .isEqualTo(Problem.NOT_ALLOWED);

        when(eventDataRepository.findById(event.id)).thenReturn(null);
        assertThat(service.save(draft, player(), "Fallback").problem()).isEqualTo(Problem.GONE);

        verify(eventDataRepository, never()).save(any(EventData.class));
    }

    @Test
    @DisplayName("a save storage refused is reported as failed")
    void save_reportsFailure() {
        when(eventDataRepository.save(any(EventData.class))).thenReturn(false);
        assertThat(service.save(draft(), player(), "Fallback").problem()).isEqualTo(Problem.FAILED);

        when(mapDataRepository.findOrCreate(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        assertThat(service.save(draft().withMap(new MapChoice(null, "A", "a.msav", "b", "pvp")), player(), "Fallback")
                .problem()).isEqualTo(Problem.FAILED);
    }
}
