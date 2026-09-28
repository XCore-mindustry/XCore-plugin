package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.MockPlayer;
import org.xcore.testkit.fixtures.junit.HeadlessWorldExtension;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(HeadlessWorldExtension.class)
@WithHeadlessWorld(width = 64, height = 64)
class TeleportControllerTest {

    private final TeleportController controller = new TeleportController();

    @BeforeEach
    void setUp(HeadlessWorld world) {
        world.state().rules.disableUnitCap = true;
    }

    private XCoreSender createSender(MockPlayer player) {
        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = new MindustrySender.PlayerSender(player.player());
        when(sender.getHandle()).thenReturn(handle);
        when(sender.isPlayer()).thenReturn(true);
        when(sender.player()).thenReturn(player.player());
        doAnswer(invocation -> {
            player.player().sendMessage(invocation.getArgument(0));
            return null;
        }).when(sender).sendMessage(anyString());
        return sender;
    }

    @Test
    @DisplayName("teleportSelf teleports sender unit to destination player")
    void teleportSelf_movesSenderUnit(HeadlessWorld world) {
        MockPlayer self = world.addPlayer(b -> b.name("Self").position(50f, 50f));
        self.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(self);

        MockPlayer dest = world.addPlayer(b -> b.name("Bob").position(240f, 320f));
        dest.spawnUnit(UnitTypes.dagger);
        dest.unit().set(240f, 320f);

        SinglePlayerSelector destSelector = mock(SinglePlayerSelector.class);
        when(destSelector.resolve(sender.getHandle())).thenReturn(dest.player());

        controller.teleportSelf(sender, destSelector);

        assertThat(self.unit().x).isEqualTo(240f);
        assertThat(self.unit().y).isEqualTo(320f);
        assertThat(self.receivedMessages())
                .anyMatch(msg -> msg.contains("Teleported to") && msg.contains("Bob"));
    }

    @Test
    @DisplayName("teleportSelf warns when destination player has no unit")
    void teleportSelf_destinationHasNoUnit(HeadlessWorld world) {
        MockPlayer self = world.addPlayer("Self", Team.sharded);
        self.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(self);

        MockPlayer dest = world.addPlayer("Bob", Team.sharded);

        SinglePlayerSelector destSelector = mock(SinglePlayerSelector.class);
        when(destSelector.resolve(sender.getHandle())).thenReturn(dest.player());

        controller.teleportSelf(sender, destSelector);

        assertThat(self.receivedMessages())
                .anyMatch(msg -> msg.contains("Target player has no active unit"));
    }

    @Test
    @DisplayName("teleportSelf warns when sender has no unit")
    void teleportSelf_senderHasNoUnit(HeadlessWorld world) {
        MockPlayer self = world.addPlayer("Self", Team.sharded);
        XCoreSender sender = createSender(self);

        MockPlayer dest = world.addPlayer("Bob", Team.sharded);
        dest.spawnUnit(UnitTypes.dagger);

        SinglePlayerSelector destSelector = mock(SinglePlayerSelector.class);

        controller.teleportSelf(sender, destSelector);

        assertThat(self.receivedMessages())
                .anyMatch(msg -> msg.contains("You must be an active in-game player"));
    }

    @Test
    @DisplayName("teleportTargetsToDestination moves multiple players to destination")
    void teleportTargetsToDestination_movesTargets(HeadlessWorld world) {
        MockPlayer senderPlayer = world.addPlayer("Admin", Team.sharded);
        senderPlayer.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(senderPlayer);

        MockPlayer dest = world.addPlayer(b -> b.name("Alice").position(100f, 200f));
        dest.spawnUnit(UnitTypes.dagger);
        dest.unit().set(100f, 200f);

        SinglePlayerSelector destSelector = mock(SinglePlayerSelector.class);
        when(destSelector.resolve(sender.getHandle())).thenReturn(dest.player());

        MockPlayer target1 = world.addPlayer(b -> b.name("Target1").position(10f, 10f));
        target1.spawnUnit(UnitTypes.dagger);

        MockPlayer target2 = world.addPlayer(b -> b.name("Target2").position(20f, 20f));
        target2.spawnUnit(UnitTypes.dagger);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);
        when(targetsSelector.resolve(sender.getHandle())).thenReturn(Seq.with(target1.player(), target2.player()));

        controller.teleportTargetsToDestination(sender, targetsSelector, destSelector);

