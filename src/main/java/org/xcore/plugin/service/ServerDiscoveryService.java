package org.xcore.plugin.service;

import arc.Core;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.core.Version;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.nio.ByteBuffer;

import static mindustry.Vars.state;
import static org.xcore.plugin.common.PacketUtils.writeString;

@Singleton
public class ServerDiscoveryService {

    private final TomlXcoreConfig config;

    @Inject
    public ServerDiscoveryService(TomlXcoreConfig config) {
        this.config = config;
    }

    public void handleDiscovery(ByteBuffer buffer) {
        String name = Administration.Config.serverName.string();
        String description = Administration.Config.desc.string().equals("off")
                ? ""
                : Administration.Config.desc.string();
        String map = state.map.name();

        writeString(buffer, name, 100);
        writeString(buffer, map, 64);

        buffer.putInt(Core.settings.getInt("totalPlayers", Groups.player.size()));
        buffer.putInt(state.wave);
        buffer.putInt(Version.build);
        writeString(buffer, Version.type);

        buffer.put((byte) state.rules.mode().ordinal());
        buffer.putInt(config.server.playerLimit > 0 ? noAdminPlayerLimit() : 0);

        writeString(buffer, description, 200);
        if (state.rules.modeName != null) {
            writeString(buffer, state.rules.modeName, 50);
        }

        buffer.position(0);
    }

    private int noAdminPlayerLimit() {
        return config.server.playerLimit + Groups.player.count(player -> player.admin);
    }
}
