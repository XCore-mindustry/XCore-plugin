package org.xcore.plugin.modules.history;

import arc.util.Log;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.Time;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.gen.Iconc;
import mindustry.world.blocks.ConstructBlock;

import java.time.Instant;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;

import static mindustry.Vars.content;
import static org.xcore.plugin.PluginVars.shortDateFormat;
import static org.xcore.plugin.utils.Utils.emoji;

public class HistoryEntry {
    public final String name;
    public final Type type;
    public final short blockID;
    public final long time;

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

    public String getMessage() {
        var builder = new StringBuilder();
        builder.append("[lightgray][").append(shortDateFormat.format(Instant.ofEpochMilli(time))).append(" (UTC)] ")
                .append(name).append("[accent] ");

        builder.append(type.name());

        var block = content.block(blockID);
        builder.append(" ").append(emoji(block));

        return builder.toString();
    }

    public enum Type {
        built, broke, configured
    }
}
