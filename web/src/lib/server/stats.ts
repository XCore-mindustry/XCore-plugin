import "server-only";
import { ObjectId, type Db, type Document } from "mongodb";
import { getGameDb } from "./db";
import type {
  LeaderboardCategory,
  LeaderboardEntry,
  LeaderboardResult,
  LinkedProfile,
  MatchFilters,
  ModeStatsSummaryDTO,
  PaginatedResult,
  PlayerProfile,
  PlayerSearchResult,
  PublicMatchDetail,
  PublicMatchParticipant,
  PublicMatchSummary,
} from "./stats-types";
import { isPid, NO_PID, parsePid } from "@/lib/pid";

const MAX_PAGE_SIZE = 50;
const DEFAULT_PAGE_SIZE = 25;
const MAX_PAGE_NUMBER = 1_000;
const PUBLIC_QUERY_TIMEOUT_MS = 2_000;

export function escapeRegex(text: string): string {
  return text.replace(/[-\\/\\^$*+?.()|[\]{}]/g, "\\$&");
}

export function safeInt(value: unknown, fallback = 0): number {
  if (value === null || value === undefined || value === "") return fallback;
  const num = Number(value);
  return Number.isFinite(num) ? Math.floor(num) : fallback;
}

export function safeIsoDate(value: unknown): string | null {
  if (!value) return null;
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value.toISOString();
  }
  if (typeof value === "number") {
    if (value <= 0) return null;
    const d = new Date(value);
    return isNaN(d.getTime()) ? null : d.toISOString();
  }
  if (typeof value === "string") {
    const d = new Date(value);
    return isNaN(d.getTime()) ? null : d.toISOString();
  }
  return null;
}

export function safeObjectIdString(value: unknown): string | null {
  if (!value) return null;
  if (typeof value === "string") return value;
  if (typeof value === "object" && "toHexString" in (value as { toHexString?: () => string })) {
    const hex = (value as { toHexString: () => string }).toHexString();
    if (typeof hex === "string") return hex;
  }
  return String(value);
}

function isObjectIdHex(value: unknown): boolean {
  return typeof value === "string" && /^[0-9a-fA-F]{24}$/.test(value);
}

function toObjectId(value: unknown): ObjectId | null {
  if (!value) return null;
  if (value instanceof ObjectId) return value;
  if (typeof value === "string" && /^[0-9a-fA-F]{24}$/.test(value)) {
    try {
      return new ObjectId(value);
    } catch {
      return null;
    }
  }
  return null;
}

export function formatPlayTime(totalMinutes: number): string {
  if (totalMinutes <= 0) return "0m";
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;

  const parts: string[] = [];
  if (days > 0) parts.push(`${days}d`);
  if (hours > 0) parts.push(`${hours}h`);
  if (minutes > 0 || parts.length === 0) parts.push(`${minutes}m`);
  return parts.join(" ");
}

function clampPage(page: unknown): number {
  const p = Number(page);
  return Number.isSafeInteger(p) && p > 0 ? Math.min(p, MAX_PAGE_NUMBER) : 1;
}

function clampPageSize(pageSize: unknown, defaultSize = DEFAULT_PAGE_SIZE): number {
  const s = Number(pageSize);
  if (!Number.isSafeInteger(s) || s < 1) return defaultSize;
  return Math.min(s, MAX_PAGE_SIZE);
}

async function resolveDb(db?: Db): Promise<Db> {
  return db ?? (await getGameDb());
}

/**
 * Searches players with bounded pagination and safe regex escaping.
 * Never exposes UUIDs, Discord IDs, passwords, IPs or device tokens.
 */
export async function searchPlayers(
  query: string,
  page = 1,
  pageSize = DEFAULT_PAGE_SIZE,
  db?: Db,
): Promise<PaginatedResult<PlayerSearchResult>> {
  const database = await resolveDb(db);
  const playersCol = database.collection("players");

  const sanitized = query.trim().slice(0, 64);
  const clampedPage = clampPage(page);
  const clampedSize = clampPageSize(pageSize);

  if (!sanitized) {
    return {
      items: [],
      page: clampedPage,
      pageSize: clampedSize,
      total: 0,
      totalPages: 0,
    };
  }

  const pidNumber = parsePid(sanitized);
  const isNumeric = pidNumber !== null;
  const escaped = escapeRegex(sanitized);
  const rx = new RegExp(escaped, "i");

  const filter = isNumeric
    ? {
        $and: [
          { pid: { $gt: NO_PID } },
          {
            $or: [
              { pid: pidNumber },
              { nickname: { $regex: rx } },
              { username: { $regex: rx } },
            ],
          },
        ],
      }
            : {
        $and: [
          { pid: { $gt: NO_PID } },
          {
            $or: [
              { nickname: { $regex: rx } },
              { username: { $regex: rx } },
            ],
          },
        ],
      };

  const projection = {
    pid: 1,
    nickname: 1,
    username: 1,
    total_play_time: 1,
    active_badge: 1,
    is_admin: 1,
    online: 1,
    created_model_time: 1,
    created_at: 1,
    _id: 0,
  };

  const total = await playersCol.countDocuments(filter, { timeoutMS: PUBLIC_QUERY_TIMEOUT_MS });
  const totalPages = Math.ceil(total / clampedSize);

  const docs = await playersCol
    .find(filter, { projection, timeoutMS: PUBLIC_QUERY_TIMEOUT_MS })
    .sort({ total_play_time: -1, pid: 1 })
    .skip((clampedPage - 1) * clampedSize)
    .limit(clampedSize)
    .toArray();

  const items: PlayerSearchResult[] = docs.map((doc) => ({
    pid: safeInt(doc.pid),
    nickname: String(doc.nickname ?? "Unknown"),
    username: doc.username ? String(doc.username) : null,
    totalPlayTime: safeInt(doc.total_play_time),
    activeBadge: doc.active_badge ? String(doc.active_badge) : null,
    isAdmin: Boolean(doc.is_admin),
    online: Boolean(doc.online),
    createdAt: safeIsoDate(doc.created_at ?? doc.created_model_time),
  }));

  return {
    items,
    page: clampedPage,
    pageSize: clampedSize,
    total,
    totalPages,
  };
}

