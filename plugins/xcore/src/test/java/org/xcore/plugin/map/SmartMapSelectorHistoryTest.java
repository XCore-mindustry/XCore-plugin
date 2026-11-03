package org.xcore.plugin.map;

import arc.Core;
import arc.Events;
import arc.Settings;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.service.EventService;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WithHeadlessWorld
class SmartMapSelectorHistoryTest {
    private final Maps previousMaps = Vars.maps;

    @AfterEach
    void restore() {
        Vars.maps = previousMaps;
    }

    private static Map map(String path) {
        return new Map(new Fi(path), 50, 50, new StringMap(), true);
    }

    private static Settings settingsIn(Path dir) {
        var settings = new Settings();
        settings.setDataDirectory(new Fi(dir.toFile()));
        settings.load();
        return settings;
    }

    private static SmartMapSelector selector() {
        var maps = mock(MapDataRepository.class);
        when(maps.findAllAsMap()).thenReturn(new ObjectMap<>());
        return new SmartMapSelector(new TomlXcoreConfig(), mock(EventDataRepository.class), maps, mock(EventService.class));
    }

    @Test
    void playedMapsAreSavedToDiskAndAvoidedAfterARestart(@TempDir Path dir) {
        Map a = map("a/maze.msav"), b = map("b/maze.msav"), c = map("c.msav");
        Vars.maps = new Maps();
        Vars.maps.all().addAll(a, b, c);
        Core.settings = settingsIn(dir);

        var beforeRestart = selector();
        beforeRestart.start();
        Vars.state.map = a;
        Events.fire(new PlayEvent());
        Vars.state.map = b;
        Events.fire(new PlayEvent());
        Events.fire(new PlayEvent()); // the same map again is not a second entry
        beforeRestart.stop();

        assertThat(settingsIn(dir).getString("xcore-recent-maps")).isEqualTo("a/maze.msav\nb/maze.msav");

        Core.settings = settingsIn(dir);
        assertThat(selector().next(Gamemode.survival, null))
                .as("both maps sharing a file name are remembered apart, and the one running at the restart is skipped")
                .isSameAs(c);
    }
}
