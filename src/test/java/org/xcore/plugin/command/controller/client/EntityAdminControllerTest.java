package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultipleUnitSelector;
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
class EntityAdminControllerTest {

    private final EntityAdminController controller = new EntityAdminController();

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
    @DisplayName("heal with null targets restores full health and clears statuses on self")
    void healSelf_restoresHealth(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Healer", Team.sharded);
        player.spawnUnit(UnitTypes.dagger);
        Unit unit = player.unit();
        unit.health = 50f;
        unit.apply(StatusEffects.burning, 1000f);

        assertThat(unit.health).isLessThan(unit.maxHealth);
        assertThat(unit.getDuration(StatusEffects.burning)).isGreaterThan(0f);

        XCoreSender sender = createSender(player);

        controller.heal(sender, null);

        assertThat(unit.health).isEqualTo(unit.maxHealth);
        assertThat(unit.getDuration(StatusEffects.burning)).isZero();
        assertThat(player.receivedMessages())
                .anyMatch(msg -> msg.contains("fully healed"));
    }

    @Test
    @DisplayName("heal with null targets warns when player has no unit")
    void healSelf_noUnit(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("PlayerNoUnit", Team.sharded);
        XCoreSender sender = createSender(player);

        controller.heal(sender, null);

        assertThat(player.receivedMessages())
                .anyMatch(msg -> msg.contains("You must have an active unit to heal"));
    }

    @Test
    @DisplayName("heal with targets heals all resolved units and clears their statuses")
    void healUnits_healsTargets(HeadlessWorld world) {
        MockPlayer admin = world.addPlayer("Admin", Team.sharded);
        admin.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(admin);

        Unit u1 = world.spawnUnit(UnitTypes.dagger, Team.sharded, 50f, 50f);
        u1.health = 20f;
        u1.apply(StatusEffects.burning, 1000f);

        Unit u2 = world.spawnUnit(UnitTypes.nova, Team.sharded, 100f, 100f);
        u2.health = 10f;
        u2.apply(StatusEffects.freezing, 1000f);

        MultipleUnitSelector selector = mock(MultipleUnitSelector.class);
        when(selector.resolve(sender.getHandle())).thenReturn(Seq.with(u1, u2));

        controller.heal(sender, selector);

        assertThat(u1.health).isEqualTo(u1.maxHealth);
        assertThat(u1.getDuration(StatusEffects.burning)).isZero();
        assertThat(u2.health).isEqualTo(u2.maxHealth);
        assertThat(u2.getDuration(StatusEffects.freezing)).isZero();
        assertThat(admin.receivedMessages())
                .anyMatch(msg -> msg.contains("Healed") && msg.contains("2") && msg.contains("unit(s)"));
    }

    @Test
    @DisplayName("killUnits destroys target units")
    void killUnits_destroysUnits(HeadlessWorld world) {
        MockPlayer admin = world.addPlayer("Admin", Team.sharded);
        admin.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(admin);

        Unit u1 = world.spawnUnit(UnitTypes.dagger, Team.sharded, 50f, 50f);
        Unit u2 = world.spawnUnit(UnitTypes.nova, Team.sharded, 100f, 100f);

        assertThat(u1.isAdded()).isTrue();
        assertThat(u1.dead).isFalse();
        assertThat(u2.isAdded()).isTrue();
        assertThat(u2.dead).isFalse();

        MultipleUnitSelector selector = mock(MultipleUnitSelector.class);
        when(selector.resolve(sender.getHandle())).thenReturn(Seq.with(u1, u2));

        controller.killUnits(sender, selector);

        assertThat(u1.dead).isTrue();
        assertThat(u2.dead).isTrue();
        assertThat(admin.receivedMessages())
                .anyMatch(msg -> msg.contains("Killed") && msg.contains("2") && msg.contains("unit(s)"));
    }

    @Test
    @DisplayName("killPlayers destroys target player units")
    void killPlayers_destroysPlayerUnits(HeadlessWorld world) {
        MockPlayer admin = world.addPlayer("Admin", Team.sharded);
        admin.spawnUnit(UnitTypes.dagger);
        XCoreSender sender = createSender(admin);

        MockPlayer target1 = world.addPlayer("Target1", Team.sharded);
        target1.spawnUnit(UnitTypes.dagger);
        Unit targetUnit1 = target1.unit();

        MockPlayer target2 = world.addPlayer("Target2", Team.sharded);
        target2.spawnUnit(UnitTypes.nova);
        Unit targetUnit2 = target2.unit();

        MultiplePlayerSelector selector = mock(MultiplePlayerSelector.class);
        when(selector.resolve(sender.getHandle())).thenReturn(Seq.with(target1.player(), target2.player()));

        controller.killPlayers(sender, selector);

        assertThat(targetUnit1.dead).isTrue();
        assertThat(targetUnit2.dead).isTrue();
        assertThat(admin.receivedMessages())
                .anyMatch(msg -> msg.contains("Killed") && msg.contains("2") && msg.contains("player(s)"));
    }

    @Test
    @DisplayName("suicide kills current player unit")
    void suicide_killsPlayerUnit(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("SuicidePlayer", Team.sharded);
        player.spawnUnit(UnitTypes.dagger);
        Unit unit = player.unit();

        XCoreSender sender = createSender(player);

        controller.suicide(sender);

        assertThat(unit.dead).isTrue();
        assertThat(player.receivedMessages())
                .anyMatch(msg -> msg.contains("killed your unit"));
    }

    @Test
    @DisplayName("suicide warns when player has no unit")
    void suicide_noUnit(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("PlayerNoUnit", Team.sharded);
        XCoreSender sender = createSender(player);

        controller.suicide(sender);

        assertThat(player.receivedMessages())
                .anyMatch(msg -> msg.contains("You must have an active unit to suicide"));
    }
}
