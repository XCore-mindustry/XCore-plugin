package org.xcore.plugin.map;

import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.service.EventService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmartMapSelectorHistoryTest {
    private final Settings previousSettings = Core.settings;
    private final Maps previousMaps = Vars.maps;

    @AfterEach
    void restore() {
        Core.settings = previousSettings;
        Vars.maps = previousMaps;
    }

    private static Map map(String fileName) {
        return new Map(new Fi(fileName), 50, 50, new StringMap(), true);
    }

    private static SmartMapSelector selector() {
        var maps = mock(MapDataRepository.class);
        when(maps.findAllAsMap()).thenReturn(new ObjectMap<>());
        return new SmartMapSelector(new TomlXcoreConfig(), mock(EventDataRepository.class), maps, mock(EventService.class));
    }

    @Test
    void historyFromABeforeRestartIsStillAvoidedAndSaved() {
        Map a = map("a.msav"), b = map("b.msav"), c = map("c.msav");
        Vars.maps = new Maps();
        Vars.maps.all().addAll(a, b, c);
        Core.settings = new Settings();
        Core.settings.put("xcore-recent-maps", "a.msav");

        assertThat(selector().next(Gamemode.survival, b)).isSameAs(c);
        assertThat(Core.settings.getString("xcore-recent-maps")).isEqualTo("a.msav\nb.msav");
    }
}
