package org.xcore.plugin.gamemode.pvp;

import arc.files.Fi;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.Bundle;
import mindustry.game.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MiniPvPHudTest {

    @Test
    @DisplayName("HUD names teams in the viewer's language")
    void hudUsesLocalizedTeamNames() {
        Map<Team, Integer> alive = new LinkedHashMap<>();
        alive.put(Team.sharded, 3);
        alive.put(Team.crux, 2);

        String english = MiniPvP.teamsLine(alive, Locale.ENGLISH);
        String russian = MiniPvP.teamsLine(alive, Locale.of("ru"));

        assertThat(english).isEqualTo("[#" + Team.sharded.color + "]Sharded (3)[] [gray]vs[] [#" + Team.crux.color + "]Crux (2)[]");
        assertThat(russian).contains("Расколотые (3)").doesNotContain("sharded");

        Bundle bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi("src/main/resources/bundles"));
        assertThat(bundle.format(Locale.of("ru"), "pvp-hud-status", Args.of("teams", russian, "time", "01:05")))
                .contains("Живые:").contains("Расколотые (3)").contains("01:05");
    }
}
