import "server-only";
import { randomUUID } from "node:crypto";
import { createClient } from "redis";
import type {
  DiscordAccountOperationRequestV1,
  DiscordAccountOperationResponseV1,
} from "../../../../xcore-protocol/typescript/discord-accounts";
import { getGameDb } from "./db";
import { sessionDb, rateLimit, type WebSession } from "./session";
import { RequestError } from "./security";

type Intent = {
  _id: string;
  sessionId: string;
  discordId: string;
  username: string;
  action: "link" | "unlink";
  code?: string;
  uuid: string;
  pid: number;
  nickname: string;
  server: string;
  expectedLinkedAt?: number;
  issuedAt: number;
  executeBefore: number;
  expiresAt: Date;
  result?: AccountResult;
};
export type AccountResult = {
  operationId: string;
  status: "success" | "rejected" | "failed" | "unknown";
  reason?: string;
  linkedAt?: number;
};
export type IntentView = Pick<Intent, "_id" | "action" | "pid" | "nickname">;

let redisConnection: ReturnType<typeof connectRedis> | undefined;
async function connectRedis() {
  if (!process.env.REDIS_URL)
    throw new RequestError(503, "linking-unavailable");
  const client = createClient({
    url: process.env.REDIS_URL,
    disableOfflineQueue: true,
    commandOptions: { timeout: 2_000 },
    socket: { connectTimeout: 2_000, reconnectStrategy: false },
  });
  client.on("error", () => {});
  await client.connect();
  return client;
}

async function redisClient() {
  redisConnection ??= connectRedis().catch((error) => {
    redisConnection = undefined;
    throw error;
  });
  const client = await redisConnection;
  if (!client.isReady) {
    redisConnection = undefined;
    return redisClient();
  }
  return client;
}

function serverName(value: unknown): string {
  if (typeof value !== "string" || !/^[\w.-]{1,64}$/.test(value))
    throw new RequestError(503, "linking-unavailable");
  return value;
}

async function liveExecutor(candidates: string[]): Promise<string> {
  if (!candidates.length) throw new RequestError(503, "linking-unavailable");
  const names = candidates.map(serverName);
  const recent = await (await redisClient()).xRevRange(
    "xcore:evt:server:heartbeat", "+", `${Date.now() - 90_000}-0`, { COUNT: 512 },
  );
  const live = new Set<string>();
  for (const entry of recent ?? []) {
    try {
      const payload: unknown = JSON.parse(entry.message.payload_json ?? "null");
      if (payload && typeof payload === "object" &&
          "messageType" in payload && payload.messageType === "server.heartbeat" &&
          "messageVersion" in payload && payload.messageVersion === 1 &&
          "serverName" in payload && typeof payload.serverName === "string") live.add(payload.serverName);
    } catch { /* Ignore malformed heartbeat entries. */ }
  }
  const target = names.find(name => live.has(name));
  if (!target) throw new RequestError(503, "linking-unavailable");
  return target;
}

function view(intent: Intent): IntentView {
  return {
    _id: intent._id,
    action: intent.action,
    pid: intent.pid,
    nickname: intent.nickname,
  };
}

export async function previewLink(
  session: WebSession,
  input: string,
): Promise<IntentView> {
  await rateLimit("link-preview", session.discordId, 15);
  const code = input.trim().toUpperCase();
  if (!/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/.test(code))
    throw new RequestError(400, "invalid-code");
  const payload = await (
    await redisClient()
  ).get(`xcore:discord-link:code:${code}`);
  if (!payload) throw new RequestError(404, "code-not-found");
  const data: unknown = JSON.parse(payload);
  if (
    !data ||
    typeof data !== "object" ||
    !("playerUuid" in data) ||
    typeof data.playerUuid !== "string" ||
    !("playerPid" in data) ||
    typeof data.playerPid !== "number" ||
    !("expiresAt" in data) ||
    typeof data.expiresAt !== "number" ||
    !("server" in data)
  ) {
    throw new RequestError(503, "linking-unavailable");
  }
  if (data.expiresAt <= Date.now()) throw new RequestError(400, "code-expired");
  const player = await (
    await getGameDb()
  )
    .collection("players")
    .findOne(
      { uuid: data.playerUuid, pid: data.playerPid },
      { projection: { uuid: 1, pid: 1, nickname: 1, discord_id: 1 } },
    );
  if (!player) throw new RequestError(404, "player-not-found");
  if (player.discord_id && player.discord_id !== session.discordId)
    throw new RequestError(409, "already-linked");
  const now = Date.now();
  const intent: Intent = {
    _id: randomUUID(),
    sessionId: session._id,
    discordId: session.discordId,
    username: session.username,
    action: "link",
    code,
    uuid: data.playerUuid,
    pid: data.playerPid,
    nickname: String(player.nickname ?? "Unknown"),
    server: serverName(data.server),
    issuedAt: now,
    executeBefore: Math.min(data.expiresAt, now + 120_000),
    expiresAt: new Date(now + 86_400_000),
  };
  await (
    await sessionDb()
  )
    .collection<Intent>("web_account_intents")
    .insertOne(intent);
  return view(intent);
}

