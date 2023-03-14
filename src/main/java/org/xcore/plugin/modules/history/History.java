package org.xcore.plugin.modules.history;

import arc.struct.Queue;
import mindustry.world.Tile;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.maxHistoryCapacity;

public class History {

    public static HistoryStack[] history;

    public static boolean enabled() {
        return !config.isMiniHexed();
    }

    public static void clear() {
        history = new HistoryStack[world.width() * world.height()];
    }

    public static void put(HistoryEntry entry, Tile tile) {
        if (tile == emptyTile) return;

        tile.getLinkedTiles(other -> {
            var stack = get(other.array());
            if (stack == null) return;

            stack.add(entry);
        });
    }

    public static HistoryStack get(int index) {
        if (index < 0 || index >= history.length) return null;

        var stack = history[index];
        if (stack == null) history[index] = stack = new HistoryStack();
        return stack;
    }

    public static class HistoryStack extends Queue<HistoryEntry> {
        public HistoryStack() {
            super(maxHistoryCapacity);
        }

        @Override
        public void add(HistoryEntry entry) {
            super.add(entry);
            if (size > maxHistoryCapacity) removeFirst();
        }
    }

    public static class TransportableHistoryStack {
        int x;
        int y;
        HistoryEntry[] data = new HistoryEntry[maxHistoryCapacity];

        public TransportableHistoryStack(Tile t) {
            var stack = get(t.array());

            if (stack.size < 1) {
                data[0] = new HistoryEntry();
            }

            this.x = t.x;
            this.y = t.y;
            for (int i = 0; i < maxHistoryCapacity; i++) {
                data[i] = stack.get(i);
            }
        }
    }
}