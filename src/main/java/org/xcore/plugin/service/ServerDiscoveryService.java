package org.xcore.plugin.service;

import arc.Core;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.core.Version;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.nio.ByteBuffer;

import static mindustry.Vars.state;
import static org.xcore.plugin.common.PacketUtils.writeString;

@Singleton
public class ServerDiscoveryService {

    private final TomlXcoreConfig config;
    private final Async async;

    @Inject
    public ServerDiscoveryService(TomlXcoreConfig config, Async async) {
        this.config = config;
        this.async = async;
    }

    /**
     * Discovery is served on an ArcNet UDP thread. The fields read below belong to the
     * running game — the current map can be swapped by a reload while a query is in
     * flight, and {@code Groups.player.count} walks a list the tick loop is mutating —
     * so they are sampled on the game thread and the packet is written afterwards.
     *
     * <p>The write and the response both happen on the game thread as well. If the game
     * thread never runs the task, the querier gets no reply and times out, which is
     * preferable to publishing a map name and player count torn out of a live game state.
     */
    public void handleDiscovery(ByteBuffer buffer, Runnable respond) {
        async.main(() -> {
            writeSnapshot(buffer, capture());
            respond.run();
        });
    }

    private DiscoverySnapshot capture() {
        GameThread.report("ServerDiscoveryService.capture");
        var map = state.map;
        return new DiscoverySnapshot(
                Administration.Config.serverName.string(),
                Administration.Config.desc.string().equals("off") ? "" : Administration.Config.desc.string(),
                // state.map is null until the world finishes loading on a dedicated server.
                map == null ? "" : map.name(),
                Core.settings.getInt("totalPlayers", Groups.player.size()),
                state.wave,
                state.rules.mode().ordinal(),
                config.server.playerLimit > 0 ? config.server.playerLimit + Groups.player.count(player -> player.admin) : 0,
                state.rules.modeName
        );
    }

    private void writeSnapshot(ByteBuffer buffer, DiscoverySnapshot snapshot) {
        writeString(buffer, snapshot.serverName(), 100);
        writeString(buffer, snapshot.map(), 64);

        buffer.putInt(snapshot.totalPlayers());
        buffer.putInt(snapshot.wave());
        buffer.putInt(Version.build);
        writeString(buffer, Version.type);

        buffer.put((byte) snapshot.mode());
        buffer.putInt(snapshot.playerLimit());

        writeString(buffer, snapshot.description(), 200);
        if (snapshot.modeName != null) {
            writeString(buffer, snapshot.modeName, 50);
        }

        buffer.position(0);
    }

    private record DiscoverySnapshot(
            String serverName,
            String description,
            String map,
            int totalPlayers,
            int wave,
            int mode,
            int playerLimit,
            String modeName
    ) {
    }
}
