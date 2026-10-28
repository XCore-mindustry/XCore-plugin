import { describe, expect, it } from "bun:test";
import type {
  LadderStandingDTO,
  PublicMatchSummary,
} from "../src/lib/server/stats-types";

const {
  escapeRegex,
  formatPlayTime,
  getLeaderboard,
  getLinkedProfiles,
  getMatchDetail,
  getPlayerProfile,
  listMatches,
  safeInt,
  safeIsoDate,
  safeObjectIdString,
  searchPlayers,
} = await import("../src/lib/server/stats");

import type { Db } from "mongodb";

// --- In-memory lightweight Mongo collection stub for testing ---
/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/no-unused-vars */
type QueryFilter = Record<string, any>;

function getNested(obj: any, path: string): any {
  if (!obj) return undefined;
  if (path in obj) return obj[path];
  const parts = path.split(".");
  let cur = obj;
  for (const part of parts) {
    if (cur === null || cur === undefined) return undefined;
    if (Array.isArray(cur)) {
      return cur.map((item) => (item ? item[part] : undefined));
    }
    cur = cur[part];
  }
  return cur;
}

function matchDocument(doc: any, filter: QueryFilter): boolean {
  if (!filter || Object.keys(filter).length === 0) return true;

  for (const [key, val] of Object.entries(filter)) {
    if (key === "$and" && Array.isArray(val)) {
      if (!val.every((sub) => matchDocument(doc, sub))) return false;
      continue;
    }
    if (key === "$or" && Array.isArray(val)) {
      if (!val.some((sub) => matchDocument(doc, sub))) return false;
      continue;
    }

    const docVal = getNested(doc, key);

    // If docVal is an array (e.g. player_stats.uuid), check if any element matches
    if (Array.isArray(docVal)) {
      if (val !== null && typeof val === "object" && !(val instanceof RegExp) && !(val instanceof Date)) {
        if ("$in" in val && Array.isArray(val.$in)) {
          if (!docVal.some((item) => val.$in.includes(item))) return false;
          continue;
        }
      } else {
        if (!docVal.includes(val)) return false;
        continue;
      }
    }

    const isValOid = val && typeof val === "object" && ("toHexString" in val || val.constructor?.name === "ObjectId");
    const isDocOid = docVal && typeof docVal === "object" && ("toHexString" in docVal || docVal.constructor?.name === "ObjectId");
    if (isValOid || isDocOid) {
      const vStr = isValOid ? val.toHexString() : String(val);
      const dStr = isDocOid ? docVal.toHexString() : String(docVal);
      if (vStr !== dStr) return false;
      continue;
    }

    if (val !== null && typeof val === "object" && !(val instanceof RegExp) && !(val instanceof Date)) {
      const isOperator = ["$gt", "$gte", "$lt", "$lte", "$ne", "$in", "$regex"].some((op) => op in val);
      if (isOperator) {
        if ("$gt" in val && !(docVal > val.$gt)) return false;
        if ("$gte" in val && !(docVal >= val.$gte)) return false;
        if ("$lt" in val && !(docVal < val.$lt)) return false;
        if ("$lte" in val && !(docVal <= val.$lte)) return false;
        if ("$ne" in val && docVal === val.$ne) return false;
        if ("$in" in val && Array.isArray(val.$in)) {
          const inVals = val.$in.map((x: any) => (x && typeof x === "object" && "toHexString" in x ? x.toHexString() : String(x)));
          const target = docVal && typeof docVal === "object" && "toHexString" in docVal ? docVal.toHexString() : String(docVal);
          if (!inVals.includes(target) && !val.$in.includes(docVal)) return false;
          continue;
        }
        if ("$regex" in val) {
          const rx = val.$regex instanceof RegExp ? val.$regex : new RegExp(val.$regex, "i");
          if (!rx.test(String(docVal ?? ""))) return false;
        }
        continue;
      }
    }

    if (val instanceof RegExp) {
      if (!val.test(String(docVal ?? ""))) return false;
      continue;
    }

    if (docVal !== val) return false;
  }
  return true;
}