const EMPTY_MODE_STATS: ModeStatsSummaryDTO = {
  gamesPlayed: 0,
  gamesWon: 0,
  bestWave: 0,
  averageWave: 0,
  bestPlacement: 0,
  top3Finishes: 0,
};

/**
 * Returns full public player profile including aggregate overview from games_v2
 * and current ladder standings from rating_standings.
 */
export async function getPlayerProfile(pid: number, db?: Db): Promise<PlayerProfile | null> {
  if (!isPid(pid)) return null;

  const database = await resolveDb(db);
  const playersCol = database.collection("players");
  const gamesCol = database.collection("games_v2");
  const seasonsCol = database.collection("rating_seasons");
  const standingsCol = database.collection("rating_standings");

  const playerDoc = await playersCol.findOne(
    { pid },
    {
      projection: {
        uuid: 1,
        pid: 1,
        nickname: 1,
        username: 1,
        total_play_time: 1,
        active_badge: 1,
        unlocked_badges: 1,
        description: 1,
        is_admin: 1,
        online: 1,
        online_server: 1,
        created_model_time: 1,
        created_at: 1,
        _id: 0,
      },
    },
  );

  if (!playerDoc) return null;

  const playerUuid = String(playerDoc.uuid ?? "");

  // 1. Aggregate overview across games_v2 (matching GameDataRepository logic)
  let overall = {
    gamesPlayed: 0,
    gamesWon: 0,
    blocksBuilt: 0,
    blocksDeconstructed: 0,
    blocksDestroyed: 0,
    unitsProduced: 0,
    unitsDestroyed: 0,
  };

  const modeStatsMap: Record<string, ModeStatsSummaryDTO> = {
    PVP: { ...EMPTY_MODE_STATS },
    SURVIVAL: { ...EMPTY_MODE_STATS },
    HEXED: { ...EMPTY_MODE_STATS },
  };

  if (playerUuid) {
    const [overallAgg, modeAgg] = await Promise.all([
      gamesCol
        .aggregate([
          { $match: { "player_stats.uuid": playerUuid, counted_in_stats: true } },
          { $unwind: "$player_stats" },
          { $match: { "player_stats.uuid": playerUuid } },
          {
            $group: {
              _id: null,
              gamesPlayed: { $sum: 1 },
              gamesWon: { $sum: { $cond: ["$player_stats.isWinner", 1, 0] } },
              blocksBuilt: { $sum: { $ifNull: ["$player_stats.blocks_built", 0] } },
              blocksDeconstructed: { $sum: { $ifNull: ["$player_stats.blocks_deconstructed", 0] } },
              blocksDestroyed: { $sum: { $ifNull: ["$player_stats.blocks_destroyed", 0] } },
              unitsProduced: { $sum: { $ifNull: ["$player_stats.units_produced", 0] } },
              unitsDestroyed: { $sum: { $ifNull: ["$player_stats.units_destroyed", 0] } },
            },
          },
        ])
        .toArray(),
      gamesCol
        .aggregate([
          { $match: { "player_stats.uuid": playerUuid, counted_in_stats: true } },
          { $unwind: "$player_stats" },
          { $match: { "player_stats.uuid": playerUuid } },
          {
            $group: {
              _id: "$stats_category",
              gamesPlayed: { $sum: 1 },
              gamesWon: { $sum: { $cond: ["$player_stats.isWinner", 1, 0] } },
              bestWave: { $max: "$waves_reached" },
              averageWave: { $avg: "$waves_reached" },
              bestPlacement: { $min: { $ifNull: ["$player_stats.placement", 999999] } },
              top3Finishes: {
                $sum: {
                  $cond: [
                    {
                      $and: [
                        { $ne: ["$player_stats.placement", null] },
                        { $lte: ["$player_stats.placement", 3] },
                      ],
                    },
                    1,
                    0,
                  ],
                },
              },
            },
          },
        ])
        .toArray(),
    ]);

    if (overallAgg.length > 0 && overallAgg[0]) {
      const row = overallAgg[0];
      overall = {
        gamesPlayed: safeInt(row.gamesPlayed),
        gamesWon: safeInt(row.gamesWon),
        blocksBuilt: safeInt(row.blocksBuilt),
        blocksDeconstructed: safeInt(row.blocksDeconstructed),
        blocksDestroyed: safeInt(row.blocksDestroyed),
        unitsProduced: safeInt(row.unitsProduced),
        unitsDestroyed: safeInt(row.unitsDestroyed),
      };
    }

    for (const row of modeAgg) {
      const cat = String(row._id ?? "").toUpperCase();
      if (cat in modeStatsMap) {
        const rawBestPlacement = safeInt(row.bestPlacement);
        modeStatsMap[cat] = {
          gamesPlayed: safeInt(row.gamesPlayed),
          gamesWon: safeInt(row.gamesWon),
          bestWave: safeInt(row.bestWave),
          averageWave: Math.round(Number(row.averageWave) || 0),
          bestPlacement: rawBestPlacement === 999999 ? 0 : rawBestPlacement,
          top3Finishes: safeInt(row.top3Finishes),
        };
      }
    }
  }

  // 2. Ladder standings
  const ladderStandings: PlayerProfile["standings"] = [];
  if (playerUuid) {
    const discoveredLadders = await seasonsCol.distinct("ladder");
    const activeLadders = Array.from(new Set([...discoveredLadders, "pvp", "minipvp", "hexed"])).filter(Boolean);

    for (const ladder of activeLadders) {
      // Find current active season or latest season (properly awaited fallback)
      const activeSeason = await seasonsCol.findOne({ ladder, status: "ACTIVE" }, { sort: { number: -1 } });
      const season = activeSeason ?? (await seasonsCol.findOne({ ladder }, { sort: { number: -1 } }));

      if (!season) continue;

      const seasonNumber = safeInt(season.number, 1);
      const standing = await standingsCol.findOne({
        ladder,
        season: seasonNumber,
        player_uuid: playerUuid,
      });

      if (!standing) continue;

      const rating = safeInt(standing.rating);
      const peakRating = Math.max(rating, safeInt(standing.peak_rating));

      // Tie-breaking rank calculation: rating DESC, player_uuid ASC
      const ahead = await standingsCol.countDocuments({
        ladder,
        season: seasonNumber,
        $or: [
          { rating: { $gt: rating } },
          { rating, player_uuid: { $lt: playerUuid } },
        ],
      });

      const participants = await standingsCol.countDocuments({
        ladder,
        season: seasonNumber,
      });

      ladderStandings.push({
        ladder,
        seasonNumber,
        seasonName: String(season.name ?? `Season ${seasonNumber}`),
        rating,
        peakRating,
        matches: safeInt(standing.matches),
        wins: safeInt(standing.wins),
        rank: ahead + 1,
        participants,
      });
    }
  }

  const badges = Array.isArray(playerDoc.unlocked_badges)
    ? playerDoc.unlocked_badges.map(String)
    : [];

  return {
    pid: safeInt(playerDoc.pid),
    nickname: String(playerDoc.nickname ?? "Unknown"),
    username: playerDoc.username ? String(playerDoc.username) : null,
    totalPlayTime: safeInt(playerDoc.total_play_time),
    activeBadge: playerDoc.active_badge ? String(playerDoc.active_badge) : null,
    unlockedBadges: badges,
    description: playerDoc.description ? String(playerDoc.description) : null,
    isAdmin: Boolean(playerDoc.is_admin),
    online: Boolean(playerDoc.online),
    onlineServer: playerDoc.online ? (playerDoc.online_server ? String(playerDoc.online_server) : null) : null,
    createdAt: safeIsoDate(playerDoc.created_at ?? playerDoc.created_model_time),
    standings: ladderStandings,
    overview: {
      overall,
      pvp: modeStatsMap.PVP ?? { ...EMPTY_MODE_STATS },
      survival: modeStatsMap.SURVIVAL ?? { ...EMPTY_MODE_STATS },
      hexed: modeStatsMap.HEXED ?? { ...EMPTY_MODE_STATS },
    },
  };
}

