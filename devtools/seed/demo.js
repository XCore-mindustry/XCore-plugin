// Run only against the isolated dev stack; rerunning inserts nothing twice.
(() => {
const DATABASE = "xcore_dev";
const SEED = "community-preview-v1";
const PLAYER_COUNT = 64;
const MATCH_COUNT = 180;
const DAY = 86_400_000;
const now = Date.now();
if (db.getName() !== DATABASE) throw new Error(`Demo seed requires ${DATABASE}`);

const check = (condition, message) => {
  if (!condition) throw new Error(message);
};
const oid = (group, index) => ObjectId(`d3${group}000000000000${String(index).padStart(8, "0")}`);
const uuid = (group, index) => `d3${group}0000-0000-4000-8000-${String(index).padStart(12, "0")}`;
const names = [
  "Nova", "CopperFox", "Кремний", "Moss", "ArcLight", "Рыжий", "Graphite", "Neko",
  "Tungsten", "Dagger", "Лис", "Quartz", "Snowfall", "Pulsar", "Механик", "Horizon",
  "Drifter", "Aster", "Север", "Ember", "Manta", "Nightfall", "Жук", "Lumen",
  "Orion", "Flint", "Титан", "Zephyr", "Monolith", "Raven", "Orbit", "Искра",
];
const badges = ["veteran", "contributor", "map-maker", "translator", "bug-finder", "event-winner", "developer", "season-champion"];
const players = Array.from({ length: PLAYER_COUNT }, (_, index) => {
  const unlocked = index === 0
    ? ["veteran", "contributor", "map-maker", "event-winner"]
    : [badges[index % badges.length], ...(index % 3 === 0 ? [badges[(index + 3) % badges.length]] : [])];
  return {
    _id: oid("10", index + 1), demo_seed: SEED,
    uuid: uuid("10", index + 1), pid: NumberInt(900001 + index),
    username: `demo-player-${String(index + 1).padStart(3, "0")}`,
    nickname: `${names[index % names.length]}${index >= names.length ? "_2" : ""} [demo]`,
    description: "Демонстрационный профиль для просмотра интерфейса. Все матчи и рейтинги вымышлены.",
    total_play_time: NumberInt(900 + (PLAYER_COUNT - index) * 1397),
    hexed_rank: NumberInt(Math.min(5, Math.floor((PLAYER_COUNT - index - 1) / 11))),
    hexed_points: NumberInt((index * 7 + 19) % 30),
    unlocked_badges: unlocked, active_badge: unlocked[0],
    online: false, online_server: "", is_admin: false, leaderboard: true,
    discord_id: "", discord_username: "", discord_linked_at: NumberLong("0"),
    created_at: NumberLong(String(now - (30 + index * 3) * DAY)),
    updated_at: NumberLong(String(now)), is_visible: true, version: NumberLong("1"),
  };
});

const session = db.getMongo().startSession();
session.startTransaction();
try {
  const target = session.getDatabase(DATABASE);
  const collections = ["players", "games_v2", "rating_matches", "rating_standings", "rating_seasons"];
  const untouched = Object.fromEntries(collections.map(name => [name, target.getCollection(name).countDocuments({ demo_seed: { $ne: SEED } })]));
  check(!target.players.findOne({
    demo_seed: { $ne: SEED },
    $or: [
      { pid: { $in: players.map(player => player.pid) } },
      { uuid: { $in: players.map(player => player.uuid) } },
      { username: { $in: players.map(player => player.username) } },
    ],
  }), "Reserved demo identity collides with an existing player");

  const maps = target.maps.find({}, { _id: 1, name: 1 }).sort({ name: 1 }).toArray();
  if (!maps.length) maps.push({ _id: oid("40", 1), name: "Demo terrain" });
  const seasons = {};
  const seasonDocs = [];
  for (const ladder of ["minipvp", "hexed"]) {
    const existing = target.rating_seasons.find({ ladder, status: "ACTIVE" }).sort({ number: -1 }).limit(1).toArray()[0];
    const season = existing ?? {
      _id: `${ladder}:1`, demo_seed: SEED, ladder, number: NumberInt(1),
      status: "ACTIVE", name: "Demo season",
      starts_at: new Date(now - 14 * DAY), ends_at: new Date(now + 90 * DAY),
    };
    check(!Number.isNaN(new Date(season.starts_at).getTime()) && new Date(season.starts_at).getTime() < now, "Active season must have started");
    seasons[ladder] = season;
    if (!existing) seasonDocs.push(season);
  }

  const standings = ["minipvp", "hexed"].flatMap((ladder, ladderIndex) => players.map((player, index) => {
    const position = ladderIndex ? (index * 17) % PLAYER_COUNT : index;
    const rating = 2750 - position * 38;
    return {
      _id: oid(ladderIndex ? "21" : "20", index + 1), demo_seed: SEED,
      ladder, season: seasons[ladder].number, player_uuid: player.uuid,
      rating, peak_rating: rating, matches: 0, wins: 0,
      created_at: new Date(seasons[ladder].starts_at), updated_at: new Date(now),
      stats: {}, applied_operations: [],
    };
  }));
  const byPlayer = new Map(standings.map(standing => [`${standing.ladder}:${standing.player_uuid}`, standing]));
  const games = [], ratingMatches = [];
  for (let index = 0; index < MATCH_COUNT; index++) {
    const kind = index % 3;
    const round = Math.floor(index / 3);
    const ladder = kind === 0 ? "minipvp" : kind === 1 ? "hexed" : null;
    const count = kind === 0 ? 4 + (round % 3) * 2 : kind === 1 ? 8 + (round % 5) * 2 : 4 + round % 5;
    const roster = [players[0], ...Array.from({ length: count - 1 }, (_, slot) => players[1 + (round * 5 + kind * 7 + slot * 11) % (PLAYER_COUNT - 1)])];
    const shift = round % count;
    const ordered = roster.slice(shift).concat(roster.slice(0, shift));
    check(new Set(ordered.map(player => player.uuid)).size === count, "Duplicate match participant");
    const lower = ladder ? Math.max(now - 14 * DAY, new Date(seasons[ladder].starts_at).getTime()) : now - 14 * DAY;
    const endedAt = lower + Math.floor((now - lower - 1000) * (index + 1) / (MATCH_COUNT + 1));
    const startedAt = Math.max(lower, endedAt - (8 + index % 29) * 60_000);
    const matchId = uuid("30", index + 1);
    const map = maps[index % maps.length];
    const survivalWin = round % 4 !== 0;
    const playerStats = [], participants = [];
    for (const [place, player] of ordered.entries()) {
      const win = kind === 0 ? place < count / 2 : kind === 1 ? place === 0 : survivalWin;
      const placement = kind === 0 ? (win ? 1 : 2) : kind === 1 ? place + 1 : 1;
      const team = kind === 0 ? (win ? 1 : 2) : kind === 1 ? place + 1 : 1;
      playerStats.push({
        uuid: player.uuid, nickname: player.nickname, isWinner: win, placement: NumberInt(placement),
        blocks_built: NumberInt(80 + (index * 19 + place * 31) % 1500),
        blocks_deconstructed: NumberInt((index + place * 3) % 65),
        blocks_destroyed: NumberInt(20 + (index * 7 + place * 17) % 700),
        units_produced: NumberInt(3 + (index + place * 11) % 100),
        units_destroyed: NumberInt((index * 13 + place * 5) % 140),
      });
      player.total_play_time = NumberInt(Number(player.total_play_time) + Math.max(1, Math.floor((endedAt - startedAt) / 60_000)));
      if (ladder) {
        const standing = byPlayer.get(`${ladder}:${player.uuid}`);
        const delta = kind === 0 ? (win ? 1 : -1) * (14 + round % 13) : (count - 1 - 2 * place) * 2;
        const before = standing.rating;
        standing.rating += delta;
        standing.peak_rating = Math.max(standing.peak_rating, standing.rating);
        standing.matches++;
        if (win) standing.wins++;
        participants.push({
          uuid: player.uuid, name: player.nickname, team: NumberInt(team), placement: NumberInt(placement), win,
          rating_before: NumberInt(before), delta: NumberInt(delta), rating_after: NumberInt(standing.rating),
          counted: true, reason: null, participation: 1, extra: {},
        });
      }
    }
    if (ladder) {
      ratingMatches.push({
        _id: `${ladder}:${matchId}`, demo_seed: SEED, ladder, season: seasons[ladder].number, match_id: matchId,
        started_at: new Date(startedAt), ended_at: new Date(endedAt),
        rated: true, skip_reason: null, finish: "NATURAL", map: String(map.name),
        algorithm: "demo", players: NumberInt(count),
        team_sizes: Object.fromEntries([...new Set(participants.map(player => Number(player.team)))].map(team => [String(team), NumberInt(participants.filter(player => Number(player.team) === team).length)])),
        participants,
      });
    }
    // A few rating-only records exercise the combined feed without duplicating matches.
    if (!ladder || index % 10 !== 0) {
      games.push({
        _id: oid("30", index + 1), demo_seed: SEED, match_id: matchId, map: map._id,
        game_mode: kind === 0 ? "Mini-PvP" : kind === 1 ? "HexedCore" : round % 3 === 0 ? "Mini-Attack" : "Mini-Surv",
        stats_category: kind === 0 ? "PVP" : kind === 1 ? "HEXED" : "SURVIVAL",
        server_name: `demo-${ladder ?? "survival"}`, is_ranked: !!ladder, counted_in_stats: true,
        started_at: NumberLong(String(startedAt)), ended_at: NumberLong(String(endedAt)),
        created_at: NumberLong(String(endedAt + 1000)), updated_at: NumberLong(String(endedAt + 1000)),
        waves_reached: NumberInt(kind === 2 ? 35 + round * 7 : 0),
        winning_team: ordered[0].uuid, finish_reason: "NATURAL", player_stats: playerStats,
        is_visible: true, version: NumberLong("1"),
      });
    }
  }
  check(standings.every(standing => standing.matches > 0 && standing.rating >= 100 && standing.peak_rating >= standing.rating), "Incomplete or invalid demo standings");
  check(games.length === 168 && ratingMatches.length === 120, "Unexpected fixture coverage");
  const datasets = { maps: maps.filter(map => String(map._id) === String(oid("40", 1))).map(map => ({ ...map, demo_seed: SEED })), players, rating_seasons: seasonDocs, games_v2: games, rating_matches: ratingMatches, rating_standings: standings };
  let inserted = 0;
  for (const [name, documents] of Object.entries(datasets)) {
    if (!documents.length) continue;
    const result = target.getCollection(name).bulkWrite(documents.map(document => ({
      updateOne: { filter: { _id: document._id, demo_seed: SEED }, update: { $setOnInsert: document }, upsert: true },
    })));
    inserted += result.upsertedCount;
  }
  for (const name of collections) {
    check(target.getCollection(name).countDocuments({ demo_seed: { $ne: SEED } }) === untouched[name], `Existing ${name} records changed`);
  }
  check(target.players.countDocuments({ demo_seed: SEED }) === PLAYER_COUNT, "Missing demo players");
  check(target.players.countDocuments({ demo_seed: SEED, $or: [{ online: true }, { is_admin: true }, { discord_id: { $ne: "" } }] }) === 0, "Demo identity must stay offline and unlinked");
  session.commitTransaction();
  print(JSON.stringify({ database: DATABASE, demo_seed: SEED, inserted, players: PLAYER_COUNT, matches: MATCH_COUNT, gameRecords: games.length, ratingMatches: ratingMatches.length, standings: standings.length, examplePid: 900001 }));
} catch (error) {
  session.abortTransaction();
  throw error;
} finally {
  session.endSession();
}
})();
