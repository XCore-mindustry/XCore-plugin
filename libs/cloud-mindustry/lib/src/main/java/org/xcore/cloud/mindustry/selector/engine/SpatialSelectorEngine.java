package org.xcore.cloud.mindustry.selector.engine;

import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.SimulationExecutor;
import org.xcore.cloud.mindustry.parser.PlayerNameInput;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.SortOrder;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.exception.NoSuchTargetException;
import org.xcore.cloud.mindustry.selector.exception.SelectorSenderRequirementException;
import org.xcore.cloud.mindustry.selector.exception.TooManyTargetsException;

/**
 * Resolves selectors against the live game state. Every resolution has to happen on the
 * engine's simulation thread; nothing here waits for it or hops to it.
 */
public final class SpatialSelectorEngine {

    private final SimulationExecutor simulation;

    /**
     * An engine for the running application; see {@link SimulationExecutor#forCurrentApplication()}.
     */
    public SpatialSelectorEngine() {
        this(SimulationExecutor.forCurrentApplication());
    }

    public SpatialSelectorEngine(@NonNull SimulationExecutor simulation) {
        this.simulation = simulation;
    }

    public @NonNull Seq<Player> resolvePlayers(@NonNull MindustrySender sender, @NonNull TargetSelectorSpec spec) {
        return resolvePlayers(sender, spec, TargetSelectorSpec.MAX_ENTITIES_HARD_CAP);
    }

    /** @param cap the most players returned, whatever the spec's limit */
    private Seq<Player> resolvePlayers(MindustrySender sender, TargetSelectorSpec spec, int cap) {
        simulation.requireOnThread();

        // 1. Literal Resolution
        if (spec.kind() == SelectorKind.LITERAL_PLAYER) {
            Player literal = resolveLiteralPlayer(spec.literalName(), sender);
            if (literal == null) {
                throw new NoSuchTargetException(spec.literalName() == null ? "" : spec.literalName());
            }
            return Seq.with(literal);
        }

        // 2. Self Selector (@s)
        if (spec.kind() == SelectorKind.SELF) {
            Player p = sender.player();
            if (p == null) {
                throw new SelectorSenderRequirementException(SelectorKind.SELF, sender.name(), "Console sender is not a player");
            }
            return Seq.with(p);
        }

        // 3. Resolve Origin (ox, oy)
        Vec2 origin = resolveOrigin(sender, spec);
        float ox = origin.x;
        float oy = origin.y;

        // 4. Candidate Collection
        Seq<Player> matches = new Seq<>(false, 16);
        Player senderPlayer = sender.player();
        boolean checkFog = senderPlayer != null && Vars.state != null && Vars.state.rules.fog;
        Team senderTeam = senderPlayer != null ? senderPlayer.team() : null;

        float minDist2 = spec.distanceMin() > 0f ? spec.distanceMin() * spec.distanceMin() : 0f;
        float maxDist2 = spec.distanceMax() < Float.MAX_VALUE ? spec.distanceMax() * spec.distanceMax() : Float.MAX_VALUE;

        for (Player p : Groups.player) {
            if (p == null || !p.isAdded()) continue;

            // Admin check
            if (spec.admin() != null && p.admin != spec.admin()) continue;

            // Name check
            if (spec.name() != null) {
                String cleanName = Strings.stripColors(p.plainName());
                boolean nameMatch = cleanName.equalsIgnoreCase(spec.name());
                if (spec.invertName() ? nameMatch : !nameMatch) continue;
            }

            // Team check
            if (spec.team() != null) {
                boolean teamMatch = p.team() == spec.team();
                if (spec.invertTeam() ? teamMatch : !teamMatch) continue;
            }

            // Distance check
            if (spec.hasDistance() || spec.sort() == SortOrder.NEAREST || spec.sort() == SortOrder.FURTHEST) {
                float d2 = Mathf.dst2(p.x, p.y, ox, oy);
                if (d2 < minDist2 || d2 > maxDist2) continue;
            }

            // Fog of War check
            if (checkFog && p.unit() != null && p.unit().inFogTo(senderTeam)) continue;

            matches.add(p);
        }

        // 5. Sorting
        applyPlayerSorting(matches, spec.sort(), ox, oy);

        // 6. Limit Truncation
        int limit = Math.min(spec.limit(), cap);
        if (matches.size > limit) {
            matches.truncate(limit);
        }

        return matches;
    }

