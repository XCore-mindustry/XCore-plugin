package org.xcore.testkit.fixtures;

import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeadlessWorldTest {

    @Test
    @DisplayName("Should create world with valid dimensions and accessible tiles")
    void shouldCreateWorldWithDimensions() {
        try (HeadlessWorld world = HeadlessWorld.create(20, 30)) {
            assertThat(world.width()).isEqualTo(20);
            assertThat(world.height()).isEqualTo(30);

            assertThat(world.tile(0, 0)).isNotNull();
            assertThat(world.tile(19, 29)).isNotNull();
            assertThat(world.tile(20, 30)).isNull(); // out of bounds

            assertThat(world.tileWorld(0f, 0f)).isEqualTo(world.tile(0, 0));
            assertThat(world.tileWorld(80f, 80f)).isEqualTo(world.tile(10, 10));
        }
    }

    @Test
    @DisplayName("Should default to a non-generating world and support generating mode")
    void shouldExposeGeneratingMode() {
        try (HeadlessWorld world = HeadlessWorld.create(8, 8)) {
            assertThat(world.world().isGenerating()).isFalse();
        }

        try (HeadlessWorld world = HeadlessWorld.builder()
                .dimensions(8, 8)
                .generating(true)
                .build()) {
            assertThat(world.world().isGenerating()).isTrue();
        }
    }

    @Test
    @DisplayName("Should allow tile mutation in generating mode without a live net layer")
    void shouldMutateTilesWhileGenerating() {
        try (HeadlessWorld world = HeadlessWorld.builder()
                .dimensions(8, 8)
                .generating(true)
                .defaultFloor(Blocks.stone)
                .build()) {
            // Tile.setFloor consults Vars.world.isGenerating(); generating mode must
            // permit bulk map-authoring writes without event/group side effects.
            world.setFloor(3, 3, Blocks.sand);
            world.setBlock(4, 4, Blocks.coreShard, Team.green);

            assertThat(world.tile(3, 3).floor()).isEqualTo(Blocks.sand);
            assertThat(world.tile(4, 4).block()).isEqualTo(Blocks.coreShard);
        }
    }

    @Test
    @DisplayName("Should support setting floors, blocks, and air")
    void shouldModifyTiles() {
        try (HeadlessWorld world = HeadlessWorld.create(16, 16)) {
            world.fillFloor(Blocks.stone);
            assertThat(world.tile(5, 5).floor()).isEqualTo(Blocks.stone);

            world.setFloor(5, 5, Blocks.dirt);
            assertThat(world.tile(5, 5).floor()).isEqualTo(Blocks.dirt);

            world.setBlock(5, 5, Blocks.copperWall, Team.sharded);
            assertThat(world.tile(5, 5).block()).isEqualTo(Blocks.copperWall);
            assertThat(world.tile(5, 5).team()).isEqualTo(Team.sharded);
            assertThat(world.build(5, 5)).isNotNull();

            world.setAir(5, 5);
            assertThat(world.tile(5, 5).block()).isEqualTo(Blocks.air);
            assertThat(world.build(5, 5)).isNull();
        }
    }

    @Test
    @DisplayName("Should manage team cores and multi-tile footprints")
    void shouldManageCores() {
        try (HeadlessWorld world = HeadlessWorld.create(32, 32)) {
            assertThat(Team.sharded.cores()).isEmpty();

            CoreBlock.CoreBuild core = world.addCore(Team.sharded, 10, 10);
            assertThat(core).isNotNull();
            assertThat(Team.sharded.cores()).hasSize(1);
            assertThat(Team.sharded.core()).isSameAs(core);
            assertThat(core.tileX()).isEqualTo(10);
            assertThat(core.tileY()).isEqualTo(10);

            // CoreShard is 3x3: check center and neighbors
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    assertThat(world.tile(10 + dx, 10 + dy).build)
                            .as("Tile (%d,%d) must point to core", 10 + dx, 10 + dy)
                            .isSameAs(core);
                }
            }

            world.setAir(10, 10);
            assertThat(Team.sharded.cores()).isEmpty();
            assertThat(world.build(10, 10)).isNull();
        }
    }

    @Test
    @DisplayName("Should add and remove players tracking Groups.player")
    void shouldManagePlayers() {
        try (HeadlessWorld world = HeadlessWorld.create(16, 16)) {
            assertThat(world.players()).isEmpty();
            assertThat(Groups.player.size()).isZero();

            MockPlayer alice = world.addPlayer("Alice", Team.sharded);
            assertThat(world.players()).containsExactly(alice);
            assertThat(Groups.player.size()).isEqualTo(1);
            assertThat(Groups.player.getByID(alice.id())).isEqualTo(alice.player());

            MockPlayer bob = world.addAdmin("Bob", Team.crux);
            assertThat(bob.isAdmin()).isTrue();
            assertThat(bob.team()).isEqualTo(Team.crux);
            assertThat(Groups.player.size()).isEqualTo(2);

            world.removePlayer(alice);
            assertThat(world.players()).containsExactly(bob);
            assertThat(Groups.player.size()).isEqualTo(1);
            assertThat(Groups.player.getByID(alice.id())).isNull();
        }
    }

    @Test
    @DisplayName("Should throw IllegalStateException when used after close")
    void shouldThrowWhenClosed() {
        HeadlessWorld world = HeadlessWorld.create(16, 16);
        world.close();

        assertThatThrownBy(world::width).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> world.tile(0, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> world.addPlayer("Alice", Team.sharded)).isInstanceOf(IllegalStateException.class);
    }
}
