package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultipleUnitSelector;
import org.xcore.plugin.cloud.XCoreSender;

import static org.mockito.Mockito.*;

class EntityAdminControllerTest {

    @Test
    @DisplayName("healSelf restores full health and clears statuses")
    void healSelf_restoresHealth() {
        EntityAdminController controller = new EntityAdminController();

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.isPlayer()).thenReturn(true);

        Player player = mock(Player.class);
        Unit unit = mock(Unit.class);
        unit.maxHealth = 250f;
        when(player.unit()).thenReturn(unit);
        when(sender.player()).thenReturn(player);

        controller.healSelf(sender);

        verify(unit).health(250f);
        verify(unit).clearStatuses();
        verify(sender).sendMessage(contains("fully healed"));
    }

    @Test
    @DisplayName("healUnits heals all resolved units")
    void healUnits_healsTargets() {
        EntityAdminController controller = new EntityAdminController();

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);

        Unit u1 = mock(Unit.class);
        u1.maxHealth = 100f;
        when(u1.isAdded()).thenReturn(true);

        Unit u2 = mock(Unit.class);
        u2.maxHealth = 500f;
        when(u2.isAdded()).thenReturn(true);

        MultipleUnitSelector selector = mock(MultipleUnitSelector.class);
        when(selector.resolve(handle)).thenReturn(Seq.with(u1, u2));

        controller.healUnits(sender, selector);

        verify(u1).health(100f);
        verify(u1).clearStatuses();
        verify(u2).health(500f);
        verify(u2).clearStatuses();
        verify(sender).sendMessage(argThat(s -> s.contains("Healed") && s.contains("2") && s.contains("unit(s)")));
    }

    @Test
    @DisplayName("killUnits destroys target units")
    void killUnits_destroysUnits() {
        EntityAdminController controller = new EntityAdminController();

        XCoreSender sender = mock(XCoreSender.class);
        MindustrySender handle = mock(MindustrySender.class);
        when(sender.getHandle()).thenReturn(handle);

        Unit u1 = mock(Unit.class);
        when(u1.isAdded()).thenReturn(true);

        MultipleUnitSelector selector = mock(MultipleUnitSelector.class);
        when(selector.resolve(handle)).thenReturn(Seq.with(u1));

        controller.killUnits(sender, selector);

        verify(u1).kill();
        verify(sender).sendMessage(argThat(s -> s.contains("Killed") && s.contains("1") && s.contains("unit(s)")));
    }

    @Test
    @DisplayName("suicide kills current player unit")
    void suicide_killsPlayerUnit() {
        EntityAdminController controller = new EntityAdminController();

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.isPlayer()).thenReturn(true);

        Player player = mock(Player.class);
        Unit unit = mock(Unit.class);
        when(player.unit()).thenReturn(unit);
        when(sender.player()).thenReturn(player);

        controller.suicide(sender);

        verify(unit).kill();
        verify(sender).sendMessage(contains("killed your unit"));
    }
}