/**
 * Returns built-in playtime/Hexed points tops and the Mini-PvP/HexedCore Elo ladders.
 * Uses canonical tie-breaking rules and resolves participant public profiles in bulk.
 * Preserves honesty when requested ladder is missing.
 */
export async function getLeaderboard(
  category: LeaderboardCategory,
  page = 1,
  season?: number,
  pageSize = DEFAULT_PAGE_SIZE,
  db?: Db,
): Promise<LeaderboardResult> {
  const database = await resolveDb(db);
  const clampedPage = clampPage(page);
  const clampedSize = clampPageSize(pageSize);

  if (category === "playtime" || category === "hexed") {
    const playersCol = database.collection("players");
    const filter = { pid: { $gt: NO_PID } };

    const total = await playersCol.countDocuments(filter);
    const totalPages = Math.ceil(total / clampedSize);

    const docs = await playersCol
      .find(filter, {
        projection: {
          pid: 1,
          nickname: 1,
          username: 1,
          active_badge: 1,
          total_play_time: 1,
          hexed_rank: 1,
          hexed_points: 1,
          _id: 0,
        },
      })
      .sort(category === "playtime" ? { total_play_time: -1, pid: 1 } : { hexed_rank: -1, hexed_points: -1, pid: 1 })
      .skip((clampedPage - 1) * clampedSize)
      .limit(clampedSize)
      .toArray();

    const items: LeaderboardEntry[] = docs.map((doc, idx) => {
      const value = safeInt(category === "playtime" ? doc.total_play_time : doc.hexed_points);
      return {
        rank: (clampedPage - 1) * clampedSize + idx + 1,
        pid: safeInt(doc.pid),
        nickname: String(doc.nickname ?? "Unknown"),
        username: doc.username ? String(doc.username) : null,
        activeBadge: doc.active_badge ? String(doc.active_badge) : null,
        value,
        formattedValue: category === "playtime" ? formatPlayTime(value) : String(value),
      };
    });

    return {
      category,
      items,
      page: clampedPage,
      pageSize: clampedSize,
      total,
      totalPages,
    };
  }

  const seasonsCol = database.collection("rating_seasons");
  const standingsCol = database.collection("rating_standings");
  const playersCol = database.collection("players");

  const ladderId = category === "pvp" ? "minipvp" : "hexed";

  let seasonNumber = season;
  if (!seasonNumber) {
    const active = await seasonsCol.findOne({ ladder: ladderId, status: "ACTIVE" }, { sort: { number: -1 } });
    const latest = active ?? (await seasonsCol.findOne({ ladder: ladderId }, { sort: { number: -1 } }));
    seasonNumber = latest ? safeInt(latest.number, 1) : 1;
  }

  const filter = { ladder: ladderId, season: seasonNumber };
  const total = await standingsCol.countDocuments(filter);
  const totalPages = Math.ceil(total / clampedSize);

  // Tie-breaking order: rating DESC, player_uuid ASC
  const standingDocs = await standingsCol
    .find(filter)
    .sort({ rating: -1, player_uuid: 1 })
    .skip((clampedPage - 1) * clampedSize)
    .limit(clampedSize)
    .toArray();

  const uuids = standingDocs.map((doc) => String(doc.player_uuid ?? "")).filter(Boolean);
  const playerDocs =
    uuids.length > 0
      ? await playersCol
          .find(
            { uuid: { $in: uuids } },
            {
              projection: {
                uuid: 1,
                pid: 1,
                nickname: 1,
                username: 1,
                active_badge: 1,
                _id: 0,
              },
            },
          )
          .toArray()
      : [];

  const playerMap = new Map(
    playerDocs.map((p) => [
      String(p.uuid),
      {
        pid: isPid(p.pid) ? p.pid : null,
        nickname: String(p.nickname ?? "Unknown"),
        username: p.username ? String(p.username) : null,
        activeBadge: p.active_badge ? String(p.active_badge) : null,
      },
    ]),
  );

  const items: LeaderboardEntry[] = standingDocs.map((doc, idx) => {
    const uuid = String(doc.player_uuid ?? "");
    const info = playerMap.get(uuid);
    const ratingVal = safeInt(doc.rating);

    return {
      rank: (clampedPage - 1) * clampedSize + idx + 1,
      pid: info?.pid ?? null,
      nickname: info?.nickname ?? "Unknown",
      username: info?.username ?? null,
      activeBadge: info?.activeBadge ?? null,
      value: ratingVal,
      formattedValue: String(ratingVal),
      matches: safeInt(doc.matches),
      wins: safeInt(doc.wins),
      season: seasonNumber,
    };
  });

  return {
    category,
    ladder: ladderId,
    season: seasonNumber,
    items,
    page: clampedPage,
    pageSize: clampedSize,
    total,
    totalPages,
  };
}

