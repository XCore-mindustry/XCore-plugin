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
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.SortOrder;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.exception.NoSuchTargetException;
import org.xcore.cloud.mindustry.selector.exception.SelectorSenderRequirementException;
import org.xcore.cloud.mindustry.selector.exception.TooManyTargetsException;

public final class SpatialSelectorEngine {

    private final Rect queryRect = new Rect();
    private final IntSet deduplicationSet = new IntSet();

    public SpatialSelectorEngine() {}

    public @NonNull Seq<Player> resolvePlayers(@NonNull MindustrySender sender, @NonNull TargetSelectorSpec spec) {
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
        int limit = Math.min(spec.limit(), TargetSelectorSpec.MAX_ENTITIES_HARD_CAP);
        if (matches.size > limit) {
            matches.truncate(limit);
        }

        return matches;
    }

    public @NonNull Seq<Unit> resolveUnits(@NonNull MindustrySender sender, @NonNull TargetSelectorSpec spec) {
        Vec2 origin = resolveOrigin(sender, spec);
        float ox = origin.x;
        float oy = origin.y;

        Seq<Unit> matches = new Seq<>(false, 32);
        deduplicationSet.clear();

        Player senderPlayer = sender.player();
        boolean checkFog = senderPlayer != null && Vars.state != null && Vars.state.rules.fog;
        Team senderTeam = senderPlayer != null ? senderPlayer.team() : null;

        float minDist2 = spec.distanceMin() > 0f ? spec.distanceMin() * spec.distanceMin() : 0f;
        float maxDist2 = spec.distanceMax() < Float.MAX_VALUE ? spec.distanceMax() * spec.distanceMax() : Float.MAX_VALUE;
        float r = spec.distanceMax();

        int limit = Math.min(spec.limit(), TargetSelectorSpec.MAX_ENTITIES_HARD_CAP);

        if (spec.hasDistance() && r < Float.MAX_VALUE) {
            // Spatial QuadTree Accelerated Branch
            float size = r * 2f;
            queryRect.set(ox - r, oy - r, size, size);

            if (spec.team() != null && !spec.invertTeam()) {
                TeamData data = spec.team().data();
                if (data != null && data.unitTree != null) {
                    data.unitTree.intersect(queryRect, u -> {
                        filterAndAddUnit(u, spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, limit);
                    });
                }
            } else if (senderPlayer != null && spec.team() != null && spec.invertTeam() && spec.team() == senderTeam) {
                Units.nearbyEnemies(senderTeam, queryRect.x, queryRect.y, queryRect.width, queryRect.height, u -> {
                    filterAndAddUnit(u, spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, limit);
                });
            } else {
                Groups.unit.intersect(queryRect.x, queryRect.y, queryRect.width, queryRect.height, u -> {
                    filterAndAddUnit(u, spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, limit);
                });
            }
        } else if (spec.team() != null && !spec.invertTeam() && spec.unitType() != null && !spec.invertType()) {
            // Direct unitsByType array index
            TeamData data = spec.team().data();
            if (data != null && data.unitsByType != null && data.unitsByType.length > spec.unitType().id) {
                Seq<Unit> seq = data.unitsByType[spec.unitType().id];
                if (seq != null) {
                    for (int i = 0; i < seq.size && matches.size < limit; i++) {
                        filterAndAddUnit(seq.items[i], spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, limit);
                    }
                }
            }
        } else {
            // Linear scan bounded by safe ceiling
            int maxScan = 500;
            int scanned = 0;
            for (Unit u : Groups.unit) {
                filterAndAddUnit(u, spec, ox, oy, minDist2, maxDist2, checkFog, senderTeam, matches, limit);
                if (++scanned >= maxScan || matches.size >= limit) break;
            }
        }

        // Sorting
        applyUnitSorting(matches, spec.sort(), ox, oy);

        if (matches.size > limit) {
            matches.truncate(limit);
        }

        return matches;
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
            int limit
    ) {
        if (out.size >= limit) return;
        if (u == null || !u.isAdded() || u.dead || u.health <= 0f) return;

        // Deduplication for QuadTree boundary overlaps
        if (!deduplicationSet.add(u.id)) return;

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

        // Exact Clean Name
        Player found = Groups.player.find(p -> Strings.stripColors(p.plainName()).equalsIgnoreCase(nameOrId));
        if (found != null) return found;

        // Raw Name
        found = Groups.player.find(p -> p.plainName().equalsIgnoreCase(nameOrId));
        if (found != null) return found;

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
