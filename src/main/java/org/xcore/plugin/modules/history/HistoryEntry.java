package org.xcore.plugin.modules.history;

import arc.util.Time;
import lombok.NoArgsConstructor;
import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.world.blocks.ConstructBlock;

import java.time.Instant;

import static mindustry.Vars.content;
import static org.xcore.plugin.PluginVars.shortDateFormat;
import static useful.Bundle.*;
import static org.xcore.plugin.utils.Utils.emoji;

@NoArgsConstructor
public class HistoryEntry {
    public String name;
    public Type type;
    public short blockID;
    public long time;

    public HistoryEntry(EventType.BlockBuildEndEvent event) {
        this.name = event.unit.getControllerName();
        this.type = event.breaking ? Type.broke : Type.built;
        this.blockID = event.tile.build instanceof ConstructBlock.ConstructBuild build ? build.current.id : event.tile.blockID();
        this.time = Time.millis();
    }

    public HistoryEntry(EventType.ConfigEvent event) {
        this.name = event.player.coloredName();
        this.type = Type.configured;
        this.blockID = event.tile.block.id;
        this.time = Time.millis();
    }

    public String getMessage(Player player) {
        var block = content.block(blockID);
        return format("history.entry", player.locale,
                shortDateFormat.format(Instant.ofEpochMilli(time)),
                name, type.name(), emoji(block));
    }

    public enum Type {
        built, broke, configured
    }
}