/**
 * Lists matches with optional filtering by PID, ladder, or category.
 * Prioritizes started_at over created_at everywhere (created_at is record persistence time),
 * respects rated boolean directly, and bulk resolves map names via maps collection
 * (never outputs raw 24-char ObjectId hex; returns null if lookup is absent).
 */
export async function listMatches(
  filters: MatchFilters = {},
  db?: Db,
): Promise<PaginatedResult<PublicMatchSummary>> {
  const database = await resolveDb(db);
  const gamesCol = database.collection("games_v2");
  const ratingCol = database.collection<{ _id: string; [key: string]: unknown }>("rating_matches");
  const playersCol = database.collection("players");
  const mapsCol = database.collection<{ _id: ObjectId | string; name?: string }>("maps");

  const clampedPage = clampPage(filters.page);
  const clampedSize = clampPageSize(filters.pageSize);
  const ladder = filters.ladder === "pvp" ? "minipvp" : filters.ladder;

  let targetUuid: string | null = null;
  if (filters.pid !== undefined) {
    if (!isPid(filters.pid)) {
      return {
        items: [],
        page: clampedPage,
        pageSize: clampedSize,
        total: 0,
        totalPages: 0,
      };
    }
    const player = await playersCol.findOne({ pid: filters.pid }, { projection: { uuid: 1, _id: 0 } });
    if (!player || !player.uuid) {
      return {
        items: [],
        page: clampedPage,
        pageSize: clampedSize,
        total: 0,
        totalPages: 0,
      };
    }
    targetUuid = String(player.uuid);
  }

  // Branch A: Ladder filter specified -> query rating_matches as primary driver
  if (ladder) {
    const ratingQuery: Record<string, unknown> = {
      ladder,
    };
    if (filters.season) {
      ratingQuery.season = filters.season;
    }
    if (targetUuid) {
      ratingQuery["participants.uuid"] = targetUuid;
    }

    const total = await ratingCol.countDocuments(ratingQuery);
    const totalPages = Math.ceil(total / clampedSize);

    const ratingDocs = await ratingCol
      .find(ratingQuery)
      .sort({ ended_at: -1, _id: -1 })
      .skip((clampedPage - 1) * clampedSize)
      .limit(clampedSize)
      .toArray();

    const matchIds = ratingDocs.map((r) => String(r.match_id ?? r.matchId ?? "")).filter(Boolean);
    const gameDocs =
      matchIds.length > 0
        ? await gamesCol.find({ match_id: { $in: matchIds } }).toArray()
        : [];

    const gamesMap = new Map(gameDocs.map((g) => [String(g.match_id), g]));

    // Bulk resolve any map ObjectIds from games_v2 or rating_matches
    const mapLookupIds: (ObjectId | string)[] = [];
    for (const g of gameDocs) {
      if (g.map) {
        const oid = toObjectId(g.map);
        mapLookupIds.push(oid ?? (g.map as string));
      }
    }
    for (const r of ratingDocs) {
      if (r.map && isObjectIdHex(r.map)) {
        const oid = toObjectId(r.map);
        mapLookupIds.push(oid ?? (r.map as string));
      }
    }

    const mapNameMap = new Map<string, string>();
    if (mapLookupIds.length > 0) {
      const mapDocs = await mapsCol
        .find({ _id: { $in: mapLookupIds } }, { projection: { _id: 1, name: 1 } })
        .toArray();
      for (const m of mapDocs) {
        if (m.name) {
          mapNameMap.set(String(m._id), String(m.name));
        }
      }
    }

    const items: PublicMatchSummary[] = ratingDocs.map((r) => {
      const matchId = String(r.match_id ?? r.matchId ?? r._id);
      const game = gamesMap.get(matchId);

      // Prioritize started_at over created_at everywhere (GameData.created_at is persistence time)
      const startedAt = safeIsoDate(r.started_at ?? game?.started_at ?? game?.created_at);
      const endedAt = safeIsoDate(r.ended_at ?? game?.ended_at);

      let durationSeconds: number | null = null;
      if (startedAt && endedAt) {
        durationSeconds = Math.max(0, Math.floor((new Date(endedAt).getTime() - new Date(startedAt).getTime()) / 1000));
      }

      let playerOutcome: PublicMatchSummary["playerOutcome"] | undefined;
      if (targetUuid && Array.isArray(r.participants)) {
        const p = r.participants.find((part: { uuid?: string }) => part.uuid === targetUuid);
        if (p) {
          playerOutcome = {
            win: Boolean(p.win),
            placement: p.placement !== undefined && p.placement !== null ? safeInt(p.placement) : null,
            ratingDelta: p.delta !== undefined && p.delta !== null ? safeInt(p.delta) : null,
            ratingAfter: p.rating_after !== undefined && p.rating_after !== null
              ? safeInt(p.rating_after)
              : (p.ratingAfter !== undefined && p.ratingAfter !== null ? safeInt(p.ratingAfter) : null),
          };
        }
      }

      // Check rated boolean explicitly; do not infer ranked merely from skip_reason null
      const isRanked = r.rated !== undefined
        ? Boolean(r.rated)
        : (game?.is_ranked !== undefined ? Boolean(game.is_ranked) : false);

      // Map name resolution: prefer r.map name, otherwise resolved name from maps, never raw ObjectId
      let resolvedMap: string | null = null;
      if (r.map && !isObjectIdHex(r.map)) {
        resolvedMap = String(r.map);
      } else if (r.map && isObjectIdHex(r.map)) {
        resolvedMap = mapNameMap.get(String(r.map)) ?? null;
      } else if (game?.map) {
        if (typeof game.map === "string" && !isObjectIdHex(game.map)) {
          resolvedMap = game.map;
        } else {
          resolvedMap = mapNameMap.get(String(game.map)) ?? null;
        }
      }

      return {
        matchId,
        ladder: String(r.ladder ?? filters.ladder),
        season: safeInt(r.season) || null,
        startedAt,
        endedAt,
        durationSeconds,
        map: resolvedMap,
        gameMode: game?.game_mode ? String(game.game_mode) : null,
        category: game?.stats_category ? String(game.stats_category) : null,
        ranked: isRanked,
        playerCount: safeInt(r.players) || (Array.isArray(game?.player_stats) ? game.player_stats.length : 0),
        finishReason: r.finish ? String(r.finish) : game?.finish_reason ? String(game.finish_reason) : null,
        wavesReached: game?.waves_reached !== undefined ? safeInt(game.waves_reached) : null,
        ...(playerOutcome ? { playerOutcome } : {}),
      };
    });

    return {
      items,
      page: clampedPage,
      pageSize: clampedSize,
      total,
      totalPages,
    };
  }

  // Deduplicate the full history before pagination; time out rather than return partial results.
  // A server with nobody online can still cycle through games; those are not matches anyone played.
  const gameQuery: Record<string, unknown> = { "player_stats.0": { $exists: true } };
  if (targetUuid) {
    gameQuery["player_stats.uuid"] = targetUuid;
  }
  if (filters.category) {
    gameQuery.stats_category = filters.category;
  }

  const ratingQuery: Record<string, unknown> = targetUuid ? { "participants.uuid": targetUuid } : {};
  if (filters.season) ratingQuery.season = filters.season;
  const categoryLadder = filters.category === "PVP" ? "minipvp" : filters.category === "HEXED" ? "hexed" : null;
  if (categoryLadder) ratingQuery.ladder = categoryLadder;
  const indexProjection = {
    _id: 0,
    matchId: { $ifNull: ["$match_id", { $toString: "$_id" }] },
    sortTime: { $convert: {
      input: { $ifNull: ["$started_at", { $ifNull: ["$created_at", "$ended_at"] }] },
      to: "date", onError: null, onNull: null,
    } },
  };
  const pipeline: Document[] = [
    { $match: filters.season ? { _id: { $exists: false } } : gameQuery },
    { $project: indexProjection },
  ];
  if (!filters.category || categoryLadder) pipeline.push({ $unionWith: {
    coll: "rating_matches", pipeline: [{ $match: ratingQuery }, { $project: indexProjection }],
  } });
  pipeline.push(
    { $group: { _id: "$matchId", sortTime: { $max: "$sortTime" } } },
    { $sort: { sortTime: -1, _id: 1 } },
    { $facet: {
      items: [{ $skip: (clampedPage - 1) * clampedSize }, { $limit: clampedSize }],
      total: [{ $count: "value" }],
    } },
  );
  const [selected] = await gamesCol.aggregate<{
    items: { _id: string }[]; total: { value: number }[];
  }>(pipeline, { allowDiskUse: true, timeoutMS: PUBLIC_QUERY_TIMEOUT_MS }).toArray();
  const total = selected?.total[0]?.value ?? 0;
  const totalPages = Math.ceil(total / clampedSize);
  const matchIds = selected?.items.map(row => row._id) ?? [];
  const storedGames = matchIds.length ? await gamesCol.find({ $or: [
    { match_id: { $in: matchIds } },
    { _id: { $in: matchIds.filter(isObjectIdHex).map(id => new ObjectId(id)) } },
  ] }).toArray() : [];
  const gamesMap = new Map(storedGames.map(game => [String(game.match_id ?? game._id), game]));
  const gameDocs = matchIds.map(matchId => gamesMap.get(matchId) ?? { match_id: matchId } as Document);
  const ratingDocs =
    matchIds.length > 0
      ? await ratingCol.find({ ...ratingQuery, match_id: { $in: matchIds } }).sort({ ladder: 1, _id: 1 }).toArray()
      : [];
  const ratingMap = new Map<string, Document>();
  for (const rating of ratingDocs) {
    const matchId = String(rating.match_id ?? "");
    if (!ratingMap.has(matchId)) ratingMap.set(matchId, rating);
  }

  // Bulk resolve any map ObjectIds from games_v2 or rating_matches
  const mapLookupIds: (ObjectId | string)[] = [];
  for (const g of gameDocs) {
    if (g.map) {
      const oid = toObjectId(g.map);
      mapLookupIds.push(oid ?? (g.map as string));
    }
  }
  for (const r of ratingDocs) {
    if (r.map && isObjectIdHex(r.map)) {
      const oid = toObjectId(r.map);
      mapLookupIds.push(oid ?? (r.map as string));
    }
  }

  const mapNameMap = new Map<string, string>();
  if (mapLookupIds.length > 0) {
    const mapDocs = await mapsCol
      .find({ _id: { $in: mapLookupIds } }, { projection: { _id: 1, name: 1 } })
      .toArray();
    for (const m of mapDocs) {
      if (m.name) {
        mapNameMap.set(String(m._id), String(m.name));
      }
    }
  }

  const items: PublicMatchSummary[] = gameDocs.map((g) => {
    const matchId = String(g.match_id ?? safeObjectIdString(g._id));
    const r = ratingMap.get(matchId);

    // Prioritize started_at over created_at everywhere (GameData.created_at is persistence time)
    const startedAt = safeIsoDate(r?.started_at ?? g.started_at ?? g.created_at);
    const endedAt = safeIsoDate(r?.ended_at ?? g.ended_at);

    let durationSeconds: number | null = null;
    if (startedAt && endedAt) {
      durationSeconds = Math.max(0, Math.floor((new Date(endedAt).getTime() - new Date(startedAt).getTime()) / 1000));
    }

    let playerOutcome: PublicMatchSummary["playerOutcome"] | undefined;
    if (targetUuid) {
      let win = false;
      let placement: number | null = null;
      let ratingDelta: number | null = null;
      let ratingAfter: number | null = null;

      if (Array.isArray(g.player_stats)) {
        const ps = g.player_stats.find((p: { uuid?: string }) => p.uuid === targetUuid);
        if (ps) {
          win = Boolean(ps.isWinner);
          placement = ps.placement !== undefined && ps.placement !== null ? safeInt(ps.placement) : null;
        }
      }

      if (r && Array.isArray(r.participants)) {
        const rp = r.participants.find((p: { uuid?: string }) => p.uuid === targetUuid);
        if (rp) {
          win = rp.win !== undefined ? Boolean(rp.win) : win;
          placement = rp.placement !== undefined ? safeInt(rp.placement) : placement;
          ratingDelta = rp.delta !== undefined ? safeInt(rp.delta) : null;
          const rAfter = rp.rating_after ?? rp.ratingAfter;
          ratingAfter = rAfter !== undefined && rAfter !== null ? safeInt(rAfter) : null;
        }
      }

      playerOutcome = { win, placement, ratingDelta, ratingAfter };
    }

    // Check rated boolean explicitly; do not infer ranked merely from skip_reason null
    const isRanked = r?.rated !== undefined
      ? Boolean(r.rated)
      : (g.is_ranked !== undefined ? Boolean(g.is_ranked) : false);

    // Map name resolution: prefer r.map name, otherwise resolved name from maps, never raw ObjectId
    let resolvedMap: string | null = null;
    if (r?.map && !isObjectIdHex(r.map)) {
      resolvedMap = String(r.map);
    } else if (r?.map && isObjectIdHex(r.map)) {
      resolvedMap = mapNameMap.get(String(r.map)) ?? null;
    } else if (g.map) {
      if (typeof g.map === "string" && !isObjectIdHex(g.map)) {
        resolvedMap = g.map;
      } else {
        resolvedMap = mapNameMap.get(String(g.map)) ?? null;
      }
    }

    return {
      matchId,
      ladder: r?.ladder ? String(r.ladder) : null,
      season: r?.season !== undefined ? safeInt(r.season) : null,
      startedAt,
      endedAt,
      durationSeconds,
      map: resolvedMap,
      gameMode: g.game_mode ? String(gameModeToString(g.game_mode)) : null,
      category: g.stats_category ? String(g.stats_category) : null,
      ranked: isRanked,
      playerCount: Array.isArray(g.player_stats) ? g.player_stats.length : safeInt(r?.players),
      finishReason: g.finish_reason ? String(g.finish_reason) : r?.finish ? String(r.finish) : null,
      wavesReached: g.waves_reached !== undefined ? safeInt(g.waves_reached) : null,
      ...(playerOutcome ? { playerOutcome } : {}),
    };
  });

  return {
    items,
    page: clampedPage,
    pageSize: clampedSize,
    total,
    totalPages,
  };
}

