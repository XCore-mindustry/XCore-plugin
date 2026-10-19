package org.xcore.plugin.gamemode.pvp.guide;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.core.GameState;
import mindustry.game.Rules;
import mindustry.game.Team;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.Multiplier;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.TeamCount;
import org.xcore.plugin.gamemode.pvp.rating.MiniPvPMatchTracker;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.testkit.fixtures.HeadlessContent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniPvPModeFactsTest {

    private static final RatingPolicy POLICY = RatingPolicy.teamEloV1();
    private static final List<TeamCount> TEAMS = List.of(new TeamCount(Team.sharded.id, 3),
            new TeamCount(Team.crux.id, 2));

    @BeforeAll
    static void initializeMindustryContent() {
        HeadlessContent.ensureLoaded();
        if (Vars.state == null) {
            Vars.state = new GameState();
        }
    }

    @Test
    void vanillaRulesAreNotReportedAsFeatures() {
        MiniPvPModeFacts facts = MiniPvPModeFacts.read(new Rules(), "Glacier", 125, TEAMS, POLICY);

        assertThat(facts.multipliers()).isEmpty();
        assertThat(facts.bannedBlocks()).isEmpty();
        assertThat(facts.bannedUnits()).isEmpty();
        assertThat(facts.unitCapChanged()).isFalse();
        assertThat(facts.fog()).isFalse();
        assertThat(facts.coreCapture()).isFalse();
        assertThat(facts.reactorExplosions()).isTrue();
        assertThat(facts.coreBuildRadius()).isEqualTo(Math.round(new Rules().enemyCoreBuildRadius / Vars.tilesize));
    }

    @Test
    void readsTheRoundAndTheRulesThatDiffer() {
        Rules rules = new Rules();
        rules.unitDamageMultiplier = 1.5f;
        rules.blockHealthMultiplier = 0.5f;
        rules.unitCap = 40;
        rules.unitCapVariable = false;
        rules.enemyCoreBuildRadius = 20 * Vars.tilesize;
        rules.fog = true;
        rules.reactorExplosions = false;
        rules.bannedBlocks.addAll(Blocks.ripple, Blocks.foreshadow);
        rules.bannedUnits.add(UnitTypes.quad);

        MiniPvPModeFacts facts = MiniPvPModeFacts.read(rules, "Glacier", 125, TEAMS, POLICY);

        assertThat(facts.map()).isEqualTo("Glacier");
        assertThat(facts.roundMinutes()).isEqualTo(2);
        assertThat(facts.teams()).isEqualTo(TEAMS);
        assertThat(facts.multipliers()).containsExactly(
                new Multiplier("unit-damage", 150), new Multiplier("block-health", 50));
        assertThat(facts.unitCapChanged()).isTrue();
        assertThat(facts.unitCap()).isEqualTo(40);
        assertThat(facts.coreBuildRadius()).isEqualTo(20);
        assertThat(facts.fog()).isTrue();
        assertThat(facts.reactorExplosions()).isFalse();
        assertThat(facts.bannedBlocks()).extracting(MiniPvPModeFacts.ContentRef::region)
                .containsExactly("block-foreshadow-ui", "block-ripple-ui");
        assertThat(facts.bannedUnits()).extracting(MiniPvPModeFacts.ContentRef::region)
                .containsExactly("unit-quad-ui");
    }

    @Test
    void aWhitelistIsNotABan() {
        Rules rules = new Rules();
        rules.blockWhitelist = true;
        rules.bannedBlocks.add(Blocks.duo);

        assertThat(MiniPvPModeFacts.read(rules, "Glacier", 0, TEAMS, POLICY).bannedBlocks()).isEmpty();
    }

    @Test
    void theRatingRulesAreTheOnesTheTrackerApplies() {
        MiniPvPModeFacts.Rating rating = MiniPvPModeFacts.read(new Rules(), null, 0, List.of(), POLICY).rating();

        assertThat(rating.startingRating()).isEqualTo(POLICY.defaultRating());
        assertThat(rating.minimumPlayers()).isEqualTo(POLICY.minimumPlayers());
        assertThat(rating.minimumSeconds()).isEqualTo(POLICY.minimumPlayTimeSeconds());
        assertThat(rating.leftAtOnceSeconds() * 1000L).isEqualTo(MiniPvPMatchTracker.LEFT_AT_ONCE_MS);
        assertThat(rating.winnerPercent()).isEqualTo(50);
        assertThat(rating.lateJoinPercent()).isEqualTo(25);
    }
}