        assertThat(target1.unit().x).isEqualTo(100f);
        assertThat(target1.unit().y).isEqualTo(200f);
        assertThat(target2.unit().x).isEqualTo(100f);
        assertThat(target2.unit().y).isEqualTo(200f);
        assertThat(senderPlayer.receivedMessages())
                .anyMatch(msg -> msg.contains("Teleported") && msg.contains("2") && msg.contains("player(s)") && msg.contains("Alice"));
    }

    @Test
    @DisplayName("teleportTargetsToDestination warns when destination has no active unit")
    void teleportTargetsToDestination_destinationHasNoUnit(HeadlessWorld world) {
        MockPlayer senderPlayer = world.addPlayer("Admin", Team.sharded);
        senderPlayer.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(senderPlayer);

        MockPlayer dest = world.addPlayer("Alice", Team.sharded);

        SinglePlayerSelector destSelector = mock(SinglePlayerSelector.class);
        when(destSelector.resolve(sender.getHandle())).thenReturn(dest.player());

        MockPlayer target = world.addPlayer("Target", Team.sharded);
        target.spawnUnit(UnitTypes.dagger);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);

        controller.teleportTargetsToDestination(sender, targetsSelector, destSelector);

        assertThat(senderPlayer.receivedMessages())
                .anyMatch(msg -> msg.contains("Destination player has no active unit"));
    }

    @Test
    @DisplayName("teleportTargetsToCoords supports relative '~' offsets and absolute coords")
    void teleportTargetsToCoords_supportsRelativeAndAbsolute(HeadlessWorld world) {
        MockPlayer senderPlayer = world.addPlayer(b -> b.name("Admin").position(80f, 80f));
        senderPlayer.spawnUnit(UnitTypes.dagger);
        senderPlayer.unit().set(80f, 80f);
        XCoreSender sender = createSender(senderPlayer);

        MockPlayer target = world.addPlayer(b -> b.name("Target").position(0f, 0f));
        target.spawnUnit(UnitTypes.dagger);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);
        when(targetsSelector.resolve(sender.getHandle())).thenReturn(Seq.with(target.player()));

        // ~5 ~-2 -> 80 + 5*8 = 120, 80 - 2*8 = 64
        controller.teleportTargetsToCoords(sender, targetsSelector, "~5", "~-2");

        assertThat(target.unit().x).isEqualTo(120f);
        assertThat(target.unit().y).isEqualTo(64f);
        assertThat(senderPlayer.receivedMessages())
                .anyMatch(msg -> msg.contains("Teleported") && msg.contains("1") && msg.contains("player(s)"));
    }

    @Test
    @DisplayName("teleportTargetsToCoords supports absolute tile coordinates")
    void teleportTargetsToCoords_absoluteCoords(HeadlessWorld world) {
        MockPlayer senderPlayer = world.addPlayer("Admin", Team.sharded);
        senderPlayer.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(senderPlayer);

        MockPlayer target = world.addPlayer(b -> b.name("Target").position(0f, 0f));
        target.spawnUnit(UnitTypes.dagger);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);
        when(targetsSelector.resolve(sender.getHandle())).thenReturn(Seq.with(target.player()));

        // tile coords 10, 20 -> 10*8 = 80f, 20*8 = 160f
        controller.teleportTargetsToCoords(sender, targetsSelector, "10", "20");

        assertThat(target.unit().x).isEqualTo(80f);
        assertThat(target.unit().y).isEqualTo(160f);
        assertThat(senderPlayer.receivedMessages())
                .anyMatch(msg -> msg.contains("Teleported") && msg.contains("1") && msg.contains("10, 20"));
    }

    @Test
    @DisplayName("teleportTargetsToCoords warns on invalid coordinates")
    void teleportTargetsToCoords_invalidCoords(HeadlessWorld world) {
        MockPlayer senderPlayer = world.addPlayer("Admin", Team.sharded);
        senderPlayer.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(senderPlayer);

        MockPlayer target = world.addPlayer("Target", Team.sharded);
        target.spawnUnit(UnitTypes.dagger);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);

        controller.teleportTargetsToCoords(sender, targetsSelector, "invalid", "coords");

        assertThat(senderPlayer.receivedMessages())
                .anyMatch(msg -> msg.contains("Invalid coordinates"));
    }

    @Test
    @DisplayName("bring teleports targets to sender location")
    void bring_teleportsTargetsToSender(HeadlessWorld world) {
        MockPlayer self = world.addPlayer(b -> b.name("Self").position(500f, 600f));
        self.spawnUnit(UnitTypes.dagger);
        self.unit().set(500f, 600f);
        XCoreSender sender = createSender(self);

        MockPlayer target = world.addPlayer(b -> b.name("Target").position(10f, 10f));
        target.spawnUnit(UnitTypes.dagger);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);
        // Includes self to verify that self is filtered out
        when(targetsSelector.resolve(sender.getHandle())).thenReturn(Seq.with(target.player(), self.player()));

        controller.bring(sender, targetsSelector);

        assertThat(target.unit().x).isEqualTo(500f);
        assertThat(target.unit().y).isEqualTo(600f);
        assertThat(self.receivedMessages())
                .anyMatch(msg -> msg.contains("Brought") && msg.contains("1") && msg.contains("player(s)"));
    }

    @Test
    @DisplayName("bring warns when sender has no unit")
    void bring_senderHasNoUnit(HeadlessWorld world) {
        MockPlayer self = world.addPlayer("Self", Team.sharded);
        XCoreSender sender = createSender(self);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);

        controller.bring(sender, targetsSelector);

        assertThat(self.receivedMessages())
                .anyMatch(msg -> msg.contains("You must be an active in-game player"));
    }
}
