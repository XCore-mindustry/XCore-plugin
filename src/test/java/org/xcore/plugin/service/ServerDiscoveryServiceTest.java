package org.xcore.plugin.service;

import arc.Core;
import arc.Settings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.Version;
import mindustry.entities.EntityGroup;
import mindustry.game.Gamemode;
import mindustry.game.Rules;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ServerDiscoveryServiceTest {

    private GameState previousState;
    private EntityGroup<Player> previousPlayers;
    private Settings previousSettings;
    private String configuredServerName;
    private String configuredDescription;

    @BeforeEach
    void setUp() {
        previousState = Vars.state;
        previousPlayers = Groups.player;
        previousSettings = Core.settings;

        Vars.state = new GameState();
        Vars.state.rules = mock(Rules.class);
        Vars.state.map = mock(mindustry.maps.Map.class);
        when(Vars.state.map.name()).thenReturn("Test Map");
        when(Vars.state.rules.mode()).thenReturn(Gamemode.pvp);
        Vars.state.rules.modeName = "Ranked PvP";
        Vars.state.wave = 17;

        Groups.player = new EntityGroup<>(Player.class, false, false);
        Core.settings = mock(Settings.class);
        configuredServerName = "Mini PvP";
        configuredDescription = "Server description";
        when(Core.settings.getString(anyString(), anyString())).thenAnswer(invocation -> switch (invocation.getArgument(0, String.class)) {
            case "servername" -> configuredServerName;
            case "desc" -> configuredDescription;
            default -> invocation.getArgument(1, String.class);
        });
        when(Core.settings.getInt("totalPlayers", 0)).thenReturn(5);
    }

    @AfterEach
    void tearDown() {
        Vars.state = previousState;
        Groups.player = previousPlayers;
        Core.settings = previousSettings;
    }

    @Test
    @DisplayName("updateFooter uses game started timer setting")
    void updateFooterUsesGameStartedTimerSetting() {
        var pluginState = new PluginState();
        pluginState.gameStartTime = 60_000L;

        try (MockedStatic<Time> time = mockStatic(Time.class)) {
            time.when(Time::millis).thenReturn(180_000L);

            var enabledService = new ServerDiscoveryService(config(true, 10), pluginState);
            enabledService.updateFooter();

            var buffer = ByteBuffer.allocate(512);
            enabledService.handleDiscovery(buffer);
            DiscoveryPacket packet = readPacket(buffer);
            assertThat(packet.description()).contains("Game started [accent]2[] minutes ago.");

            var disabledService = new ServerDiscoveryService(config(false, 10), pluginState);
            disabledService.updateFooter();

            var disabledBuffer = ByteBuffer.allocate(512);
            disabledService.handleDiscovery(disabledBuffer);
            DiscoveryPacket disabledPacket = readPacket(disabledBuffer);
            assertThat(disabledPacket.description()).isEqualTo("Server description");
        }
    }

    @Test
    @DisplayName("handleDiscovery writes zero player limit when disabled")
    void handleDiscoveryWritesZeroPlayerLimitWhenDisabled() {
        var service = new ServerDiscoveryService(config(true, 0), new PluginState());
        service.updateFooter();

        ByteBuffer buffer = ByteBuffer.allocate(512);
        service.handleDiscovery(buffer);

        assertThat(buffer.position()).isZero();
        DiscoveryPacket packet = readPacket(buffer);
        assertThat(packet.name()).isEqualTo("Mini PvP");
        assertThat(packet.map()).isEqualTo("Test Map");
        assertThat(packet.playerLimit()).isZero();
        assertThat(packet.wave()).isEqualTo(17);
        assertThat(packet.versionType()).isEqualTo(Version.type);
        assertThat(packet.modeName()).isEqualTo("Ranked PvP");
    }

    @Test
    @DisplayName("handleDiscovery preserves no-admin player limit semantics")
    void handleDiscoveryPreservesNoAdminPlayerLimitSemantics() {
        Groups.player.add(player(true));
        Groups.player.add(player(false));
        Groups.player.add(player(false));

        var service = new ServerDiscoveryService(config(true, 3), new PluginState());
        service.updateFooter();

        ByteBuffer buffer = ByteBuffer.allocate(512);
        service.handleDiscovery(buffer);

        DiscoveryPacket packet = readPacket(buffer);
        assertThat(packet.playerLimit()).isEqualTo(4);
    }

    @Test
    @DisplayName("handleDiscovery uses footer as description when administration description is off")
    void handleDiscoveryUsesFooterWhenAdministrationDescriptionOff() {
        configuredDescription = "off";

        var pluginState = new PluginState();
        pluginState.gameStartTime = 0L;

        try (MockedStatic<Time> time = mockStatic(Time.class)) {
            time.when(Time::millis).thenReturn(120_000L);

            var service = new ServerDiscoveryService(config(true, 10), pluginState);
            service.updateFooter();

            ByteBuffer buffer = ByteBuffer.allocate(512);
            service.handleDiscovery(buffer);

            DiscoveryPacket packet = readPacket(buffer);
            assertThat(packet.description()).isEqualTo("\n[green]Game started [accent]2[] minutes ago.");
        }
    }

    private static TomlXcoreConfig config(boolean gameStartedTimer, int playerLimit) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.gameStartedTimer = gameStartedTimer;
        config.server.playerLimit = playerLimit;
        return config;
    }

    private static Player player(boolean admin) {
        Player player = Player.create();
        player.admin = admin;
        return player;
    }

    private static DiscoveryPacket readPacket(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        copy.position(0);
        String name = readString(copy);
        String map = readString(copy);
        int totalPlayers = copy.getInt();
        int wave = copy.getInt();
        int build = copy.getInt();
        String versionType = readString(copy);
        byte modeOrdinal = copy.get();
        int playerLimit = copy.getInt();
        String description = readString(copy);
        String modeName = copy.hasRemaining() ? readString(copy) : "";
        return new DiscoveryPacket(name, map, totalPlayers, wave, build, versionType, modeOrdinal, playerLimit, description, modeName);
    }

    private static String readString(ByteBuffer buffer) {
        int length = Byte.toUnsignedInt(buffer.get());
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, Vars.charset);
    }

    private record DiscoveryPacket(
            String name,
            String map,
            int totalPlayers,
            int wave,
            int build,
            String versionType,
            byte modeOrdinal,
            int playerLimit,
            String description,
            String modeName
    ) {
    }
}
