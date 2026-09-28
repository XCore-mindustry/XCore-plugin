package org.xcore.testkit.fixtures;

import mindustry.game.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xcore.testkit.fixtures.junit.HeadlessWorldExtension;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(HeadlessWorldExtension.class)
@WithHeadlessWorld(width = 48, height = 48)
class HeadlessWorldExtensionTest {

    @Test
    @DisplayName("Should inject initialized HeadlessWorld matching annotation dimensions")
    void shouldInjectWorld(HeadlessWorld world) {
        assertThat(world).isNotNull();
        assertThat(world.width()).isEqualTo(48);
        assertThat(world.height()).isEqualTo(48);

        MockPlayer player = world.addPlayer("Injected", Team.sharded);
        assertThat(player.name()).isEqualTo("Injected");
        assertThat(world.players()).hasSize(1);
    }
}
