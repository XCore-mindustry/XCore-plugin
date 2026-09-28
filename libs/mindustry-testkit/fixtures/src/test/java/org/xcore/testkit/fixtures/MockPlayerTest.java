package org.xcore.testkit.fixtures;

import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.SendMessageCallPacket2;
import mindustry.net.Packets.KickReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPlayerTest {
    private HeadlessWorld world;

    @BeforeEach
    void setUp() {
        world = HeadlessWorld.create(32, 32);
    }

    @AfterEach
    void tearDown() {
        if (world != null) {
            world.close();
        }
    }

    @Test
    @DisplayName("Should capture player.sendMessage in transcript and packets")
    void shouldCaptureSendMessage() {
        MockPlayer player = world.addPlayer("Explorer", Team.sharded);

        player.player().sendMessage("Hello from server!");

        assertThat(player.receivedMessages()).containsExactly("Hello from server!");
        assertThat(player.lastReceivedMessage()).isEqualTo("Hello from server!");

        SendMessageCallPacket2 packet = player.lastPacket(SendMessageCallPacket2.class);
        assertThat(packet).isNotNull();
        assertThat(packet.message).isEqualTo("Hello from server!");
    }

    @Test
    @DisplayName("Should capture Call announcements, info popups, and warning toasts")
    void shouldCaptureCallPopups() {
        MockPlayer player = world.addPlayer("Observer", Team.sharded);

        Call.announce(player.con(), "Server restarting in 5 minutes!");
        Call.infoMessage(player.con(), "Welcome to the game!");
        Call.warningToast(player.con(), 0, "Warning: Low power!");

        assertThat(player.announcements()).containsExactly("Server restarting in 5 minutes!");
        assertThat(player.lastAnnouncement()).isEqualTo("Server restarting in 5 minutes!");

        assertThat(player.infoPopups()).containsExactly("Welcome to the game!");
        assertThat(player.lastInfoPopup()).isEqualTo("Welcome to the game!");

        assertThat(player.warningToasts()).containsExactly("Warning: Low power!");
        assertThat(player.lastWarningToast()).isEqualTo("Warning: Low power!");
    }

    @Test
    @DisplayName("Should capture kick with custom string reason")
    void shouldCaptureKickString() {
        MockPlayer player = world.addPlayer("Griefer", Team.crux);

        player.con().kick("Griefing defense walls");

        assertThat(player.isKicked()).isTrue();
        assertThat(player.kickReason()).isEqualTo("Griefing defense walls");
    }

    @Test
    @DisplayName("Should capture kick with enum reason")
    void shouldCaptureKickEnum() {
        MockPlayer player = world.addPlayer("Spammer", Team.crux);

        player.con().kick(KickReason.kick);

        assertThat(player.isKicked()).isTrue();
        assertThat(player.kickReason()).isEqualTo("kick");
    }

    @Test
    @DisplayName("Should execute chat and command handlers via runCommand")
    void shouldRouteCommands() {
        MockPlayer admin = world.addAdmin("Admin", Team.sharded);

        world.registerCommand("ping", "", "Reply with pong", (args, sender) -> {
            sender.sendMessage("pong");
        });

        admin.runCommand("ping");

        assertThat(admin.lastReceivedMessage()).isEqualTo("pong");
    }

    @Test
    @DisplayName("Should spawn and attach unit to player")
    void shouldSpawnUnit() {
        MockPlayer player = world.addPlayer(b -> b.name("Pilot").positionTiles(5, 5));

        player.spawnUnit(UnitTypes.dagger);

        assertThat(player.unit()).isNotNull();
        assertThat(player.unit().type).isEqualTo(UnitTypes.dagger);
        assertThat(player.unit().team).isEqualTo(Team.sharded);
        assertThat(player.unit().x).isEqualTo(player.x());
        assertThat(player.unit().y).isEqualTo(player.y());
    }
}
