package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.plugin.cloud.XCoreSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeleportControllerTest {

    @Test
    @DisplayName("teleportSelf teleports sender unit to destination player")
    void teleportSelf_movesSenderUnit() {
        TeleportController controller = new TeleportController();

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);
        when(sender.isPlayer()).thenReturn(true);

        Player selfPlayer = mock(Player.class);
        Unit selfUnit = mock(Unit.class);
        when(selfPlayer.unit()).thenReturn(selfUnit);
        when(sender.player()).thenReturn(selfPlayer);

        Player destPlayer = mock(Player.class);
        Unit destUnit = mock(Unit.class);
        destUnit.x = 240f;
        destUnit.y = 320f;
        when(destPlayer.unit()).thenReturn(destUnit);
        when(destPlayer.plainName()).thenReturn("Bob");

        SinglePlayerSelector destSelector = mock(SinglePlayerSelector.class);
        when(destSelector.resolve(handle)).thenReturn(destPlayer);

        controller.teleportSelf(sender, destSelector);

        verify(selfUnit).set(240f, 320f);
        verify(selfPlayer).snapInterpolation();
        verify(sender).sendMessage(contains("Teleported to"));
    }

    @Test
    @DisplayName("teleportTargetsToDestination moves multiple players to destination")
    void teleportTargetsToDestination_movesTargets() {
        TeleportController controller = new TeleportController();

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);

        Player destPlayer = mock(Player.class);
        Unit destUnit = mock(Unit.class);
        destUnit.x = 100f;
        destUnit.y = 200f;
        when(destPlayer.unit()).thenReturn(destUnit);
        when(destPlayer.plainName()).thenReturn("Alice");

        SinglePlayerSelector destSelector = mock(SinglePlayerSelector.class);
        when(destSelector.resolve(handle)).thenReturn(destPlayer);

        Player target1 = mock(Player.class);
        Unit unit1 = mock(Unit.class);
        when(target1.unit()).thenReturn(unit1);

        Player target2 = mock(Player.class);
        Unit unit2 = mock(Unit.class);
        when(target2.unit()).thenReturn(unit2);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);
        when(targetsSelector.resolve(handle)).thenReturn(Seq.with(target1, target2));

        controller.teleportTargetsToDestination(sender, targetsSelector, destSelector);

        verify(unit1).set(100f, 200f);
        verify(target1).snapInterpolation();
        verify(unit2).set(100f, 200f);
        verify(target2).snapInterpolation();
        verify(sender).sendMessage(argThat(s -> s.contains("Teleported") && s.contains("2") && s.contains("player(s)")));
    }

    @Test
    @DisplayName("teleportTargetsToCoords supports relative '~' offsets and absolute coords")
    void teleportTargetsToCoords_supportsRelativeAndAbsolute() {
        TeleportController controller = new TeleportController();

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);
        when(sender.isPlayer()).thenReturn(true);

        Player selfPlayer = mock(Player.class);
        Unit selfUnit = mock(Unit.class);
        selfUnit.x = 80f; // 10 tiles
        selfUnit.y = 80f; // 10 tiles
        when(selfPlayer.unit()).thenReturn(selfUnit);
        when(sender.player()).thenReturn(selfPlayer);

        Player target = mock(Player.class);
        Unit targetUnit = mock(Unit.class);
        when(target.unit()).thenReturn(targetUnit);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);
        when(targetsSelector.resolve(handle)).thenReturn(Seq.with(target));

        // ~5 ~-2 -> 80 + 5*8 = 120, 80 - 2*8 = 64
        controller.teleportTargetsToCoords(sender, targetsSelector, "~5", "~-2");

        verify(targetUnit).set(120f, 64f);
        verify(target).snapInterpolation();
    }

    @Test
    @DisplayName("bring teleports targets to sender location")
    void bring_teleportsTargetsToSender() {
        TeleportController controller = new TeleportController();

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);
        when(sender.isPlayer()).thenReturn(true);

        Player self = mock(Player.class);
        Unit selfUnit = mock(Unit.class);
        selfUnit.x = 500f;
        selfUnit.y = 600f;
        when(self.unit()).thenReturn(selfUnit);
        when(sender.player()).thenReturn(self);

        Player target = mock(Player.class);
        Unit targetUnit = mock(Unit.class);
        when(target.unit()).thenReturn(targetUnit);

        MultiplePlayerSelector targetsSelector = mock(MultiplePlayerSelector.class);
        when(targetsSelector.resolve(handle)).thenReturn(Seq.with(target));

        controller.bring(sender, targetsSelector);

        verify(targetUnit).set(500f, 600f);
        verify(target).snapInterpolation();
        verify(sender).sendMessage(argThat(s -> s.contains("Brought") && s.contains("1") && s.contains("player(s)")));
    }
}
