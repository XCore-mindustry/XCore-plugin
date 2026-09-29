package org.xcore.plugin.service;

import arc.Core;
import arc.Settings;
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
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerDiscoveryServiceTest {

    /**
     * Inline dispatcher: discovery is now sampled on the game thread, so the existing
     * assertions can keep reading the buffer straight after the call.
     */
    private final Async async = new Async(new StorageExecutor(4), Runnable::run);

    private final java.util.List<Runnable> queued = new java.util.ArrayList<>();

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
    @DisplayName("handleDiscovery writes configured description")
    void handleDiscoveryWritesConfiguredDescription() {
        var service = new ServerDiscoveryService(config(10), async);

        ByteBuffer buffer = ByteBuffer.allocate(512);
        service.handleDiscovery(buffer, () -> {});

        DiscoveryPacket packet = readPacket(buffer);
        assertThat(packet.description()).isEqualTo("Server description");
    }

    @Test
    @DisplayName("handleDiscovery writes zero player limit when disabled")
    void handleDiscoveryWritesZeroPlayerLimitWhenDisabled() {
        var service = new ServerDiscoveryService(config(0), async);

        ByteBuffer buffer = ByteBuffer.allocate(512);
        service.handleDiscovery(buffer, () -> {});

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

        var service = new ServerDiscoveryService(config(3), async);

        ByteBuffer buffer = ByteBuffer.allocate(512);
        service.handleDiscovery(buffer, () -> {});

        DiscoveryPacket packet = readPacket(buffer);
        assertThat(packet.playerLimit()).isEqualTo(4);
    }

    @Test
    @DisplayName("handleDiscovery defers the snapshot to the game thread before answering")
    void handleDiscoveryDefersSnapshotToGameThread() {
        var service = new ServerDiscoveryService(config(10), new Async(new StorageExecutor(4), queued::add));

        ByteBuffer buffer = ByteBuffer.allocate(512);
        var responded = new AtomicBoolean();
        service.handleDiscovery(buffer, () -> responded.set(true));

        // Discovery is served on an ArcNet UDP thread. Reading state.map and Groups.player
        // there races a map reload or a player join happening on the tick loop.
        assertThat(queued).hasSize(1);
        assertThat(responded).isFalse();
        assertThat(buffer.position()).isZero();

        queued.forEach(Runnable::run);

        assertThat(responded).isTrue();
        assertThat(readPacket(buffer).map()).isEqualTo("Test Map");
    }

    @Test
    @DisplayName("handleDiscovery falls back to an empty map name before the world loads")
    void handleDiscoveryFallsBackToEmptyMapName() {
        Vars.state.map = null;

        var service = new ServerDiscoveryService(config(10), async);
        ByteBuffer buffer = ByteBuffer.allocate(512);
        service.handleDiscovery(buffer, () -> {});

        assertThat(readPacket(buffer).map()).isEmpty();
    }

    @Test
    @DisplayName("handleDiscovery uses empty description when administration description is off")
    void handleDiscoveryUsesEmptyDescriptionWhenAdministrationDescriptionOff() {
        configuredDescription = "off";

        var service = new ServerDiscoveryService(config(10), async);

        ByteBuffer buffer = ByteBuffer.allocate(512);
        service.handleDiscovery(buffer, () -> {});

        DiscoveryPacket packet = readPacket(buffer);
        assertThat(packet.description()).isEmpty();
    }

    private static TomlXcoreConfig config(int playerLimit) {
        TomlXcoreConfig config = new TomlXcoreConfig();
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