function createMockCollection(initialDocs: any[] = []) {
  const docs = initialDocs.map((d) => ({ ...d }));

  const collection = {
    find(filter: QueryFilter = {}, _options: any = {}) {
      const filtered = docs.filter((d) => matchDocument(d, filter));
      let sortSpec: any = null;
      let skipCount = 0;
      let limitCount = Infinity;

      const cursor = {
        sort(spec: any) {
          sortSpec = spec;
          return cursor;
        },
        skip(n: number) {
          skipCount = n;
          return cursor;
        },
        limit(n: number) {
          limitCount = n;
          return cursor;
        },
        project(_proj: any) {
          return cursor;
        },
        async toArray() {
          let res = [...filtered];
          if (sortSpec) {
            res.sort((a, b) => {
              for (const [k, dir] of Object.entries(sortSpec)) {
                const va = getNested(a, k);
                const vb = getNested(b, k);
                if (va !== vb) {
                  return (dir as number) > 0 ? (va > vb ? 1 : -1) : va < vb ? 1 : -1;
                }
              }
              return 0;
            });
          }
          if (skipCount > 0) res = res.slice(skipCount);
          if (limitCount < Infinity) res = res.slice(0, limitCount);
          return res.map((d) => ({ ...d }));
        },
      };
      return cursor;
    },

    async findOne(filter: QueryFilter = {}, options: any = {}) {
      const matched = docs.filter((d) => matchDocument(d, filter));
      if (options?.sort) {
        matched.sort((a, b) => {
          for (const [k, dir] of Object.entries(options.sort)) {
            const va = getNested(a, k);
            const vb = getNested(b, k);
            if (va !== vb) {
              return (dir as number) > 0 ? (va > vb ? 1 : -1) : va < vb ? 1 : -1;
            }
          }
          return 0;
        });
      }
      return matched.length > 0 ? { ...matched[0] } : null;
    },

    async countDocuments(filter: QueryFilter = {}) {
      return docs.filter((d) => matchDocument(d, filter)).length;
    },

    async distinct(field: string, filter: QueryFilter = {}) {
      const filtered = docs.filter((d) => matchDocument(d, filter));
      const set = new Set();
      for (const d of filtered) {
        const v = getNested(d, field);
        if (v !== undefined) set.add(v);
      }
      return Array.from(set);
    },

    aggregate(pipeline: any[]) {
      return {
        async toArray() {
          let cur = docs.map((d) => ({ ...d }));
          for (const stage of pipeline) {
            if ("$match" in stage) {
              cur = cur.filter((d) => {
                const m = stage.$match;
                if (m["player_stats.uuid"]) {
                  const targetUuid = m["player_stats.uuid"];
                  const stats = d.player_stats;
                  if (Array.isArray(stats)) {
                    if (!stats.some((s: any) => s?.uuid === targetUuid)) return false;
                  } else if (stats && typeof stats === "object") {
                    if (stats.uuid !== targetUuid) return false;
                  } else {
                    return false;
                  }
                }
                if (m.counted_in_stats !== undefined && d.counted_in_stats !== m.counted_in_stats) {
                  return false;
                }
                return true;
              });
            } else if ("$unwind" in stage) {
              const field = stage.$unwind.replace("$", "");
              const unwound: any[] = [];
              for (const d of cur) {
                const arr = d[field];
                if (Array.isArray(arr)) {
                  for (const item of arr) {
                    unwound.push({ ...d, [field]: item });
                  }
                }
              }
              cur = unwound;
            } else if ("$group" in stage) {
              const groupField = stage.$group._id;
              const groups = new Map<any, any>();

              for (const row of cur) {
                let key = null;
                if (typeof groupField === "string" && groupField.startsWith("$")) {
                  key = getNested(row, groupField.slice(1));
                }
                let acc = groups.get(key);
                if (!acc) {
                  acc = {
                    _id: key,
                    gamesPlayed: 0,
                    gamesWon: 0,
                    blocksBuilt: 0,
                    blocksDeconstructed: 0,
                    blocksDestroyed: 0,
                    unitsProduced: 0,
                    unitsDestroyed: 0,
                    bestWave: 0,
                    totalWaves: 0,
                    waveCounts: 0,
                    bestPlacement: 999999,
                    top3Finishes: 0,
                  };
                  groups.set(key, acc);
                }

                acc.gamesPlayed += 1;
                const isWinner = getNested(row, "player_stats.isWinner");
                if (isWinner) acc.gamesWon += 1;

                acc.blocksBuilt += Number(getNested(row, "player_stats.blocks_built")) || 0;
                acc.blocksDeconstructed += Number(getNested(row, "player_stats.blocks_deconstructed")) || 0;
                acc.blocksDestroyed += Number(getNested(row, "player_stats.blocks_destroyed")) || 0;
                acc.unitsProduced += Number(getNested(row, "player_stats.units_produced")) || 0;
                acc.unitsDestroyed += Number(getNested(row, "player_stats.units_destroyed")) || 0;

                const waves = Number(getNested(row, "waves_reached")) || 0;
                if (waves > acc.bestWave) acc.bestWave = waves;
                acc.totalWaves += waves;
                acc.waveCounts += 1;

                const placement = getNested(row, "player_stats.placement");
                if (placement !== undefined && placement !== null) {
                  const pNum = Number(placement);
                  if (pNum < acc.bestPlacement) acc.bestPlacement = pNum;
                  if (pNum <= 3) acc.top3Finishes += 1;
                }
              }

              cur = Array.from(groups.values()).map((g) => ({
                _id: g._id,
                gamesPlayed: g.gamesPlayed,
                gamesWon: g.gamesWon,
                blocksBuilt: g.blocksBuilt,
                blocksDeconstructed: g.blocksDeconstructed,
                blocksDestroyed: g.blocksDestroyed,
                unitsProduced: g.unitsProduced,
                unitsDestroyed: g.unitsDestroyed,
                bestWave: g.bestWave,
                averageWave: g.waveCounts > 0 ? g.totalWaves / g.waveCounts : 0,
                bestPlacement: g.bestPlacement,
                top3Finishes: g.top3Finishes,
              }));
            }
          }
          return cur;
        },
      };
    },
  };

  return collection;
}