export async function previewUnlink(
  session: WebSession,
  pid: number,
): Promise<IntentView> {
  await rateLimit("unlink-preview", session.discordId, 15);
  if (!Number.isSafeInteger(pid) || pid < 1)
    throw new RequestError(400, "invalid-player");
  const player = await (
    await getGameDb()
  )
    .collection("players")
    .findOne(
      { pid, discord_id: session.discordId },
      {
        projection: {
          uuid: 1,
          pid: 1,
          nickname: 1,
          online: 1,
          online_server: 1,
          discord_linked_at: 1,
        },
      },
    );
  if (!player || typeof player.uuid !== "string")
    throw new RequestError(404, "player-not-found");
  const writable = (process.env.XCORE_ACCOUNT_SERVERS ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  const current =
    player.online && player.online_server
      ? String(player.online_server)
      : undefined;
  const target = await liveExecutor([
    ...(current && (!writable.length || writable.includes(current)) ? [current] : []),
    ...writable,
  ]);
  const now = Date.now();
  const intent: Intent = {
    _id: randomUUID(),
    sessionId: session._id,
    discordId: session.discordId,
    username: session.username,
    action: "unlink",
    uuid: player.uuid,
    pid,
    nickname: String(player.nickname ?? "Unknown"),
    server: serverName(target),
    expectedLinkedAt: Number(player.discord_linked_at ?? 0),
    issuedAt: now,
    executeBefore: now + 120_000,
    expiresAt: new Date(now + 86_400_000),
  };
  await (
    await sessionDb()
  )
    .collection<Intent>("web_account_intents")
    .insertOne(intent);
  return view(intent);
}

export function parseOperationResult(
  value: unknown,
  operationId: string,
  server: string,
): AccountResult {
  if (
    !value ||
    typeof value !== "object" ||
    !("messageType" in value) ||
    value.messageType !== "discord.account.operation.response" ||
    !("messageVersion" in value) ||
    value.messageVersion !== 1 ||
    !("server" in value) ||
    value.server !== server ||
    !("operationId" in value) ||
    value.operationId !== operationId ||
    !("status" in value) ||
    !["success", "rejected", "failed", "unknown"].includes(String(value.status))
  ) {
    throw new RequestError(502, "invalid-operation-response");
  }
  const data = value as DiscordAccountOperationResponseV1;
  return {
    operationId,
    status: data.status,
    ...(typeof data.reason === "string"
      ? { reason: data.reason.slice(0, 100) }
      : {}),
    ...(typeof data.linkedAt === "number" ? { linkedAt: data.linkedAt } : {}),
  };
}

async function sendOperation(
  payload: DiscordAccountOperationRequestV1,
): Promise<AccountResult> {
  const client = await redisClient();
  const reader = client.duplicate();
  reader.on("error", () => {});
  try {
    await reader.connect();
    const replyTo = "xcore:rpc:resp:web",
      correlationId = randomUUID();
    const tail = await client.xRevRange(replyTo, "+", "-", { COUNT: 1 });
    let cursor = tail?.[0]?.id ?? "0-0";
    const now = Date.now(),
      timeout = 10_000;
    await client.xAdd(
      `xcore:rpc:req:${payload.server}`,
      "*",
      {
        schema_version: "1",
        rpc_type: payload.messageType,
        correlation_id: correlationId,
        request_id: randomUUID(),
        idempotency_key: `web.account:${payload.operationId}`,
        reply_to: replyTo,
        requested_by: "web",
        server: payload.server,
        timeout_ms: String(timeout),
        created_at: String(now),
        // A fresh transport envelope may retrieve an expired operation's receipt.
        expires_at: String(now + timeout),
        payload_json: JSON.stringify(payload),
      },
      {
        TRIM: { strategy: "MAXLEN", strategyModifier: "~", threshold: 10_000 },
      },
    );
    const deadline = now + timeout;
    while (Date.now() < deadline) {
      const responses = await reader.xRead([{ key: replyTo, id: cursor }], {
        COUNT: 100,
        BLOCK: Math.min(1_000, Math.max(1, deadline - Date.now())),
      });
      for (const stream of responses ?? [])
        for (const message of stream.messages) {
          cursor = message.id;
          if (message.message.correlation_id !== correlationId) continue;
          if (message.message.status !== "ok")
            return {
              operationId: payload.operationId,
              status: "unknown",
              reason: "rpc-error",
            };
          return parseOperationResult(
            JSON.parse(message.message.payload_json ?? "null"),
            payload.operationId,
            payload.server,
          );
        }
    }
    return {
      operationId: payload.operationId,
      status: "unknown",
      reason: "timeout",
    };
  } finally {
    reader.destroy();
  }
}

export async function confirmIntent(
  session: WebSession,
  operationId: string,
): Promise<AccountResult> {
  if (!/^[0-9a-f-]{36}$/i.test(operationId))
    throw new RequestError(400, "invalid-operation");
  const collection = (await sessionDb()).collection<Intent>(
    "web_account_intents",
  );
  const intent = await collection.findOne({
    _id: operationId,
    sessionId: session._id,
    discordId: session.discordId,
  });
  if (!intent) throw new RequestError(404, "operation-not-found");
  if (intent.result && intent.result.status !== "unknown") return intent.result;
  if (intent.expiresAt.getTime() <= Date.now())
    throw new RequestError(404, "operation-not-found");
  if (intent.executeBefore <= Date.now() && !intent.result)
    throw new RequestError(409, "operation-expired");
  await rateLimit("account-confirm", session.discordId, 15);
  // Persist uncertainty before sending: the worker may stop after dispatch.
  await collection.updateOne(
    { _id: operationId, sessionId: session._id, result: { $exists: false } },
    { $set: { result: { operationId, status: "unknown", reason: "awaiting-result" } } },
  );
  const payload: DiscordAccountOperationRequestV1 = {
    messageType: "discord.account.operation.request",
    messageVersion: 1,
    server: intent.server,
    operationId: intent._id,
    action: intent.action,
    player: {
      playerUuid: intent.uuid,
      playerPid: intent.pid,
      playerName: intent.nickname,
    },
    discord: {
      discordId: session.discordId,
      discordUsername: intent.username,
    },
    issuedAt: intent.issuedAt,
    expiresAt: intent.executeBefore,
    ...(intent.code ? { code: intent.code } : {}),
    ...(intent.expectedLinkedAt !== undefined
      ? { expectedLinkedAt: intent.expectedLinkedAt }
      : {}),
  };
  let result: AccountResult;
  try {
    result = await sendOperation(payload);
  } catch {
    result = { operationId, status: "unknown", reason: "unavailable" };
  }
  // Without a receipt, an expired replay cannot establish the original outcome.
  if (result.status === "rejected" && result.reason === "request-expired")
    result = { operationId, status: "unknown", reason: "result-unavailable" };
  await collection.updateOne(
    {
      _id: operationId,
      sessionId: session._id,
      $or: [{ result: { $exists: false } }, { "result.status": "unknown" }],
    },
    { $set: { result } },
  );
  return (
    (await collection.findOne({ _id: operationId, sessionId: session._id }))
      ?.result ?? result
  );
}

export async function getIntentResult(
  session: WebSession,
  operationId: string,
): Promise<(AccountResult & { action: "link" | "unlink" }) | null> {
  if (!/^[0-9a-f-]{36}$/i.test(operationId)) return null;
  const intent = await (
    await sessionDb()
  )
    .collection<Intent>("web_account_intents")
    .findOne({
      _id: operationId,
      sessionId: session._id,
      discordId: session.discordId,
    });
  return intent?.result ? { ...intent.result, action: intent.action } : null;
}
