package org.xcore.plugin.service;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MindustryMenuGateway;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventEditorServiceTest {

    private EventDataRepository eventDataRepository;
    private MapDataRepository mapDataRepository;
    private PlayerDataRepository playerDataRepository;
    private EventEditorService eventEditorService;
    private Session session;

    @BeforeEach
    void setUp() {
        eventDataRepository = mock(EventDataRepository.class);
        mapDataRepository = mock(MapDataRepository.class);
        playerDataRepository = mock(PlayerDataRepository.class);
        eventEditorService = new EventEditorService(eventDataRepository, mapDataRepository, playerDataRepository);

        session = createSession();
    }

    @Test
    @DisplayName("initializeDraft sets author and optional map")
    void initializeDraft_setsAuthorAndMap() {
        MapData map = new MapData();
        map.id = new ObjectId();

        EventData draft = eventEditorService.initializeDraft(session, map);

        assertThat(draft.author).isEqualTo(session.data.id);
        assertThat(draft.map).isEqualTo(map.id);
    }

    @Test
    @DisplayName("initializeDraft sets author only when map is null")
    void initializeDraft_setsAuthorOnlyWhenMapNull() {
        EventData draft = eventEditorService.initializeDraft(session, null);

        assertThat(draft.author).isEqualTo(session.data.id);
        assertThat(draft.map).isNull();
    }

    @Test
    @DisplayName("resetName resets draft name to default")
    void resetName_resetsToDefault() {
        EventData draft = new EventData();
        draft.name = "Custom";
        eventEditorService.resetName(draft);
        assertThat(draft.name).isEqualTo(new EventData().name);
    }

    @Test
    @DisplayName("updateName mutates draft name")
    void updateName_mutatesDraftName() {
        EventData draft = new EventData();
        eventEditorService.updateName(draft, "New Name");
        assertThat(draft.name).isEqualTo("New Name");
    }

    @Test
    @DisplayName("updateDescription mutates draft description")
    void updateDescription_mutatesDraftDescription() {
        EventData draft = new EventData();
        eventEditorService.updateDescription(draft, "Desc");
        assertThat(draft.description).isEqualTo("Desc");
    }

    @Test
    @DisplayName("toggleTemporary flips draft flag")
    void toggleTemporary_flipsFlag() {
        EventData draft = new EventData();
        assertThat(draft.isTemporary).isFalse();
        eventEditorService.toggleTemporary(draft);
        assertThat(draft.isTemporary).isTrue();
        eventEditorService.toggleTemporary(draft);
        assertThat(draft.isTemporary).isFalse();
    }

    @Test
    @DisplayName("toggleMajor flips draft flag")
    void toggleMajor_flipsFlag() {
        EventData draft = new EventData();
        assertThat(draft.isMajor).isFalse();
        eventEditorService.toggleMajor(draft);
        assertThat(draft.isMajor).isTrue();
        eventEditorService.toggleMajor(draft);
        assertThat(draft.isMajor).isFalse();
    }

    @Test
    @DisplayName("updatePlannedStartTime parses absolute timestamp")
    void updatePlannedStartTime_parsesAbsolute() {
        EventData draft = new EventData();
        eventEditorService.updatePlannedStartTime(draft, "12345");
        assertThat(draft.plannedStartTime).isEqualTo(12345L);
    }

    @Test
    @DisplayName("updatePlannedEndTime parses invalid input as zero")
    void updatePlannedEndTime_parsesInvalidAsZero() {
        EventData draft = new EventData();
        eventEditorService.updatePlannedEndTime(draft, "bad");
        assertThat(draft.plannedEndTime).isEqualTo(0L);
    }

    @Test
    @DisplayName("parseTime handles empty and null as zero")
    void parseTime_emptyAndNullAsZero() {
        assertThat(eventEditorService.parseTime("")).isEqualTo(0L);
        assertThat(eventEditorService.parseTime(null)).isEqualTo(0L);
    }

    @Test
    @DisplayName("parseTime handles relative offsets")
    void parseTime_relativeOffsets() {
        long before = System.currentTimeMillis();
        long result = eventEditorService.parseTime("+5m");
        long after = System.currentTimeMillis();
        assertThat(result).isGreaterThanOrEqualTo(before + 5 * 60_000L);
        assertThat(result).isLessThanOrEqualTo(after + 5 * 60_000L);
    }

    @Test
    @DisplayName("saveDraft without map sends error and returns false without saving")
    void saveDraft_withoutMap_sendsError() {
        EventData draft = session.getDraft(EventData.class);
        draft.map = null;

        boolean result = eventEditorService.saveDraft(session);

        assertThat(result).isFalse();
        verify(eventDataRepository, never()).save(any());
        verify(session.localization).send("error-no-map");
    }

    @Test
    @DisplayName("saveDraft with map saves and clears draft")
    void saveDraft_withMap_savesAndClears() {
        MapData map = new MapData();
        map.id = new ObjectId();
        EventData draft = session.getDraft(EventData.class);
        draft.map = map.id;

        boolean result = eventEditorService.saveDraft(session);

        assertThat(result).isTrue();
        verify(eventDataRepository).save(draft);
        assertThat(session.hasDraft(EventData.class)).isFalse();
    }

    @Test
    @DisplayName("cancelDraft clears draft")
    void cancelDraft_clearsDraft() {
        session.getDraft(EventData.class);
        assertThat(session.hasDraft(EventData.class)).isTrue();

        eventEditorService.cancelDraft(session);

        assertThat(session.hasDraft(EventData.class)).isFalse();
    }

    @Test
    @DisplayName("selectMapForDraft assigns persisted map id")
    void selectMapForDraft_assignsMapId() {
        MapData persisted = new MapData();
        persisted.id = new ObjectId();
        when(mapDataRepository.findOrCreate("Name", "file.msav", "Author", "survival")).thenReturn(persisted);

        MapData result = eventEditorService.selectMapForDraft(session, "Name", "file.msav", "Author", "survival");

        assertThat(result).isEqualTo(persisted);
        EventData draft = session.getDraft(EventData.class);
        assertThat(draft.map).isEqualTo(persisted.id);
    }

    private Session createSession() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("test-uuid", true);
        data.uuid = "test-uuid";
        data.id = new ObjectId();

        MindustryMenuGateway gateway = mock(MindustryMenuGateway.class);
        SessionService sessionService = mock(SessionService.class);
        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        MenuService menuService = new MenuService(sessionProvider, gateway);

        Session session = new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;
        return session;
    }
}