function createMockDb(collections: Record<string, any[]>): Db {
  const store: Record<string, any> = {};
  for (const [name, docs] of Object.entries(collections)) {
    store[name] = createMockCollection(docs);
  }

  return {
    collection(name: string) {
      if (!store[name]) {
        store[name] = createMockCollection([]);
      }
      return store[name];
    },
  } as unknown as Db;
}
/* eslint-enable @typescript-eslint/no-explicit-any, @typescript-eslint/no-unused-vars */

// --- Test Fixture Data ---

const MOCK_PLAYERS = [
  {
    pid: 101,
    nickname: "Alice",
    username: "alice_mind",
    total_play_time: 1500, // 25 hours -> 1d 1h
    active_badge: "badge_veteran",
    unlocked_badges: ["badge_alpha", "badge_veteran"],
    description: "Pro mindustry defender",
    is_admin: false,
    online: true,
    online_server: "hub-1",
    created_at: new Date("2026-01-01T00:00:00Z"),
    // SENSITIVE FIELDS THAT MUST NEVER LEAK:
    uuid: "uuid-alice-private-12345",
    discord_id: "123456789012345678",
    last_ip: "192.168.1.1",
    password_hash: "$2a$12$e8Xfakehashalice",
    device_tokens: { "token-abc-123": 1700000000 },
  },
  {
    pid: 102,
    nickname: "Bob [Pro]",
    username: null,
    total_play_time: 2880, // 48 hours -> 2d
    active_badge: null,
    unlocked_badges: [],
    description: null,
    is_admin: true,
    online: false,
    online_server: null,
    created_at: new Date("2026-02-01T00:00:00Z"),
    // SENSITIVE FIELDS:
    uuid: "uuid-bob-private-67890",
    discord_id: "123456789012345678", // Linked to same discord user
    last_ip: "10.0.0.2",
    password_hash: "$2a$12$e8Xfakehashbob",
    device_tokens: { "token-def-456": 1700000000 },
  },
  {
    pid: 103,
    nickname: "Charlie_Special.*+?",
    username: "charlie",
    total_play_time: 120, // 2h
    active_badge: null,
    unlocked_badges: [],
    description: null,
    is_admin: false,
    online: false,
    created_at: new Date("2026-03-01T00:00:00Z"),
    // SENSITIVE FIELDS:
    uuid: "uuid-charlie-private-99999",
    discord_id: "987654321098765432",
    last_ip: "172.16.0.4",
    password_hash: "$2a$12$fakepwhash",
  },
];

const MOCK_MAPS = [
  { _id: "650000000000000000000001", name: "Glacier Ridge" },
  { _id: "650000000000000000000002", name: "Hexed Basin" },
];

