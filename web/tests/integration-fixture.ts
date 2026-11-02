import { ObjectId, type Db, type Document } from "mongodb";

export const FIXTURE_DISCORD_ID = "123456789012345678";
export const FIXTURE_GAME_ID = "10000000-0000-4000-8000-000000000001";
export const FIXTURE_HEXED_ID = "10000000-0000-4000-8000-000000000002";
export const FIXTURE_UUID = "fixture-private-player-one";

export async function seedIntegrationData(db: Db): Promise<void> {
  if (!db.databaseName.startsWith("xcore_web_test_"))
    throw new Error("Fixture databases must start with xcore_web_test_");
  for (const name of [
    "players",
    "games_v2",
    "rating_matches",
    "rating_standings",
    "rating_seasons",
    "maps",
  ])
    await db.collection(name).deleteMany({});
  const map = new ObjectId("650000000000000000000001");
  await db.collection("maps").insertOne({ _id: map, name: "QA Craters" });
  await db.collection("players").insertMany([
    {
      uuid: FIXTURE_UUID,
      pid: 101,
      nickname: "QA Alpha",
      username: "qa-alpha",
      total_play_time: 6000,
      discord_id: FIXTURE_DISCORD_ID,
      discord_username: "QA Discord",
      discord_linked_at: 1_000,
      online: true,
      online_server: "qa-game",
      password_hash: "fixture-private-password",
      last_ip: "192.0.2.1",
      device_tokens: { "fixture-private-device": 9999999999999 },
      unlocked_badges: ["veteran"],
      active_badge: "veteran",
      hexed_rank: 2,
      hexed_points: 100,
    },
    {
      uuid: "fixture-private-player-two",
      pid: 102,
      nickname: "QA Beta",
      total_play_time: 3000,
      discord_id: FIXTURE_DISCORD_ID,
      discord_linked_at: 2_000,
      online: false,
      hexed_rank: 1,
      hexed_points: 200,
    },
    {
      uuid: "fixture-private-player-three",
      pid: 103,
      nickname: "QA Gamma",
      total_play_time: 120,
      discord_id: "",
      discord_linked_at: 0,
      online: true,
      online_server: "qa-game",
    },
  ]);
  await db
    .collection("rating_seasons")
    .insertMany(
      ["minipvp", "hexed"].map((ladder) => ({
        ladder,
        number: 1,
        status: "ACTIVE",
        name: "QA Season",
        starts_at: new Date("2026-10-01T00:00:00Z"),
        ends_at: new Date("2027-01-01T00:00:00Z"),
      })),
    );
  await db.collection("rating_standings").insertMany([
    {
      ladder: "minipvp",
      season: 1,
      player_uuid: FIXTURE_UUID,
      rating: 1450,
      peak_rating: 1500,
      matches: 12,
      wins: 8,
    },
    {
      ladder: "minipvp",
      season: 1,
      player_uuid: "fixture-private-player-two",
      rating: 1400,
      peak_rating: 1450,
      matches: 11,
      wins: 6,
    },
    {
      ladder: "hexed",
      season: 1,
      player_uuid: FIXTURE_UUID,
      rating: 1600,
      peak_rating: 1620,
      matches: 10,
      wins: 4,
    },
  ]);
  for (const [index, matchId] of [
    FIXTURE_GAME_ID,
    FIXTURE_HEXED_ID,
  ].entries()) {
    const start = Date.parse(`2026-10-27T10:${index ? "20" : "00"}:00Z`),
      end = start + 600_000,
      ladder = index ? "hexed" : "minipvp";
    await db.collection("games_v2").insertOne({
      match_id: matchId,
      map,
      game_mode: index ? "HexedCore" : "Mini-PvP",
      stats_category: index ? "HEXED" : "PVP",
      counted_in_stats: true,
      is_ranked: true,
      started_at: start,
      ended_at: end,
      created_at: end + 5_000,
      server_name: "qa-game",
      winning_team: FIXTURE_UUID,
      player_stats: [
        {
          uuid: FIXTURE_UUID,
          nickname: "QA Alpha",
          isWinner: true,
          placement: 1,
          blocks_built: 20,
          blocks_destroyed: 5,
          units_destroyed: 3,
        },
        {
          uuid: "fixture-private-player-two",
          nickname: "QA Beta",
          isWinner: false,
          placement: 2,
          blocks_built: 10,
        },
      ],
    });
    await db.collection<Document & { _id: string }>("rating_matches").insertOne({
      _id: `${ladder}:${matchId}`,
      ladder,
      season: 1,
      match_id: matchId,
      started_at: new Date(start),
      ended_at: new Date(end),
      rated: true,
      map: "QA Craters",
      players: 2,
      skip_reason: null,
      team_sizes: { "1": 1, "2": 1 },
      participants: [
        {
          uuid: FIXTURE_UUID,
          name: "QA Alpha",
          team: 1,
          placement: 1,
          win: true,
          rating_before: 1400,
          delta: 15,
          rating_after: 1415,
          counted: true,
          extra: { password_hash: "fixture-private-extra" },
        },
        {
          uuid: "fixture-private-player-two",
          name: "QA Beta",
          team: 2,
          placement: 2,
          win: false,
          rating_before: 1400,
          delta: -15,
          rating_after: 1385,
          counted: true,
        },
      ],
    });
  }
}
