import "server-only";
import { cache } from "react";
import { cookies } from "next/headers";
import { getWebDb } from "./db";
import { randomToken, tokenHash, siteOrigin, RequestError } from "./security";

const cookiePrefix = /^https:\/\//i.test(process.env.WEB_ORIGIN ?? "")
  ? "__Host-"
  : "";
export const SESSION_COOKIE = `${cookiePrefix}xcore_session`;
export const OAUTH_COOKIE = `${cookiePrefix}xcore_oauth`;
export type WebSession = {
  _id: string;
  discordId: string;
  username: string;
  csrf: string;
  createdAt: Date;
  expiresAt: Date;
};
export type OAuthAttempt = {
  _id: string;
  state: string;
  verifier: string;
  returnTo: string;
  expiresAt: Date;
};

let indexes: Promise<void> | undefined;
export async function sessionDb() {
  const db = await getWebDb();
  indexes ??= Promise.all(
    ["web_sessions", "web_oauth", "web_rate_limits", "web_account_intents"].map(
      (name) =>
        db
          .collection(name)
          .createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }),
    ),
  )
    .then(() => undefined)
    .catch((error) => {
      indexes = undefined;
      throw error;
    });
  await indexes;
  return db;
}

export function cookieOptions(maxAge: number, path = "/") {
  return {
    httpOnly: true,
    secure: siteOrigin().protocol === "https:",
    sameSite: "lax" as const,
    path,
    maxAge,
  };
}

export async function readSession(
  token: string | undefined,
): Promise<WebSession | null> {
  if (!token || !/^[\w-]{43}$/.test(token)) return null;
  const db = await sessionDb();
  return db
    .collection<WebSession>("web_sessions")
    .findOne({ _id: tokenHash(token), expiresAt: { $gt: new Date() } });
}

export const getSession = cache(async () =>
  readSession((await cookies()).get(SESSION_COOKIE)?.value),
);

export async function requireSession(): Promise<WebSession> {
  const session = await getSession();
  if (!session) throw new RequestError(401, "sign-in-required");
  return session;
}

export async function createSession(
  discordId: string,
  username: string,
): Promise<{ token: string; maxAge: number; session: WebSession }> {
  const days = Number(process.env.WEB_SESSION_DAYS ?? 30);
  const maxAge =
    (Number.isFinite(days) ? Math.max(1, Math.min(days, 90)) : 30) * 86_400;
  const token = randomToken();
  const session: WebSession = {
    _id: tokenHash(token),
    discordId,
    username,
    csrf: randomToken(),
    createdAt: new Date(),
    expiresAt: new Date(Date.now() + maxAge * 1_000),
  };
  await (
    await sessionDb()
  )
    .collection<WebSession>("web_sessions")
    .insertOne(session);
  return { token, maxAge, session };
}

export async function revokeSession(token: string | undefined): Promise<void> {
  if (token && /^[\w-]{43}$/.test(token))
    await (
      await sessionDb()
    )
      .collection<WebSession>("web_sessions")
      .deleteOne({ _id: tokenHash(token) });
}

export async function rateLimit(
  kind: string,
  identity: string,
  limit: number,
  seconds = 600,
): Promise<void> {
  const window = Math.floor(Date.now() / (seconds * 1_000));
  const key = `${kind}:${tokenHash(identity)}:${window}`;
  const row = await (
    await sessionDb()
  )
    .collection<{ _id: string; count: number; expiresAt: Date }>(
      "web_rate_limits",
    )
    .findOneAndUpdate(
      { _id: key },
      {
        $inc: { count: 1 },
        $setOnInsert: { expiresAt: new Date((window + 2) * seconds * 1_000) },
      },
      { upsert: true, returnDocument: "after" },
    );
  if (!row || row.count > limit)
    throw new RequestError(429, "too-many-attempts");
}