const MOCK_GAMES_V2 = [
  {
    match_id: "match-pvp-001",
    game_mode: "MiniPvP",
    stats_category: "PVP",
    is_ranked: true,
    counted_in_stats: true,
    waves_reached: 15,
    winning_team: "1",
    finish_reason: "CORE_DESTROYED",
    map: "650000000000000000000001", // ObjectId hex resolved via maps collection
    server_name: "mini-pvp-1",
    started_at: new Date("2026-10-25T12:00:00Z").getTime(),
    ended_at: new Date("2026-10-25T12:20:00Z").getTime(), // 20m = 1200s
    created_at: new Date("2026-10-25T12:20:05Z"), // Record persistence time after end!
    player_stats: [
      {
        uuid: "uuid-alice-private-12345",
        isWinner: true,
        placement: 1,
        blocks_built: 120,
        blocks_deconstructed: 10,
        blocks_destroyed: 50,
        units_produced: 40,
        units_destroyed: 15,
      },
      {
        uuid: "uuid-bob-private-67890",
        isWinner: false,
        placement: 2,
        blocks_built: 95,
        blocks_deconstructed: 5,
        blocks_destroyed: 20,
        units_produced: 30,
        units_destroyed: 8,
      },
    ],
  },
  {
    match_id: "match-hexed-002",
    game_mode: "Hexed",
    stats_category: "HEXED",
    is_ranked: true,
    counted_in_stats: true,
    waves_reached: 30,
    winning_team: null,
    finish_reason: "TIME_OUT",
    map: "650000000000000000000099", // Absent from maps collection -> must yield null
    server_name: "hexed-1",
    started_at: new Date("2026-10-26T14:00:00Z").getTime(),
    ended_at: new Date("2026-10-26T14:45:00Z").getTime(), // 45m = 2700s
    created_at: new Date("2026-10-26T14:45:10Z"), // Persistence time
    player_stats: [
      {
        uuid: "uuid-alice-private-12345",
        isWinner: true,
        placement: 1,
        blocks_built: 300,
        blocks_deconstructed: 20,
        blocks_destroyed: 80,
        units_produced: 100,
        units_destroyed: 60,
      },
    ],
  },
];

const MOCK_RATING_SEASONS = [
  { ladder: "minipvp", number: 1, name: "MiniPvP Season 1", status: "ACTIVE" },
  { ladder: "hexed", number: 2, name: "Hexed Season 2", status: "ACTIVE" },
];

const MOCK_RATING_STANDINGS = [
  {
    ladder: "minipvp",
    season: 1,
    player_uuid: "uuid-bob-private-67890",
    rating: 1650,
    peak_rating: 1700,
    matches: 25,
    wins: 18,
  },
  {
    ladder: "minipvp",
    season: 1,
    player_uuid: "uuid-alice-private-12345",
    rating: 1500,
    peak_rating: 1520,
    matches: 10,
    wins: 7,
  },
  {
    ladder: "hexed",
    season: 2,
    player_uuid: "uuid-alice-private-12345",
    rating: 1420,
    peak_rating: 1420,
    matches: 5,
    wins: 3,
  },
];

// Canonical match_id, rating_before, rating_after, team_sizes, rated per MongoMatchStore.document()
const MOCK_RATING_MATCHES = [
  {
    _id: "minipvp:match-pvp-001",
    match_id: "match-pvp-001",
    ladder: "minipvp",
    season: 1,
    started_at: new Date("2026-10-25T12:00:00Z"),
    ended_at: new Date("2026-10-25T12:20:00Z"),
    rated: true,
    skip_reason: null,
    finish: "CORE_DESTROYED",
    map: "QA Craters", // Preferential string name
    algorithm: "teamEloV1",
    players: 2,
    team_sizes: { 1: 1, 2: 1 },
    participants: [
      {
        uuid: "uuid-alice-private-12345",
        name: "Alice",
        team: 1,
        placement: 1,
        win: true,
        rating_before: 1485,
        delta: 15,
        rating_after: 1500,
        counted: true,
        reason: "win",
        extra: { internal_arbitrary_debug: 9999 }, // RAW ENGINE DOCS MUST NOT LEAK
      },
      {
        uuid: "uuid-bob-private-67890",
        name: "Bob [Pro]",
        team: 2,
        placement: 2,
        win: false,
        rating_before: 1665,
        delta: -15,
        rating_after: 1650,
        counted: true,
        reason: "loss",
        extra: { internal_arbitrary_debug: 8888 }, // RAW ENGINE DOCS MUST NOT LEAK
      },
    ],
  },
];

function getTestDb() {
  return createMockDb({
    players: MOCK_PLAYERS,
    maps: MOCK_MAPS,
    games_v2: MOCK_GAMES_V2,
    rating_seasons: MOCK_RATING_SEASONS,
    rating_standings: MOCK_RATING_STANDINGS,
    rating_matches: MOCK_RATING_MATCHES,
  });
}

// ============================================================================
// TESTS
// ============================================================================

