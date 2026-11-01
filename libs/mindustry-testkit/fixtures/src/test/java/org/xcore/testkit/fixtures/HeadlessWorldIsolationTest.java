package org.xcore.testkit.fixtures;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Groups;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HeadlessWorldIsolationTest {

    @Test
    @Order(1)
    @DisplayName("First test creates core and player")
    void testPhaseOne() {
        try (HeadlessWorld world = HeadlessWorld.create(32, 32)) {
            world.addCore(Team.sharded, 10, 10);
            world.addPlayer("P1", Team.sharded);

            assertThat(Team.sharded.cores()).hasSize(1);
            assertThat(Groups.player.size()).isEqualTo(1);
        }

        // Post-close assertions: world singletons and groups must be empty/null
        assertThat(Vars.world).isNull();
        assertThat(Groups.player.size()).isZero();
    }

    @Test
    @Order(2)
    @DisplayName("Second test must start with clean slate, no leftover core or player from phase 1")
    void testPhaseTwo() {
        try (HeadlessWorld world = HeadlessWorld.create(32, 32)) {
            // Must be completely clean
            assertThat(Team.sharded.cores()).isEmpty();
            assertThat(Groups.player.size()).isZero();
            assertThat(world.players()).isEmpty();

            // World tiles must be fresh
            assertThat(world.tile(10, 10).block()).isEqualTo(Blocks.air);
        }
    }
}
