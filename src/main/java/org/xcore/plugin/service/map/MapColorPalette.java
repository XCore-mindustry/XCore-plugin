package org.xcore.plugin.service.map;

import arc.graphics.Color;
import arc.struct.ObjectIntMap;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.ColorMapper;

/**
 * Provides static color mappings for environment blocks on headless servers.
 *
 * <p>In headless mode, Mindustry skips sprite atlas generation, leaving {@code block.mapColor}
 * as default black (0, 0, 0, 1). This palette assigns authentic center-pixel colors to all
 * vanilla floors and walls so {@code MapIO.generatePreview} creates rich, accurate minimaps.
 */
public final class MapColorPalette {

    private static final ObjectIntMap<String> PALETTE = new ObjectIntMap<>();
    private static volatile boolean initialized = false;

    static {
        // Extracted authentic center-pixel RGBA colors for vanilla Mindustry environment blocks
        setColor("arkycite-floor", 0x60834EFF);
        setColor("arkyic-stone", 0x567148FF);
        setColor("arkyic-vent", 0x131F1DFF);
        setColor("arkyic-wall", 0x567148FF);
        setColor("basalt", 0x515151FF);
        setColor("basalt-vent", 0x181218FF);
        setColor("beryllic-stone", 0x323935FF);
        setColor("beryllic-stone-wall", 0x465248FF);
        setColor("bluemat", 0x373050FF);
        setColor("carbon-stone", 0x3C4448FF);
        setColor("carbon-vent", 0x06060CFF);
        setColor("carbon-wall", 0x3C4448FF);
        setColor("char", 0x54545BFF);
        setColor("core-zone", 0xEAB678FF);
        setColor("crater-stone", 0x54545BFF);
        setColor("crystal-floor", 0x60496DFF);
        setColor("crystalline-stone", 0x57485BFF);
        setColor("crystalline-stone-wall", 0x2E2A30FF);
        setColor("crystalline-vent", 0x0D0C11FF);
        setColor("dacite", 0xC1C4CBFF);
        setColor("dacite-wall", 0x9192A6FF);
        setColor("dark-metal", 0x9B9DAAFF);
        setColor("dark-panel-1", 0x3F4049FF);
        setColor("dark-panel-2", 0x3F4049FF);
        setColor("dark-panel-3", 0x686B7BFF);
        setColor("dark-panel-4", 0x686B7BFF);
        setColor("dark-panel-5", 0xEF806CFF);
        setColor("dark-panel-6", 0x3F4049FF);
        setColor("darksand", 0x3C3838FF);
        setColor("darksand-tainted-water", 0x483D5DFF);
        setColor("darksand-water", 0x374C78FF);
        setColor("darksand-wall", 0x2F2C2CFF);
        setColor("deep-tainted-water", 0x523363FF);
        setColor("deep-water", 0x32477EFF);
        setColor("dense-red-stone", 0x832A2AFF);
        setColor("dirt", 0x73513FFF);
        setColor("dirt-wall", 0x5C3F30FF);
        setColor("dune-wall", 0x9C885BFF);
        setColor("ferric-craters", 0x6D4336FF);
        setColor("ferric-stone", 0x834F40FF);
        setColor("ferric-stone-wall", 0x633C31FF);
        setColor("grass", 0x5D8036FF);
        setColor("hotrock", 0xC04E2EFF);
        setColor("ice", 0xCBE3FAFF);
        setColor("ice-snow", 0xDDEAFBFF);
        setColor("ice-wall", 0x9BBFEAFF);
        setColor("magmarock", 0xEB7938FF);
        setColor("metal-floor", 0x585B65FF);
        setColor("metal-floor-2", 0x686B7BFF);
        setColor("metal-floor-3", 0x3F4049FF);
        setColor("metal-floor-4", 0x585B65FF);
        setColor("metal-floor-5", 0x686B7BFF);
        setColor("metal-floor-damaged", 0x3F4049FF);
        setColor("moss", 0x466B38FF);
        setColor("mud", 0x4E3C32FF);
        setColor("ore-coal", 0x27272DFF);
        setColor("ore-copper", 0xD99D73FF);
        setColor("ore-lead", 0x8C7FA9FF);
        setColor("ore-scrap", 0x7E7E7EFF);
        setColor("ore-titanium", 0x8DA1E3FF);
        setColor("ore-thorium", 0xF38CBAFF);
        setColor("pooled-cryofluid", 0x6ECDECFF);
        setColor("molten-slag", 0xF68021FF);
        setColor("red-ice", 0xC78D8DFF);
        setColor("red-ice-wall", 0x9B6262FF);
        setColor("red-stone", 0x9E3B3BFF);
        setColor("red-stone-vent", 0x2B1010FF);
        setColor("red-stone-wall", 0x7D2B2BFF);
        setColor("regolith", 0x595D6EFF);
        setColor("regolith-wall", 0x414553FF);
        setColor("rhyolite", 0x5C635BFF);
        setColor("rhyolite-crater", 0x3A4039FF);
        setColor("rhyolite-vent", 0x1E221DFF);
        setColor("rhyolite-wall", 0x4A5049FF);
        setColor("rough-rhyolite", 0x5C635BFF);
        setColor("salt", 0xEAEAEAFF);
        setColor("sand-floor", 0xD1B16CFF);
        setColor("sand-wall", 0xAB8C51FF);
        setColor("sand-water", 0x4E6E85FF);
        setColor("shale", 0x3A3742FF);
        setColor("shale-wall", 0x2A2730FF);
        setColor("shallow-water", 0x405B98FF);
        setColor("slag", 0xF68021FF);
        setColor("snow", 0xE5EFFCFF);
        setColor("snow-wall", 0xB3CAE5FF);
        setColor("spore-moss", 0x6E4A8CFF);
        setColor("spore-wall", 0x4F3366FF);
        setColor("spore-water", 0x4B3A69FF);
        setColor("stone", 0x7A7A7AFF);
        setColor("stone-wall", 0x5C5C5CFF);
        setColor("tainted-water", 0x6E4387FF);
        setColor("tar", 0x222227FF);
        setColor("water", 0x405B98FF);
        setColor("yellow-stone", 0xA78E44FF);
        setColor("yellow-stone-plates", 0x8A7332FF);
        setColor("yellow-stone-vent", 0x2E250EFF);
        setColor("yellow-stone-wall", 0x7A632BFF);
    }

    private static void setColor(String name, int rgba) {
        PALETTE.put(name, rgba);
    }

    /** Ensures environment blocks have their mapColor populated on headless servers. */
    public static void ensureInitialized() {
        if (initialized || Vars.content == null) return;
        synchronized (MapColorPalette.class) {
            if (initialized) return;

            for (Block block : Vars.content.blocks()) {
                if (block.synthetic()) continue;

                // If block.mapColor is default pure black, assign from palette
                if (block.mapColor.r == 0f && block.mapColor.g == 0f && block.mapColor.b == 0f) {
                    int color = PALETTE.get(block.name, 0);
                    if (color == 0) {
                        color = PALETTE.get(block.name.replace("-floor", ""), 0);
                    }
                    if (color != 0) {
                        block.mapColor.set(Color.valueOf(String.format("%08X", color)));
                    }
                }
            }

            ColorMapper.load();
            initialized = true;
        }
    }
}
