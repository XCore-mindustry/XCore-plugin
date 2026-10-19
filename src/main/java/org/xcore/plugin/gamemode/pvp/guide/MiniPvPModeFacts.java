package org.xcore.plugin.gamemode.pvp.guide;

import com.ospx.flubundle.mindustry.ContentNames;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Rules;
import mindustry.type.UnitType;
import mindustry.world.Block;
import org.xcore.plugin.gamemode.pvp.rating.MiniPvPMatchTracker;
import org.xcore.plugin.rating.RatingPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What a player has to know about the running MiniPvP round: where it stands, how its rules
 * differ from vanilla PvP, and how it is rated.
 *
 * <p>Read from the live rules and the rating code rather than written into the texts: the rules
 * come with each map and the server's settings, so the guide shows the ones of the round that is
 * being played. A rule that matches vanilla is left out instead of presented as a feature.
 *
 * @param map                   name of the map, or {@code null} between rounds
 * @param roundSeconds          how long the round has been going, 0 when it has not started
 * @param teams                 the teams still in the round
 * @param unitCap               the base unit cap of a team
 * @param unitCapVariable       whether every core raises the cap
 * @param coreBuildRadius       tiles around an enemy core nobody else may build in, 0 for none
 * @param polygonCoreProtection whether the land is split between cores instead of circles around them
 * @param multipliers           the rule multipliers that differ from vanilla
 * @param bannedBlocks          blocks nobody may build
 * @param bannedUnits           units nobody may make
 * @param fog                   whether the map is hidden outside what a team sees
 * @param coreCapture           whether a destroyed core goes to the team that destroyed it
 * @param reactorExplosions     whether reactors explode
 */