function gameModeToString(value: unknown): string {
  return typeof value === "string" ? value : String(value);
}

/**
 * Returns full match detail with all public participants.
 * Resolves participant UUIDs to PIDs on the server and strips arbitrary rating engine docs.
 * Resolves map names via maps collection without outputting raw ObjectId hexes (returns null if absent).
 * Preserves honesty when requested ladder is missing (no silently selecting another ladder).
 */
export async function getMatchDetail(
  matchId: string,
  ladder?: string,
  db?: Db,
): Promise<PublicMatchDetail | null> {
  if (ladder === "pvp") ladder = "minipvp";
  const sanitizedId = matchId.trim();
  if (!sanitizedId) return null;

  const database = await resolveDb(db);
  const gamesCol = database.collection("games_v2");
  const ratingCol = database.collection<{ _id: string; [key: string]: unknown }>("rating_matches");
  const playersCol = database.collection("players");
  const mapsCol = database.collection<{ _id: ObjectId | string; name?: string }>("maps");

  let ratingDoc: Document | null = null;
  if (ladder) {
    ratingDoc =
      (await ratingCol.findOne({ _id: `${ladder}:${sanitizedId}` })) ??
      (await ratingCol.findOne({ ladder, match_id: sanitizedId }));
    // If a ladder was explicitly requested, but this match does not belong to that ladder,
    // do NOT silently select another ladder or fall back to an unverified game.
    if (!ratingDoc) {
      return null;
    }
  } else {
    ratingDoc = await ratingCol.findOne({ match_id: sanitizedId });
  }

  const gameDoc = (await gamesCol.findOne({ match_id: sanitizedId })) ??
    (isObjectIdHex(sanitizedId) ? await gamesCol.findOne({ _id: new ObjectId(sanitizedId) }) : null);

  if (!gameDoc && !ratingDoc) return null;

  // Gather all participant UUIDs across both sources
  const uuidSet = new Set<string>();
  if (Array.isArray(ratingDoc?.participants)) {
    for (const p of ratingDoc.participants) {
      if (p.uuid) uuidSet.add(String(p.uuid));
    }
  }
  if (Array.isArray(gameDoc?.player_stats)) {
    for (const p of gameDoc.player_stats) {
      if (p.uuid) uuidSet.add(String(p.uuid));
    }
  }

  const allUuids = Array.from(uuidSet);
  const playerDocs =
    allUuids.length > 0
      ? await playersCol
          .find(
            { uuid: { $in: allUuids } },
            {
              projection: {
                uuid: 1,
                pid: 1,
                nickname: 1,
                username: 1,
                active_badge: 1,
                _id: 0,
              },
            },
          )
          .toArray()
      : [];

  const playerMap = new Map(
    playerDocs.map((p) => [
      String(p.uuid),
      {
        pid: isPid(p.pid) ? p.pid : null,
        nickname: String(p.nickname ?? "Unknown"),
        username: p.username ? String(p.username) : null,
        activeBadge: p.active_badge ? String(p.active_badge) : null,
      },
    ]),
  );

  // Map games_v2 player_stats by uuid
  const inGameStatsMap = new Map<string, {
    blocksBuilt: number;
    blocksDeconstructed: number;
    blocksDestroyed: number;
    unitsProduced: number;
    unitsDestroyed: number;
    placement?: number;
    isWinner?: boolean;
  }>();

  if (Array.isArray(gameDoc?.player_stats)) {
    for (const ps of gameDoc.player_stats) {
      if (ps.uuid) {
        inGameStatsMap.set(String(ps.uuid), {
          blocksBuilt: safeInt(ps.blocks_built),
          blocksDeconstructed: safeInt(ps.blocks_deconstructed),
          blocksDestroyed: safeInt(ps.blocks_destroyed),
          unitsProduced: safeInt(ps.units_produced),
          unitsDestroyed: safeInt(ps.units_destroyed),
          placement: ps.placement !== undefined && ps.placement !== null ? safeInt(ps.placement) : undefined,
          isWinner: Boolean(ps.isWinner),
        });
      }
    }
  }

  // Construct participants list
  const participants: PublicMatchParticipant[] = [];
  const processedUuids = new Set<string>();

  if (Array.isArray(ratingDoc?.participants)) {
    for (const p of ratingDoc.participants) {
      const uuid = String(p.uuid ?? "");
      if (!uuid) continue;
      processedUuids.add(uuid);

      const resolved = playerMap.get(uuid);
      const inGame = inGameStatsMap.get(uuid);

      const rBefore = p.rating_before ?? p.ratingBefore;
      const rAfter = p.rating_after ?? p.ratingAfter;

      participants.push({
        pid: resolved?.pid ?? null,
        name: p.name ? String(p.name) : resolved?.nickname ?? "Unknown",
        username: resolved?.username ?? null,
        activeBadge: resolved?.activeBadge ?? null,
        team: p.team !== undefined && p.team !== null ? safeInt(p.team) : null,
        placement: p.placement !== undefined && p.placement !== null ? safeInt(p.placement) : (inGame?.placement ?? null),
        win: p.win !== undefined ? Boolean(p.win) : Boolean(inGame?.isWinner),
        ratingBefore: rBefore !== undefined && rBefore !== null ? safeInt(rBefore) : null,
        delta: p.delta !== undefined && p.delta !== null ? safeInt(p.delta) : null,
        ratingAfter: rAfter !== undefined && rAfter !== null ? safeInt(rAfter) : null,
        counted: p.counted !== undefined && p.counted !== null ? Boolean(p.counted) : null,
        reason: p.reason ? String(p.reason) : null,
        blocksBuilt: inGame ? inGame.blocksBuilt : null,
        blocksDeconstructed: inGame ? inGame.blocksDeconstructed : null,
        blocksDestroyed: inGame ? inGame.blocksDestroyed : null,
        unitsProduced: inGame ? inGame.unitsProduced : null,
        unitsDestroyed: inGame ? inGame.unitsDestroyed : null,
      });
    }
  }

  // Add any in-game participants that were not in rating participants
  for (const [uuid, inGame] of inGameStatsMap.entries()) {
    if (processedUuids.has(uuid)) continue;
    const resolved = playerMap.get(uuid);

    participants.push({
      pid: resolved?.pid ?? null,
      name: resolved?.nickname ?? "Unknown",
      username: resolved?.username ?? null,
      activeBadge: resolved?.activeBadge ?? null,
      team: null,
      placement: inGame.placement ?? null,
      win: Boolean(inGame.isWinner),
      ratingBefore: null,
      delta: null,
      ratingAfter: null,
      counted: null,
      reason: null,
      blocksBuilt: inGame.blocksBuilt,
      blocksDeconstructed: inGame.blocksDeconstructed,
      blocksDestroyed: inGame.blocksDestroyed,
      unitsProduced: inGame.unitsProduced,
      unitsDestroyed: inGame.unitsDestroyed,
    });
  }

  // Sort participants by placement ASC (winners first), then by name
  participants.sort((a, b) => {
    const placeA = a.placement ?? 999999;
    const placeB = b.placement ?? 999999;
    if (placeA !== placeB) return placeA - placeB;
    return a.name.localeCompare(b.name);
  });

  // Prioritize started_at over created_at everywhere (GameData.created_at is persistence time)
  const startedAt = safeIsoDate(ratingDoc?.started_at ?? gameDoc?.started_at ?? gameDoc?.created_at);
  const endedAt = safeIsoDate(ratingDoc?.ended_at ?? gameDoc?.ended_at);

  let durationSeconds: number | null = null;
  if (startedAt && endedAt) {
    durationSeconds = Math.max(0, Math.floor((new Date(endedAt).getTime() - new Date(startedAt).getTime()) / 1000));
  }

  let teamSizes: Record<string, number> | null = null;
  const rawTeams = ratingDoc?.team_sizes ?? ratingDoc?.teamSizes;
  if (rawTeams && typeof rawTeams === "object") {
    teamSizes = {};
    for (const [k, v] of Object.entries(rawTeams)) {
      teamSizes[k] = safeInt(v);
    }
  }

  // Check rated boolean explicitly; do not infer ranked merely from skip_reason null
  const isRanked = ratingDoc?.rated !== undefined
    ? Boolean(ratingDoc.rated)
    : (gameDoc?.is_ranked !== undefined ? Boolean(gameDoc.is_ranked) : false);

  // Map name resolution: prefer ratingDoc.map string name, otherwise query maps collection, never raw ObjectId
  let mapName: string | null = null;
  if (ratingDoc?.map && !isObjectIdHex(ratingDoc.map)) {
    mapName = String(ratingDoc.map);
  } else {
    const rawMap = ratingDoc?.map ?? gameDoc?.map;
    if (rawMap) {
      if (typeof rawMap === "string" && !isObjectIdHex(rawMap)) {
        mapName = rawMap;
      } else {
        const oid = toObjectId(rawMap);
        const mapDoc = await mapsCol.findOne(
          { _id: (oid ?? rawMap) as ObjectId | string },
          { projection: { name: 1 } },
        );
        mapName = mapDoc?.name ? String(mapDoc.name) : null;
      }
    }
  }

  return {
    matchId: sanitizedId,
    ladder: ratingDoc?.ladder ? String(ratingDoc.ladder) : null,
    season: ratingDoc?.season !== undefined ? safeInt(ratingDoc.season) : null,
    startedAt,
    endedAt,
    durationSeconds,
    map: mapName,
    serverName: gameDoc?.server_name ? String(gameDoc.server_name) : null,
    gameMode: gameDoc?.game_mode ? String(gameDoc.game_mode) : null,
    category: gameDoc?.stats_category ? String(gameDoc.stats_category) : null,
    ranked: isRanked,
    countedInStats: Boolean(gameDoc?.counted_in_stats),
    finishReason: gameDoc?.finish_reason ? String(gameDoc.finish_reason) : ratingDoc?.finish ? String(ratingDoc.finish) : null,
    wavesReached: gameDoc?.waves_reached !== undefined ? safeInt(gameDoc.waves_reached) : null,
    playerCount: participants.length,
    teamSizes,
    participants,
  };
}

/**
 * Looks up all linked profiles for a verified Discord identity.
 * Strictly allowlisted: never exposes discord_id or private credentials in output.
 */
export async function getLinkedProfiles(discordId: string, db?: Db): Promise<LinkedProfile[]> {
  if (!discordId || !/^\d{17,20}$/.test(discordId)) return [];

  const database = await resolveDb(db);
  const playersCol = database.collection("players");

  const docs = await playersCol
    .find(
      { discord_id: discordId },
      {
        projection: {
          pid: 1,
          nickname: 1,
          username: 1,
          total_play_time: 1,
          active_badge: 1,
          online: 1,
          created_model_time: 1,
          created_at: 1,
          _id: 0,
        },
      },
    )
    .sort({ pid: 1 })
    .toArray();

  return docs.map((doc) => ({
    pid: safeInt(doc.pid),
    nickname: String(doc.nickname ?? "Unknown"),
    username: doc.username ? String(doc.username) : null,
    totalPlayTime: safeInt(doc.total_play_time),
    activeBadge: doc.active_badge ? String(doc.active_badge) : null,
    online: Boolean(doc.online),
    createdAt: safeIsoDate(doc.created_at ?? doc.created_model_time),
  }));
}