    /**
     * Resolves a selector to units. {@code @e} selects any unit; the player selectors
     * ({@code @s}, {@code @p}, {@code @r}, {@code @a}, names and {@code #id}s) select the units the
     * matching players control.
     */
    public @NonNull Seq<Unit> resolveUnits(@NonNull MindustrySender sender, @NonNull TargetSelectorSpec spec) {
        simulation.requireOnThread();

        if (spec.kind() != SelectorKind.ALL_ENTITIES) {
            return resolvePlayerUnits(sender, spec);
        }

        Vec2 origin = resolveOrigin(sender, spec);
        float ox = origin.x;
        float oy = origin.y;

        Seq<Unit> matches = new Seq<>(false, 32);
        // Scratch state belongs to this call: a resolution may start another one.
        IntSet seen = new IntSet();

        Player senderPlayer = sender.player();
        boolean checkFog = senderPlayer != null && Vars.state != null && Vars.state.rules.fog;
        Team senderTeam = senderPlayer != null ? senderPlayer.team() : null;

        float minDist2 = spec.distanceMin() > 0f ? spec.distanceMin() * spec.distanceMin() : 0f;
        float maxDist2 = spec.distanceMax() < Float.MAX_VALUE ? spec.distanceMax() * spec.distanceMax() : Float.MAX_VALUE;
        float r = spec.distanceMax();

        int limit = Math.min(spec.limit(), TargetSelectorSpec.MAX_ENTITIES_HARD_CAP);
        // A sorted selector has to see every candidate before the limit applies, or "nearest"
        // would mean "nearest of the first few found".
        int collectLimit = spec.sort() == SortOrder.ARBITRARY ? limit : Integer.MAX_VALUE;

        if (spec.hasDistance() && r < Float.MAX_VALUE) {
            // Spatial QuadTree Accelerated Branch
            float size = r * 2f;
            Rect queryRect = new Rect(ox - r, oy - r, size, size);

            if (spec.team() != null && !spec.invertTeam()) {
                TeamData data = spec.team().data();
                if (data != null && data.unitTree != null) {
                    data.unitTree.intersect(queryRect, u -> {
                        filterAndAddUnit(u, spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, seen, collectLimit);
                    });
                }
            } else if (senderPlayer != null && spec.team() != null && spec.invertTeam() && spec.team() == senderTeam) {
                Units.nearbyEnemies(senderTeam, queryRect.x, queryRect.y, queryRect.width, queryRect.height, u -> {
                    filterAndAddUnit(u, spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, seen, collectLimit);
                });
            } else {
                Groups.unit.intersect(queryRect.x, queryRect.y, queryRect.width, queryRect.height, u -> {
                    filterAndAddUnit(u, spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, seen, collectLimit);
                });
            }
        } else if (spec.team() != null && !spec.invertTeam() && spec.unitType() != null && !spec.invertType()) {
            // Direct unitsByType array index
            TeamData data = spec.team().data();
            if (data != null && data.unitsByType != null && data.unitsByType.length > spec.unitType().id) {
                Seq<Unit> seq = data.unitsByType[spec.unitType().id];
                if (seq != null) {
                    for (int i = 0; i < seq.size && matches.size < collectLimit; i++) {
                        filterAndAddUnit(seq.items[i], spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, seen, collectLimit);
                    }
                }
            }
        } else {
            for (Unit u : Groups.unit) {
                filterAndAddUnit(u, spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, seen, collectLimit);
                if (matches.size >= collectLimit) break;
            }
        }

        // Sorting
        applyUnitSorting(matches, spec.sort(), ox, oy);

        if (matches.size > limit) {
            matches.truncate(limit);
        }

        return matches;
    }

    /**
     * The units of the players a player selector matches. Every matching player is considered, so
     * that players without a unit, or with a unit of the wrong team or type, do not use up the limit.
     */
    private Seq<Unit> resolvePlayerUnits(MindustrySender sender, TargetSelectorSpec spec) {
        int limit = Math.min(spec.limit(), TargetSelectorSpec.MAX_ENTITIES_HARD_CAP);
        Seq<Player> players = resolvePlayers(sender, spec.withLimit(Integer.MAX_VALUE), Integer.MAX_VALUE);

        Seq<Unit> units = new Seq<>(false, Math.min(players.size, limit));
        for (Player player : players) {
            if (units.size >= limit) break;
            Unit unit = player.unit();
            if (unit == null || !unit.isAdded() || unit.dead) continue;
            // @s and literals skip the player filters, so the team is checked on the unit too.
            if (spec.team() != null) {
                boolean teamMatch = unit.team == spec.team();
                if (spec.invertTeam() ? teamMatch : !teamMatch) continue;
            }
            if (spec.unitType() != null) {
                boolean typeMatch = unit.type == spec.unitType();
                if (spec.invertType() ? typeMatch : !typeMatch) continue;
            }
            units.add(unit);
        }
        return units;
    }

    private void filterAndAddUnit(
            Unit u,
            TargetSelectorSpec spec,
            float ox,
            float oy,
            float minDist2,
            float maxDist2,
            boolean checkFog,
            Team senderTeam,
            Seq<Unit> out,
            IntSet seen,
            int limit
    ) {
        if (out.size >= limit) return;
        if (u == null || !u.isAdded() || u.dead || u.health <= 0f) return;

        // Deduplication for QuadTree boundary overlaps
        if (!seen.add(u.id)) return;

        // Unit type check
        if (spec.unitType() != null) {
            boolean typeMatch = u.type == spec.unitType();
            if (spec.invertType() ? typeMatch : !typeMatch) return;
        }

        // Team check
        if (spec.team() != null) {
            boolean teamMatch = u.team == spec.team();
            if (spec.invertTeam() ? teamMatch : !teamMatch) return;
        }

        // Distance check
        if (spec.hasDistance() || spec.sort() == SortOrder.NEAREST || spec.sort() == SortOrder.FURTHEST) {
            float d2 = Mathf.dst2(u.x, u.y, ox, oy);
            if (d2 < minDist2 || d2 > maxDist2) return;
        }

        // Fog check
        if (checkFog && u.inFogTo(senderTeam)) return;

        out.add(u);
    }