public record MiniPvPModeFacts(
        String map,
        int roundSeconds,
        List<TeamCount> teams,
        int unitCap,
        boolean unitCapVariable,
        int coreBuildRadius,
        boolean polygonCoreProtection,
        List<Multiplier> multipliers,
        List<ContentRef> bannedBlocks,
        List<ContentRef> bannedUnits,
        boolean fog,
        boolean coreCapture,
        boolean reactorExplosions,
        Rating rating) {

    public MiniPvPModeFacts {
        teams = List.copyOf(teams);
        multipliers = List.copyOf(multipliers);
        bannedBlocks = List.copyOf(bannedBlocks);
        bannedUnits = List.copyOf(bannedUnits);
    }

    /** A team still in the round and the players on it. */
    public record TeamCount(int teamId, int players) {
    }

    /**
     * A rule multiplier that differs from vanilla.
     *
     * @param key     names the rule in the bundle, {@code pvp-guide-rule-<key>}
     * @param percent the multiplier as a percentage of vanilla
     */
    public record Multiplier(String key, int percent) {
    }

    /**
     * How a round is rated.
     *
     * @param startingRating     rating of a player's first rated round
     * @param minimumPlayers     rated players a round needs to be rated
     * @param minimumSeconds     length a round needs to be rated
     * @param leftAtOnceSeconds  play a loser needs before the loss counts
     * @param winnerPercent      share of the round a winner has to play to gain
     * @param lateJoinPercent    share of the round under which a late loser loses nothing
     */
    public record Rating(int startingRating, int minimumPlayers, int minimumSeconds, int leftAtOnceSeconds,
                         int winnerPercent, int lateJoinPercent) {

        static Rating of(RatingPolicy policy) {
            return new Rating(policy.defaultRating(), policy.minimumPlayers(), policy.minimumPlayTimeSeconds(),
                    (int) (MiniPvPMatchTracker.LEFT_AT_ONCE_MS / 1000L),
                    (int) Math.round(MiniPvPMatchTracker.WINNER_SHARE * 100),
                    (int) Math.round(MiniPvPMatchTracker.LATE_JOIN_SHARE * 100));
        }
    }

    /**
     * A piece of content as the client can draw it.
     *
     * @param region atlas region of its UI icon
     */
    public record ContentRef(String region) {
        static ContentRef of(UnlockableContent content) {
            return new ContentRef(content.getContentType().name() + "-" + content.name + "-ui");
        }

        /** The game bundle key of its name, {@code unit-flare-ui} → {@code unit.flare.name}. */
        public String nameKey() {
            String id = region.endsWith("-ui") ? region.substring(0, region.length() - 3) : region;
            int dash = id.indexOf('-');
            return dash < 0 ? id + ".name" : id.substring(0, dash) + "." + id.substring(dash + 1) + ".name";
        }

        /** Its name in {@code locale} from the game's own translations, or its internal name. */
        public String name(Locale locale) {
            String name = ContentNames.vanilla().get(nameKey(), locale);
            if (name != null) {
                return name;
            }
            String key = nameKey();
            return key.substring(key.indexOf('.') + 1, key.length() - ".name".length());
        }
    }

    /**
     * The facts of the round that is running now; call on the game thread.
     *
     * @param teams the teams still in the round, as the mode counts them
     */
    public static MiniPvPModeFacts current(List<TeamCount> teams, int roundSeconds, RatingPolicy policy) {
        String map = Vars.state != null && Vars.state.map != null && !Vars.state.isMenu()
                ? Vars.state.map.plainName() : null;
        return read(Vars.state.rules, map, roundSeconds, teams, policy);
    }

    static MiniPvPModeFacts read(Rules rules, String map, int roundSeconds, List<TeamCount> teams,
                                 RatingPolicy policy) {
        Rules vanilla = new Rules();

        List<Multiplier> multipliers = new ArrayList<>();
        multiplier(multipliers, "unit-damage", rules.unitDamageMultiplier, vanilla.unitDamageMultiplier);
        multiplier(multipliers, "unit-health", rules.unitHealthMultiplier, vanilla.unitHealthMultiplier);
        multiplier(multipliers, "unit-cost", rules.unitCostMultiplier, vanilla.unitCostMultiplier);
        multiplier(multipliers, "unit-build-speed", rules.unitBuildSpeedMultiplier, vanilla.unitBuildSpeedMultiplier);
        multiplier(multipliers, "block-damage", rules.blockDamageMultiplier, vanilla.blockDamageMultiplier);
        multiplier(multipliers, "block-health", rules.blockHealthMultiplier, vanilla.blockHealthMultiplier);
        multiplier(multipliers, "build-cost", rules.buildCostMultiplier, vanilla.buildCostMultiplier);
        multiplier(multipliers, "build-speed", rules.buildSpeedMultiplier, vanilla.buildSpeedMultiplier);

        List<ContentRef> bannedBlocks = new ArrayList<>();
        // With a whitelist the set lists what is allowed, which is not something to warn about.
        if (!rules.blockWhitelist) {
            for (Block block : rules.bannedBlocks) {
                bannedBlocks.add(ContentRef.of(block));
            }
        }
        bannedBlocks.sort(Comparator.comparing(ContentRef::region));
        List<ContentRef> bannedUnits = new ArrayList<>();
        if (!rules.unitWhitelist) {
            for (UnitType unit : rules.bannedUnits) {
                bannedUnits.add(ContentRef.of(unit));
            }
        }
        bannedUnits.sort(Comparator.comparing(ContentRef::region));

        return new MiniPvPModeFacts(
                map,
                Math.max(0, roundSeconds),
                teams,
                rules.unitCap,
                rules.unitCapVariable,
                Math.round(rules.enemyCoreBuildRadius / Vars.tilesize),
                rules.polygonCoreProtection,
                multipliers,
                bannedBlocks,
                bannedUnits,
                rules.fog,
                rules.coreCapture,
                rules.reactorExplosions,
                Rating.of(policy));
    }

    private static void multiplier(List<Multiplier> into, String key, float value, float vanilla) {
        int percent = Math.round(value * 100f);
        if (percent != Math.round(vanilla * 100f)) {
            into.add(new Multiplier(key, percent));
        }
    }

    /** Whether the unit cap is not the vanilla one: none to start with, raised by every core. */
    public boolean unitCapChanged() {
        Rules vanilla = new Rules();
        return unitCap != vanilla.unitCap || unitCapVariable != vanilla.unitCapVariable;
    }

    public int roundMinutes() {
        return roundSeconds / 60;
    }
}
