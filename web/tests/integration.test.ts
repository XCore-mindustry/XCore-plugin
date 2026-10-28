import { afterAll, beforeAll, describe, expect, spyOn, test } from "bun:test";
import { NextRequest } from "next/server";
import { createClient } from "redis";
import { getGameDb } from "../src/lib/server/db";
import {
  createSession,
  readSession,
  revokeSession,
  sessionDb,
  type WebSession,
  OAUTH_COOKIE,
  SESSION_COOKIE,
} from "../src/lib/server/session";
import { GET as startOAuth } from "../src/app/api/auth/discord/route";
import { GET as finishOAuth } from "../src/app/api/auth/discord/callback/route";
import {
  confirmIntent,
  previewLink,
  previewUnlink,
} from "../src/lib/server/accounts";
import {
  getLeaderboard,
  getLinkedProfiles,
  getMatchDetail,
  getPlayerProfile,
  listMatches,
  searchPlayers,
} from "../src/lib/server/stats";
import {
  seedIntegrationData,
  FIXTURE_DISCORD_ID,
  FIXTURE_GAME_ID,
  FIXTURE_UUID,
} from "./integration-fixture";

describe.skipIf(process.env.XCORE_WEB_INTEGRATION !== "1")(
  "real Mongo and Redis boundaries",
  () => {
    const redis = createClient({
      url: process.env.REDIS_URL,
      socket: { reconnectStrategy: false },
    });
    let session: WebSession;
    beforeAll(async () => {
      expect(process.env.MONGO_DB_NAME).toMatch(/^xcore_web_test_/);
      expect(process.env.WEB_MONGO_DB_NAME).toMatch(/^xcore_web_test_/);
      await seedIntegrationData(await getGameDb());
    for (const name of ["web_sessions", "web_oauth", "web_account_intents", "web_rate_limits"]) await (await sessionDb()).collection(name).deleteMany({});
      await redis.connect();
    await redis.del(["xcore:rpc:req:qa-game", "xcore:rpc:resp:web", "xcore:evt:server:heartbeat"]);
    const heartbeat = (serverName: string) => ({ payload_json: JSON.stringify({
      messageType: "server.heartbeat", messageVersion: 1, serverName,
      discordChannelId: 0, players: 0, maxPlayers: 50, version: "qa",
    }) });
    await redis.xAdd("xcore:evt:server:heartbeat", `${Date.now() - 120_000}-0`, heartbeat("stopped"));
    await redis.xAdd("xcore:evt:server:heartbeat", "*", heartbeat("qa-game"));
      session = (await createSession(FIXTURE_DISCORD_ID, "QA Discord")).session;
    });
    afterAll(async () => {
      if (redis.isOpen) await redis.close();
    });

    test("sessions are revocable and expiry is checked before TTL cleanup", async () => {
      const { token, session: created } = await createSession(
        FIXTURE_DISCORD_ID,
        "QA",
      );
      expect((await readSession(token))?.discordId).toBe(FIXTURE_DISCORD_ID);
      expect(created._id).not.toBe(token);
      await (
        await sessionDb()
      )
        .collection<{ _id: string; expiresAt: Date }>("web_sessions")
        .updateOne({ _id: created._id }, { $set: { expiresAt: new Date(0) } });
      expect(await readSession(token)).toBeNull();
      const next = await createSession(FIXTURE_DISCORD_ID, "QA");
      await revokeSession(next.token);
      expect(await readSession(next.token)).toBeNull();
    });

  test("OAuth callback is browser-bound, single-use, rotates the session and preserves the game return path", async () => {
    const savedId = process.env.DISCORD_CLIENT_ID, savedSecret = process.env.DISCORD_CLIENT_SECRET;
    process.env.DISCORD_CLIENT_ID = "123456789012345678";
    process.env.DISCORD_CLIENT_SECRET = "integration-test-only";
    const old = await createSession(FIXTURE_DISCORD_ID, "Previous session");
    const fetch = spyOn(globalThis, "fetch").mockImplementation((async (input: URL | RequestInfo) => {
      if (String(input).endsWith("/oauth2/token")) return Response.json({ access_token: "test-token", token_type: "Bearer", expires_in: 3600, scope: "identify" });
      return Response.json({ id: FIXTURE_DISCORD_ID, username: "QA Discord" });
    }) as typeof globalThis.fetch);
    try {
      const origin = process.env.WEB_ORIGIN!;
      const started = await startOAuth(new NextRequest(`${origin}/api/auth/discord?lang=ru&returnTo=${encodeURIComponent("/ru/link?code=ABC234")}`));
      const authorize = new URL(started.headers.get("location")!);
      const nonce = started.cookies.get(OAUTH_COOKIE)!.value;
      expect(authorize.searchParams.get("scope")).toBe("identify");
      expect(authorize.searchParams.get("code_challenge_method")).toBe("S256");
      const state = authorize.searchParams.get("state")!;
      const wrong = await finishOAuth(new NextRequest(`${origin}/api/auth/discord/callback?state=foreign&code=test&iss=https%3A%2F%2Fdiscord.com`, { headers: { cookie: `${OAUTH_COOKIE}=${nonce}` } }));
      expect(wrong.cookies.get(SESSION_COOKIE)).toBeUndefined();
      expect(fetch).not.toHaveBeenCalled();
      const callback = `${origin}/api/auth/discord/callback?state=${state}&code=test&iss=https%3A%2F%2Fdiscord.com`;
      const completed = await finishOAuth(new NextRequest(callback, { headers: { cookie: `${OAUTH_COOKIE}=${nonce}; ${SESSION_COOKIE}=${old.token}` } }));
      expect(new URL(completed.headers.get("location")!).pathname).toBe("/ru/link");
      expect(new URL(completed.headers.get("location")!).searchParams.get("code")).toBe("ABC234");
      const token = completed.cookies.get(SESSION_COOKIE)!.value;
      expect(token).not.toBe(old.token);
      expect((await readSession(token))?.discordId).toBe(FIXTURE_DISCORD_ID);
      expect(await readSession(old.token)).toBeNull();
      const replay = await finishOAuth(new NextRequest(callback, { headers: { cookie: `${OAUTH_COOKIE}=${nonce}` } }));
      expect(replay.cookies.get(SESSION_COOKIE)).toBeUndefined();
      expect(new URL(replay.headers.get("location")!).searchParams.get("error")).toBe("sign-in-failed");
      expect(fetch).toHaveBeenCalledTimes(2);
    } finally {
      fetch.mockRestore();
      if (savedId === undefined) delete process.env.DISCORD_CLIENT_ID; else process.env.DISCORD_CLIENT_ID = savedId;
      if (savedSecret === undefined) delete process.env.DISCORD_CLIENT_SECRET; else process.env.DISCORD_CLIENT_SECRET = savedSecret;
    }
  });

    test("canonical production-shaped queries yield joined matches and private data stays private", async () => {
      const [players, profile, top, matches, personal, match, owned] =
        await Promise.all([
          searchPlayers("QA Alpha"),
          getPlayerProfile(101),
          getLeaderboard("hexed-elo"),
          listMatches(),
          listMatches({ pid: 101 }),
          getMatchDetail(FIXTURE_GAME_ID, "pvp"),
          getLinkedProfiles(FIXTURE_DISCORD_ID),
        ]);
      expect(players.items[0]?.pid).toBe(101);
      expect(profile?.overview.overall.gamesPlayed).toBe(2);
      expect(profile?.overview.overall.blocksBuilt).toBe(40);
      expect(top.items[0]?.value).toBe(1600);
      expect(matches.total).toBe(2);
      expect(personal.items[0]?.playerOutcome?.ratingDelta).toBe(15);
      expect(match?.participants).toHaveLength(2);
      expect(match?.durationSeconds).toBe(600);
      expect(match?.map).toBe("QA Craters");
      expect(owned).toHaveLength(2);
      const publicJson = JSON.stringify({
        players,
        profile,
        top,
        matches,
        personal,
        match,
        owned,
      });
      for (const secret of [
        "fixture-private",
        FIXTURE_DISCORD_ID,
        "192.0.2.1",
        "password_hash",
        "device_tokens",
      ])
        expect(publicJson).not.toContain(secret);
    });

    test("confirmed operation uses the canonical targeted RPC and repeats preserve its ID", async () => {
      const code = "ABC234";
      await redis.set(
        `xcore:discord-link:code:${code}`,
        JSON.stringify({
          code,
          playerUuid: "fixture-private-player-three",
          playerPid: 103,
          playerNickname: "QA Gamma",
          server: "qa-game",
          createdAt: Date.now(),
          expiresAt: Date.now() + 600_000,
        }),
        { EX: 600 },
      );
      const intent = await previewLink(session, code);
      const listener = redis.duplicate();
      await listener.connect();
      try {
        const execute = async () => {
          const messages = await listener.xRead(
            [{ key: "xcore:rpc:req:qa-game", id: "0-0" }],
            { BLOCK: 5_000, COUNT: 1 },
          );
          const request = messages?.[0]?.messages[0]?.message;
          expect(request?.requested_by).toBe("web");
          expect(request?.reply_to).toBe("xcore:rpc:resp:web");
          const payload = JSON.parse(request!.payload_json);
          expect(payload.operationId).toBe(intent._id);
          expect(payload.discord.discordId).toBe(session.discordId);
          expect(payload.player.playerPid).toBe(103);
          expect(payload.action).toBe("link");
          expect(
            await redis.get(`xcore:discord-link:code:${code}`),
          ).not.toBeNull();
          await redis.xAdd(request!.reply_to, "*", {
            correlation_id: request!.correlation_id,
            status: "ok",
            payload_json: JSON.stringify({
              messageType: "discord.account.operation.response",
              messageVersion: 1,
              server: "qa-game",
              operationId: payload.operationId,
              status: "success",
              linkedAt: 12345,
            }),
          });
        };
        const [result] = await Promise.all([
          confirmIntent(session, intent._id),
          execute(),
        ]);
        expect(result.status).toBe("success");
        expect((await confirmIntent(session, intent._id)).operationId).toBe(
          intent._id,
        );
        expect(await redis.xLen("xcore:rpc:req:qa-game")).toBe(1);
        expect(
          (
            await (
              await getGameDb()
            )
              .collection("players")
              .findOne({ pid: 103 })
          )?.discord_id,
        ).toBe("");
      } finally {
        listener.destroy();
      }
    });

    test("the combined feed includes rating-only matches once, with real Mongo pagination and canonical PvP filters", async () => {
      const before = await listMatches({ pageSize: 1 });
      const matchId = "20000000-0000-4000-8000-000000000003";
      const ratings = (await getGameDb()).collection("rating_matches");
      await ratings.insertOne({
        ladder: "minipvp", match_id: matchId, season: 1,
        started_at: new Date("2026-10-28T12:00:00Z"),
        ended_at: new Date("2026-10-28T12:10:00Z"),
        rated: true, map: "Rating-only arena", players: 1,
        participants: [{ uuid: FIXTURE_UUID, name: "QA Alpha", win: true, delta: 9, placement: 1 }],
      });
      try {
        const [all, personal, next, alias, canonical, detail] = await Promise.all([
          listMatches({ pageSize: 1 }),
          listMatches({ pid: 101, pageSize: 1 }),
          listMatches({ page: 2, pageSize: 1 }),
          listMatches({ ladder: "pvp" }),
          listMatches({ ladder: "minipvp" }),
          getMatchDetail(matchId, "minipvp"),
        ]);
        expect(all.total).toBe(before.total + 1);
        expect(all.items.map(row => row.matchId)).toEqual([matchId]);
        expect(personal.items[0]?.matchId).toBe(matchId);
        expect(personal.items[0]?.playerOutcome?.ratingDelta).toBe(9);
        expect(next.items[0]?.matchId).not.toBe(matchId);
        expect(alias).toEqual(canonical);
        expect(canonical.items.every(row => row.ladder === "minipvp")).toBe(true);
        expect(detail?.participants[0]?.pid).toBe(101);
        expect(JSON.stringify({ all, personal, detail })).not.toContain(FIXTURE_UUID);
      } finally {
        await ratings.deleteOne({ match_id: matchId });
      }
    });

    test("a different session cannot submit another user's confirmation; unlink captures ownership generation", async () => {
      const own = await previewUnlink(session, 101);
      const stranger = (await createSession("999999999999999999", "Other"))
        .session;
      await expect(confirmIntent(stranger, own._id)).rejects.toThrow(
        "operation-not-found",
      );
      const row = await (
        await sessionDb()
      )
        .collection<{ _id: string; expectedLinkedAt: number; uuid: string }>(
          "web_account_intents",
        )
        .findOne({ _id: own._id });
      expect(row?.expectedLinkedAt).toBe(1000);
      expect(row?.uuid).toBe(FIXTURE_UUID);
      await expect(previewUnlink(stranger, 101)).rejects.toThrow(
        "player-not-found",
      );
    });

    test("an unknown operation can retrieve its receipt after the execution deadline without changing its payload", async () => {
      const intent = await previewUnlink(session, 101);
      const intents = (await sessionDb()).collection<{
        _id: string;
        executeBefore: number;
        issuedAt: number;
        result?: { operationId: string; status: string; reason?: string };
      }>("web_account_intents");
      const expired = Date.now() - 180_000;
      await intents.updateOne({ _id: intent._id }, { $set: {
        executeBefore: expired,
        issuedAt: expired - 120_000,
        result: { operationId: intent._id, status: "unknown", reason: "timeout" },
      } });

      async function checkReceipt(status: "success" | "rejected", reason?: string) {
        const tail = await redis.xRevRange("xcore:rpc:req:qa-game", "+", "-", { COUNT: 1 });
        const listener = redis.duplicate();
        await listener.connect();
        try {
          const executor = async () => {
            const streams = await listener.xRead([{ key: "xcore:rpc:req:qa-game", id: tail?.[0]?.id ?? "0-0" }], { BLOCK: 5_000, COUNT: 1 });
            const request = streams?.[0]?.messages[0]?.message;
            expect(request).toBeDefined();
            const payload = JSON.parse(request!.payload_json);
            expect(payload.operationId).toBe(intent._id);
            expect(payload.expiresAt).toBe(expired);
            expect(payload.discord.discordUsername).toBe("QA Discord");
            expect(Number(request!.expires_at)).toBeGreaterThan(Date.now());
            await redis.xAdd(request!.reply_to, "*", {
              correlation_id: request!.correlation_id,
              status: "ok",
              payload_json: JSON.stringify({ messageType: "discord.account.operation.response", messageVersion: 1, server: "qa-game", operationId: intent._id, status, reason }),
            });
          };
          const [result] = await Promise.all([
            confirmIntent({ ...session, username: "Changed after dispatch" }, intent._id),
            executor(),
          ]);
          return result;
        } finally { listener.destroy(); }
      }
      // A missing receipt cannot prove that the original operation was rejected.
      expect((await checkReceipt("rejected", "request-expired")).status).toBe("unknown");
      expect((await checkReceipt("success")).status).toBe("success");
      const count = await redis.xLen("xcore:rpc:req:qa-game");
      expect((await confirmIntent(session, intent._id)).status).toBe("success");
      expect(await redis.xLen("xcore:rpc:req:qa-game")).toBe(count);
    });

    test("an expired intent that was never dispatched cannot start a new operation", async () => {
      const intent = await previewUnlink(session, 101);
      await (await sessionDb()).collection<{ _id: string; executeBefore: number }>("web_account_intents")
        .updateOne({ _id: intent._id }, { $set: { executeBefore: Date.now() - 1 } });
      const count = await redis.xLen("xcore:rpc:req:qa-game");
      await expect(confirmIntent(session, intent._id)).rejects.toThrow("operation-expired");
      expect(await redis.xLen("xcore:rpc:req:qa-game")).toBe(count);
    });

    test("offline unlink requires an explicitly configured writable executor", async () => {
      const configured = process.env.XCORE_ACCOUNT_SERVERS;
      delete process.env.XCORE_ACCOUNT_SERVERS;
      try {
        await expect(previewUnlink(session, 102)).rejects.toThrow("linking-unavailable");
      } finally {
        if (configured !== undefined) process.env.XCORE_ACCOUNT_SERVERS = configured;
      }
    });

    test("offline unlink skips an expired executor heartbeat and selects a live writable fallback", async () => {
      const configured = process.env.XCORE_ACCOUNT_SERVERS;
      process.env.XCORE_ACCOUNT_SERVERS = "stopped,qa-game";
      try {
        const intent = await previewUnlink(session, 102);
        const row = await (await sessionDb()).collection<{ _id: string; server: string }>("web_account_intents")
          .findOne({ _id: intent._id });
        expect(row?.server).toBe("qa-game");
        process.env.XCORE_ACCOUNT_SERVERS = "stopped";
        await expect(previewUnlink(session, 102)).rejects.toThrow("linking-unavailable");
        expect((await (await sessionDb()).collection<{ _id: string; server: string }>("web_account_intents")
          .findOne({ _id: intent._id }))?.server).toBe("qa-game");
      } finally {
        if (configured === undefined) delete process.env.XCORE_ACCOUNT_SERVERS;
        else process.env.XCORE_ACCOUNT_SERVERS = configured;
      }
    });
  },
);