    private Vec2 resolveOrigin(MindustrySender sender, TargetSelectorSpec spec) {
        float ox = 0f;
        float oy = 0f;
        Player p = sender.player();

        if (spec.hasExplicitOrigin()) {
            if (spec.relativeX()) {
                if (p == null) throw new SelectorSenderRequirementException(spec.kind(), sender.name(), "Relative coordinate '~' requires a player sender");
                ox = p.x + spec.originX();
            } else {
                ox = spec.originX();
            }

            if (spec.relativeY()) {
                if (p == null) throw new SelectorSenderRequirementException(spec.kind(), sender.name(), "Relative coordinate '~' requires a player sender");
                oy = p.y + spec.originY();
            } else {
                oy = spec.originY();
            }
        } else if (p != null) {
            ox = p.x;
            oy = p.y;
        } else {
            // Console fallback: team sharded core or map center
            if (Vars.state != null && Vars.state.teams != null) {
                TeamData sharded = Vars.state.teams.get(Team.sharded);
                if (sharded != null && sharded.cores != null && sharded.cores.size > 0 && sharded.cores.first() != null) {
                    ox = sharded.cores.first().x;
                    oy = sharded.cores.first().y;
                } else if (Vars.world != null) {
                    ox = Vars.world.width() * Vars.tilesize / 2f;
                    oy = Vars.world.height() * Vars.tilesize / 2f;
                }
            }
        }

        return new Vec2(ox, oy);
    }

    private Player resolveLiteralPlayer(String nameOrId, MindustrySender sender) {
        if (nameOrId == null || nameOrId.isEmpty()) return null;

        // Check #ID
        if (nameOrId.startsWith("#")) {
            int id = Strings.parseInt(nameOrId.substring(1), -1);
            if (id != -1) {
                Player p = Groups.player.getByID(id);
                if (p != null) return p;
            }
        }

        // The exact name first (no colors, any case), so "Big  Bob" does not select "Big Bob"
        Player found = Groups.player.find(p -> Strings.stripColors(p.plainName()).equalsIgnoreCase(nameOrId));
        if (found != null) return found;

        // Then as PlayerNameInput reads it, spacing ignored as well; two players who differ only
        // in spacing make the name ambiguous rather than picking one of them
        Seq<Player> loose = new Seq<>();
        for (Player p : Groups.player) {
            if (PlayerNameInput.matches(p.plainName(), nameOrId)) loose.add(p);
        }
        if (loose.size > 1) {
            throw new TooManyTargetsException(nameOrId,
                    "'" + nameOrId + "' matches " + loose.size + " players; type the exact name or use #id");
        }
        if (loose.size == 1) return loose.first();

        // Console UUID / IP match
        if (!sender.isPlayer()) {
            found = Groups.player.find(p -> p.uuid().equals(nameOrId) || (p.con != null && p.con.address.equals(nameOrId)));
            if (found != null) return found;
        }

        return null;
    }

    private void applyPlayerSorting(Seq<Player> players, SortOrder sort, float ox, float oy) {
        if (sort == SortOrder.NEAREST) {
            players.sort(p -> Mathf.dst2(p.x, p.y, ox, oy));
        } else if (sort == SortOrder.FURTHEST) {
            players.sort(p -> -Mathf.dst2(p.x, p.y, ox, oy));
        } else if (sort == SortOrder.RANDOM) {
            players.shuffle();
        } else if (sort == SortOrder.HEALTH_ASC) {
            players.sort(p -> (p.unit() != null && p.unit().maxHealth > 0f ? p.unit().health / p.unit().maxHealth : 0f));
        } else if (sort == SortOrder.HEALTH_DESC) {
            players.sort(p -> -(p.unit() != null && p.unit().maxHealth > 0f ? p.unit().health / p.unit().maxHealth : 0f));
        }
    }

    private void applyUnitSorting(Seq<Unit> units, SortOrder sort, float ox, float oy) {
        if (sort == SortOrder.NEAREST) {
            units.sort(u -> Mathf.dst2(u.x, u.y, ox, oy));
        } else if (sort == SortOrder.FURTHEST) {
            units.sort(u -> -Mathf.dst2(u.x, u.y, ox, oy));
        } else if (sort == SortOrder.RANDOM) {
            units.shuffle();
        } else if (sort == SortOrder.HEALTH_ASC) {
            units.sort(u -> u.maxHealth > 0f ? u.health / u.maxHealth : 0f);
        } else if (sort == SortOrder.HEALTH_DESC) {
            units.sort(u -> -(u.maxHealth > 0f ? u.health / u.maxHealth : 0f));
        }
    }
}