describe("XCore Stats Service - Safe Public DTO Privacy and Queries", () => {
  const db = getTestDb();

  // 1. DTO Privacy Verification
  describe("DTO Privacy Guarantee", () => {
    it("searchPlayers does not leak UUID, Discord ID, IP, or password hash", async () => {
      const res = await searchPlayers("Alice", 1, 25, db);
      expect(res.items.length).toBeGreaterThan(0);
      const player = res.items[0]!;

      expect(player.pid).toBe(101);
      expect(player.nickname).toBe("Alice");
      const p = player as Record<string, unknown>;
      expect(p.uuid).toBeUndefined();
      expect(p.discord_id).toBeUndefined();
      expect(p.discordId).toBeUndefined();
      expect(p.last_ip).toBeUndefined();
      expect(p.password_hash).toBeUndefined();
      expect(p.device_tokens).toBeUndefined();
    });

    it("getPlayerProfile does not leak UUID, Discord ID, IP, or password hash", async () => {
      const profile = await getPlayerProfile(101, db);
      expect(profile).not.toBeNull();
      if (!profile) return;

      expect(profile.pid).toBe(101);
      expect(profile.nickname).toBe("Alice");
      const p = profile as Record<string, unknown>;
      expect(p.uuid).toBeUndefined();
      expect(p.discord_id).toBeUndefined();
      expect(p.discordId).toBeUndefined();
      expect(p.last_ip).toBeUndefined();
      expect(p.password_hash).toBeUndefined();
      expect(p.device_tokens).toBeUndefined();
    });

    it("getLeaderboard does not leak UUIDs or Discord IDs", async () => {
      const pvpLeaderboard = await getLeaderboard("pvp", 1, 1, 25, db);
      expect(pvpLeaderboard.items.length).toBe(2);

      for (const entry of pvpLeaderboard.items) {
        expect(entry.pid).toBeDefined();
        const e = entry as Record<string, unknown>;
        expect(e.uuid).toBeUndefined();
        expect(e.player_uuid).toBeUndefined();
        expect(e.discord_id).toBeUndefined();
      }

      const playtimeLeaderboard = await getLeaderboard("playtime", 1, undefined, 25, db);
      expect(playtimeLeaderboard.items.length).toBeGreaterThan(0);
      for (const entry of playtimeLeaderboard.items) {
        expect(entry.pid).toBeDefined();
        const e = entry as Record<string, unknown>;
        expect(e.uuid).toBeUndefined();
        expect(e.discord_id).toBeUndefined();
      }
    });

    it("getMatchDetail does not leak participant UUIDs or raw rating extra arbitrary docs", async () => {
      const detail = await getMatchDetail("match-pvp-001", "minipvp", db);
      expect(detail).not.toBeNull();
      if (!detail) return;

      expect(detail.participants.length).toBe(2);
      for (const participant of detail.participants) {
        expect(participant.pid).toBeDefined();
        const p = participant as Record<string, unknown>;
        expect(p.uuid).toBeUndefined();
        expect(p.player_uuid).toBeUndefined();
        expect(p.extra).toBeUndefined();
        expect(p.internal_arbitrary_debug).toBeUndefined();
      }
    });

    it("getLinkedProfiles returns public-shaped profile and never exposes discordId", async () => {
      const profiles = await getLinkedProfiles("123456789012345678", db);
      expect(profiles.length).toBe(2); // Alice and Bob
      expect(profiles[0]?.pid).toBe(101);
      expect(profiles[1]?.pid).toBe(102);

      for (const p of profiles) {
        const item = p as Record<string, unknown>;
        expect(item.discordId).toBeUndefined();
        expect(item.discord_id).toBeUndefined();
        expect(item.uuid).toBeUndefined();
        expect(item.last_ip).toBeUndefined();
        expect(item.password_hash).toBeUndefined();
      }
    });
  });

  // 2. Search & Pagination Behavior
  describe("Search & Pagination Behavior", () => {
    it("safely escapes regex special characters without crashing or erroring", async () => {
      const specialQuery = ".*+?^${}()|[]\\-";
      const res = await searchPlayers(specialQuery, 1, 25, db);
      expect(res.items).toBeArray();
      expect(res.total).toBe(0);
    });

    it("finds player with regex special characters in nickname", async () => {
      // Bob has "[Pro]" in nickname
      const res = await searchPlayers("Bob [Pro]", 1, 25, db);
      expect(res.items.length).toBe(1);
      expect(res.items[0]?.pid).toBe(102);
    });

    it("finds player directly by numeric PID", async () => {
      const res = await searchPlayers("101", 1, 25, db);
      expect(res.items.length).toBe(1);
      expect(res.items[0]?.pid).toBe(101);
      expect(res.items[0]?.nickname).toBe("Alice");
    });

    it("clamps page <= 1000 and pageSize <= 50 boundaries safely", async () => {
      const res = await searchPlayers("Alice", 2000, 100, db);
      expect(res.page).toBe(1000);
      expect(res.pageSize).toBe(50);

      const resMin = await searchPlayers("Alice", -5, 0, db);
      expect(resMin.page).toBe(1);
      expect(resMin.pageSize).toBe(25);
    });

    it("returns empty result on blank query", async () => {
      const res = await searchPlayers("   ", 1, 25, db);
      expect(res.items).toBeEmpty();
      expect(res.total).toBe(0);
    });
  });

  // 3. Player Profile & Aggregations
  describe("getPlayerProfile & Aggregations", () => {
    it("returns complete profile with aggregated games_v2 overview and ladder standings", async () => {
      const profile = await getPlayerProfile(101, db);
      expect(profile).not.toBeNull();
      if (!profile) return;

      expect(profile.pid).toBe(101);
      expect(profile.nickname).toBe("Alice");
      expect(profile.username).toBe("alice_mind");
      expect(profile.totalPlayTime).toBe(1500);
      expect(profile.unlockedBadges).toEqual(["badge_alpha", "badge_veteran"]);
      expect(profile.online).toBe(true);
      expect(profile.onlineServer).toBe("hub-1");

      // Aggregate overview from games_v2
      expect(profile.overview.overall.gamesPlayed).toBe(2);
      expect(profile.overview.overall.gamesWon).toBe(2);
      expect(profile.overview.overall.blocksBuilt).toBe(420); // 120 + 300
      expect(profile.overview.overall.unitsProduced).toBe(140); // 40 + 100

      // Mode breakdowns
      expect(profile.overview.pvp.gamesPlayed).toBe(1);
      expect(profile.overview.pvp.gamesWon).toBe(1);
      expect(profile.overview.pvp.bestWave).toBe(15);
      expect(profile.overview.pvp.bestPlacement).toBe(1);

      expect(profile.overview.hexed.gamesPlayed).toBe(1);
      expect(profile.overview.hexed.gamesWon).toBe(1);
      expect(profile.overview.hexed.bestWave).toBe(30);

      // Standings
      expect(profile.standings.length).toBe(2); // minipvp and hexed
      const pvpStanding = profile.standings.find((s: LadderStandingDTO) => s.ladder === "minipvp");
      expect(pvpStanding).toBeDefined();
      expect(pvpStanding?.rating).toBe(1500);
      expect(pvpStanding?.peakRating).toBe(1520);
      expect(pvpStanding?.matches).toBe(10);
      expect(pvpStanding?.wins).toBe(7);
      expect(pvpStanding?.rank).toBe(2); // Bob is #1 (1650), Alice is #2 (1500)
      expect(pvpStanding?.participants).toBe(2);

      const hexedStanding = profile.standings.find((s: LadderStandingDTO) => s.ladder === "hexed");
      expect(hexedStanding).toBeDefined();
      expect(hexedStanding?.rating).toBe(1420);
      expect(hexedStanding?.rank).toBe(1);
    });

    it("returns null for nonexistent PID", async () => {
      const profile = await getPlayerProfile(999999, db);
      expect(profile).toBeNull();
    });

    it("returns null for non-positive or invalid PID", async () => {
      expect(await getPlayerProfile(0, db)).toBeNull();
      expect(await getPlayerProfile(-1, db)).toBeNull();
      expect(await getPlayerProfile(NaN, db)).toBeNull();
    });
  });

  // 4. Leaderboards & Tie-Breaking
  describe("getLeaderboard", () => {
    it("returns playtime leaderboard sorted descending by playtime", async () => {
      const lb = await getLeaderboard("playtime", 1, undefined, 10, db);
      expect(lb.category).toBe("playtime");
      expect(lb.items.length).toBe(3);

      // Bob has 2880m (2d), Alice 1500m (1d 1h), Charlie 120m (2h)
      expect(lb.items[0]?.pid).toBe(102);
      expect(lb.items[0]?.rank).toBe(1);
      expect(lb.items[0]?.value).toBe(2880);
      expect(lb.items[0]?.formattedValue).toBe("2d");

      expect(lb.items[1]?.pid).toBe(101);
      expect(lb.items[1]?.rank).toBe(2);
      expect(lb.items[1]?.value).toBe(1500);
      expect(lb.items[1]?.formattedValue).toBe("1d 1h");

      expect(lb.items[2]?.pid).toBe(103);
      expect(lb.items[2]?.rank).toBe(3);
      expect(lb.items[2]?.value).toBe(120);
      expect(lb.items[2]?.formattedValue).toBe("2h");
    });

    it("returns pvp leaderboard with correct rating tie-breaking and resolved PIDs", async () => {
      const lb = await getLeaderboard("pvp", 1, 1, 10, db);
      expect(lb.category).toBe("pvp");
      expect(lb.ladder).toBe("minipvp");
      expect(lb.season).toBe(1);
      expect(lb.items.length).toBe(2);

      expect(lb.items[0]?.pid).toBe(102);
      expect(lb.items[0]?.nickname).toBe("Bob [Pro]");
      expect(lb.items[0]?.value).toBe(1650);
      expect(lb.items[0]?.rank).toBe(1);
      expect(lb.items[0]?.matches).toBe(25);
      expect(lb.items[0]?.wins).toBe(18);

      expect(lb.items[1]?.pid).toBe(101);
      expect(lb.items[1]?.nickname).toBe("Alice");
      expect(lb.items[1]?.value).toBe(1500);
      expect(lb.items[1]?.rank).toBe(2);
    });

    it("returns HexedCore Elo using ladder hexed and correct season", async () => {
      const lb = await getLeaderboard("hexed-elo", 1, undefined, 10, db);
      expect(lb.category).toBe("hexed-elo");
      expect(lb.ladder).toBe("hexed");
      expect(lb.season).toBe(2); // Season 2 is ACTIVE
      expect(lb.items.length).toBe(1);
      expect(lb.items[0]?.pid).toBe(101);
      expect(lb.items[0]?.value).toBe(1420);
    });

    it("keeps the built-in Hexed rank/points top separate from seasonal Elo", async () => {
      const legacyDb = createMockDb({ players: [
        { pid: 101, nickname: "Higher rank", hexed_rank: 2, hexed_points: 100 },
        { pid: 102, nickname: "More points", hexed_rank: 1, hexed_points: 200 },
        { pid: 103, nickname: "Tie", hexed_rank: 1, hexed_points: 200 },
      ] });
      const top = await getLeaderboard("hexed", 1, undefined, 10, legacyDb);
      expect(top.items.map(player => player.pid)).toEqual([101, 102, 103]);
      expect(top.items.map(player => player.value)).toEqual([100, 200, 200]);
      expect(top.season).toBeUndefined();
      expect(top.ladder).toBeUndefined();
    });

    it("keeps route behavior honest when requested season has no data", async () => {
      const lb = await getLeaderboard("pvp", 1, 999, 10, db);
      expect(lb.category).toBe("pvp");
      expect(lb.season).toBe(999);
      expect(lb.items).toBeEmpty();
      expect(lb.total).toBe(0);
    });
  });

  // 5. Matches Listing & Enrichment
  describe("listMatches & getMatchDetail", () => {
    // The cross-collection union and pagination run against real Mongo in integration.test.ts.
    it("lists the canonical PvP ladder enriched through the shared match ID", async () => {
      const res = await listMatches({ ladder: "pvp" }, db);
      expect(res.items.length).toBe(1);

      const pvpMatch = res.items.find((m: PublicMatchSummary) => m.matchId === "match-pvp-001");
      expect(pvpMatch).toBeDefined();
      expect(pvpMatch?.gameMode).toBe("MiniPvP");
      expect(pvpMatch?.category).toBe("PVP");
      expect(pvpMatch?.ranked).toBe(true);
      expect(pvpMatch?.ladder).toBe("minipvp");
      expect(pvpMatch?.season).toBe(1);
      // Prioritizes started_at (12:00:00Z) over created_at (12:20:05Z persistence time)
      expect(pvpMatch?.durationSeconds).toBe(1200);
      expect("winningTeam" in res.items[0]!).toBe(false);
    });

    it("prioritizes rating_matches.map string preferentially and resolves game.map via maps collection", async () => {
      // match-pvp-001 has rating_matches.map = "QA Craters" -> preferred over game.map ObjectId
      const detailPvp = await getMatchDetail("match-pvp-001", "minipvp", db);
      expect(detailPvp?.map).toBe("QA Craters");

      // match-hexed-002 has game.map ObjectId pointing to nonexistent map -> returns null (never raw hex)
      const detailHexed = await getMatchDetail("match-hexed-002", undefined, db);
      expect(detailHexed?.map).toBeNull();
      expect(detailHexed?.durationSeconds).toBe(2700); // 45 minutes from started_at to ended_at
    });

    it("filters matches by PID and returns player outcome", async () => {
      const res = await listMatches({ pid: 101, ladder: "minipvp" }, db);
      expect(res.items.length).toBe(1);

      const match = res.items.find((m: PublicMatchSummary) => m.matchId === "match-pvp-001");
      expect(match?.playerOutcome).toBeDefined();
      expect(match?.playerOutcome?.win).toBe(true);
      expect(match?.playerOutcome?.placement).toBe(1);
      expect(match?.playerOutcome?.ratingDelta).toBe(15);
      expect(match?.playerOutcome?.ratingAfter).toBe(1500);
    });

    it("returns null for nonexistent match in getMatchDetail", async () => {
      expect(await getMatchDetail("nonexistent-match", undefined, db)).toBeNull();
    });

    it("returns null when requested ladder is missing rather than silently selecting another ladder", async () => {
      // match-pvp-001 belongs to "minipvp", requesting "hexed" must return null
      const match = await getMatchDetail("match-pvp-001", "hexed", db);
      expect(match).toBeNull();
    });

    it("returns full match detail with public participant list resolved to PIDs using match_id", async () => {
      const detail = await getMatchDetail("match-pvp-001", "minipvp", db);
      expect(detail).not.toBeNull();
      if (!detail) return;

      expect(detail.matchId).toBe("match-pvp-001");
      expect(detail.ladder).toBe("minipvp");
      expect(detail.season).toBe(1);
      expect(detail.durationSeconds).toBe(1200);
      expect(detail.ranked).toBe(true);
      expect(detail.countedInStats).toBe(true);

      expect(detail.participants.length).toBe(2);

      // Participant 1: Alice (winner)
      const p1 = detail.participants[0]!;
      expect(p1.pid).toBe(101);
      expect(p1.name).toBe("Alice");
      expect(p1.win).toBe(true);
      expect(p1.placement).toBe(1);
      expect(p1.ratingBefore).toBe(1485);
      expect(p1.delta).toBe(15);
      expect(p1.ratingAfter).toBe(1500);
      expect(p1.blocksBuilt).toBe(120);
      expect(p1.unitsProduced).toBe(40);

      // Participant 2: Bob (loser)
      const p2 = detail.participants[1]!;
      expect(p2.pid).toBe(102);
      expect(p2.name).toBe("Bob [Pro]");
      expect(p2.win).toBe(false);
      expect(p2.placement).toBe(2);
      expect(p2.ratingBefore).toBe(1665);
      expect(p2.delta).toBe(-15);
      expect(p2.ratingAfter).toBe(1650);
      expect(p2.blocksBuilt).toBe(95);
    });
  });

  // 6. Utility Functions
  describe("Utility functions", () => {
    it("formatPlayTime formats minutes, hours, days correctly", () => {
      expect(formatPlayTime(0)).toBe("0m");
      expect(formatPlayTime(45)).toBe("45m");
      expect(formatPlayTime(90)).toBe("1h 30m");
      expect(formatPlayTime(120)).toBe("2h");
      expect(formatPlayTime(1440)).toBe("1d");
      expect(formatPlayTime(1500)).toBe("1d 1h");
    });

    it("safeInt converts valid and invalid values safely", () => {
      expect(safeInt(42)).toBe(42);
      expect(safeInt("42")).toBe(42);
      expect(safeInt("invalid", 10)).toBe(10);
      expect(safeInt(null, 5)).toBe(5);
      expect(safeInt(undefined, 7)).toBe(7);
      expect(safeInt("", 9)).toBe(9);
      expect(safeInt(undefined)).toBe(0);
    });

    it("safeIsoDate handles Date, timestamp, string and null safely", () => {
      const now = new Date("2026-10-27T12:00:00Z");
      expect(safeIsoDate(now)).toBe("2026-10-27T12:00:00.000Z");
      expect(safeIsoDate(now.getTime())).toBe("2026-10-27T12:00:00.000Z");
      expect(safeIsoDate("2026-10-27T12:00:00Z")).toBe("2026-10-27T12:00:00.000Z");
      expect(safeIsoDate(null)).toBeNull();
      expect(safeIsoDate("invalid-date")).toBeNull();
      expect(safeIsoDate(0)).toBeNull();
    });

    it("safeObjectIdString converts strings and ObjectId-like objects", () => {
      expect(safeObjectIdString("abc123")).toBe("abc123");
      expect(safeObjectIdString({ toHexString: () => "hex567" })).toBe("hex567");
      expect(safeObjectIdString(null)).toBeNull();
    });

    it("escapeRegex correctly escapes all regex metacharacters including backslashes and hyphens", () => {
      const raw = "-/\\^$*+?.()|[]{}" + "abc 123";
      const escaped = escapeRegex(raw);
      expect(() => new RegExp(escaped)).not.toThrow();
      expect(new RegExp(escaped).test(raw)).toBe(true);
      expect(new RegExp(escaped).test("prefix " + raw + " suffix")).toBe(true);
      expect(new RegExp(escaped).test("different string")).toBe(false);
    });
  });
});
