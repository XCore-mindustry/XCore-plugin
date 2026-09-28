package org.xcore.testkit.fixtures;

import arc.math.Mathf;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.net.Administration.ActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionFilterIntegrationTest {

    @Test
    @DisplayName("Should enforce core protection action filter headlessly")
    void shouldEnforceActionFilter() {
        try (HeadlessWorld world = HeadlessWorld.create(64, 64)) {
            // Core placed at (30, 30)
            world.addCore(Team.sharded, 30, 30);

            // Install anti-grief filter: non-admins cannot break blocks within 5 tiles of team core
            world.addActionFilter(action -> {
                if (action.type == ActionType.breakBlock && action.tile != null) {
                    var core = action.player.team().core();
                    if (core != null) {
                        float dist = Mathf.dst(action.tile.x, action.tile.y, core.tileX(), core.tileY());
                        if (dist <= 5) {
                            return action.player.admin;
                        }
                    }
                }
                return true;
            });

            MockPlayer guest = world.addPlayer("Guest", Team.sharded);
            MockPlayer admin = world.addAdmin("Admin", Team.sharded);

            // Place defensive wall near core at (32, 30)
            world.setBlock(32, 30, Blocks.copperWall, Team.sharded);
            var nearWall = world.tile(32, 30);

            // Place distant wall at (50, 50)
            world.setBlock(50, 50, Blocks.copperWall, Team.sharded);
            var farWall = world.tile(50, 50);

            // 1. Guest breaking wall near core -> rejected
            assertThat(world.allowAction(guest, ActionType.breakBlock, nearWall)).isFalse();

            // 2. Guest breaking distant wall -> allowed
            assertThat(world.allowAction(guest, ActionType.breakBlock, farWall)).isTrue();

            // 3. Admin breaking wall near core -> allowed
            assertThat(world.allowAction(admin, ActionType.breakBlock, nearWall)).isTrue();
        }
    }
}
