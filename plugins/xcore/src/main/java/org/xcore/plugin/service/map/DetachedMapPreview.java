package org.xcore.plugin.service.map;

import arc.files.Fi;
import arc.graphics.Pixmap;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OverlayFloor;
import org.xcore.plugin.service.map.inspect.ContentRegistrySnapshot;
import org.xcore.plugin.service.map.inspect.DetachedMsavReader;
import org.xcore.plugin.service.map.inspect.InspectionSettings;
import org.xcore.plugin.service.map.inspect.MapSnapshot;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Captures values on the game thread; reading and drawing never touch engine state. */
final class DetachedMapPreview {
    private final ContentRegistrySnapshot registry;
    private final Map<Integer, BlockColor> colors;
    private final int[] teamColors;

    private DetachedMapPreview(ContentRegistrySnapshot registry, Map<Integer, BlockColor> colors, int[] teamColors) {
        this.registry = registry;
        this.colors = Map.copyOf(colors);
        this.teamColors = teamColors;
    }

    static DetachedMapPreview capture() {
        var registry = ContentRegistrySnapshot.capture();
        var colors = new HashMap<Integer, BlockColor>();
        for (var block : Vars.content.blocks()) {
            colors.put((int) block.id, new BlockColor(MapColorPalette.colorFor(block), block.synthetic(),
                    block.solid, block.useColor, block instanceof OverlayFloor,
                    block instanceof Floor floor && floor.wallOre));
        }
        int[] teams = new int[Team.all.length];
        for (Team team : Team.all) teams[team.id] = team.color.rgba();
        return new DetachedMapPreview(registry, colors, teams);
    }

    Pixmap read(Fi file) throws IOException {
        var settings = new InspectionSettings(InspectionSettings.MAX_INFLATED_BYTES,
                InspectionSettings.MAX_ALLOCATED_TILES, Map.of());
        try (var source = file.read()) {
            var snapshot = new DetachedMsavReader().read(source, file.length(), "", registry, settings);
            if (!snapshot.tilesAvailable()) throw new IOException("Map exceeds preview tile limit");
            return render(snapshot);
        }
    }

    private Pixmap render(MapSnapshot snapshot) {
        int width = snapshot.metadata().width(), height = snapshot.metadata().height();
        var pixmap = new Pixmap(width, height);
        try {
            for (int i = 0; i < snapshot.tileCount(); i++) {
                var wall = colors.get(snapshot.block(i));
                var floor = colors.get(snapshot.floor(i));
                var overlay = colors.get(snapshot.overlay(i));
                int color = floor == null ? 255 : floor.rgba;
                if (wall != null && wall.synthetic) color = teamColors[snapshot.team(i)];
                else if (overlay != null && overlay.wallOre) color = overlay.rgba;
                else if (wall != null && wall.solid) color = wall.rgba;
                else if (overlay != null && overlay.useColor) {
                    color = overlay.overlay ? overlay.rgba : Pixmap.blend((overlay.rgba & ~255) | 128, color);
                }
                pixmap.setRaw(i % width, height - 1 - i / width, color);
            }
            return pixmap;
        } catch (RuntimeException | Error error) {
            pixmap.dispose();
            throw error;
        }
    }

    private record BlockColor(int rgba, boolean synthetic, boolean solid, boolean useColor,
                              boolean overlay, boolean wallOre) {}
}
