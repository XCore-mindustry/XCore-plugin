package org.xcore.plugin.service;

import arc.Core;
import arc.util.Time;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.core.Version;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.config.Config;

import java.nio.ByteBuffer;
import java.time.Duration;

import static mindustry.Vars.state;
import static org.xcore.plugin.common.PacketUtils.writeString;

@Singleton
public class ServerDiscoveryService {

    private final Config config;
    private final PluginState pluginState;

    private String footer = "";

    @Inject
    public ServerDiscoveryService(Config config, PluginState pluginState) {
        this.config = config;
        this.pluginState = pluginState;
    }

    public void updateFooter() {
        this.footer = config.gameStartedTimer
                ? "\n[green]Game started [accent]" + Duration.ofMillis(Time.millis() - pluginState.gameStartTime).toMinutes() + "[] minutes ago."
                : "";
    }

    public void handleDiscovery(ByteBuffer buffer) {
        String name = Administration.Config.serverName.string();
        String description = Administration.Config.desc.string().equals("off")
                ? footer
                : Administration.Config.desc.string() + footer;
        String map = state.map.name();

        writeString(buffer, name, 100);
        writeString(buffer, map, 64);

        buffer.putInt(Core.settings.getInt("totalPlayers", Groups.player.size()));
        buffer.putInt(state.wave);
        buffer.putInt(Version.build);
        writeString(buffer, Version.type);

        buffer.put((byte) state.rules.mode().ordinal());
        buffer.putInt(config.playerLimit > 0 ? config.getNoAdminPlayerLimit() : 0);

        writeString(buffer, description, 200);
        if (state.rules.modeName != null) {
            writeString(buffer, state.rules.modeName, 50);
        }

        buffer.position(0);
    }
}
