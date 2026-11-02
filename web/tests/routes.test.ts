import { afterEach, beforeEach, describe, expect, mock, spyOn, test } from "bun:test";
import { NextRequest } from "next/server";
import type { Db } from "mongodb";
import { POST as confirmAccount } from "../src/app/api/accounts/confirm/route";
import { GET as startOAuth } from "../src/app/api/auth/discord/route";
import * as accounts from "../src/lib/server/accounts";
import * as sessions from "../src/lib/server/session";
import { RequestError } from "../src/lib/server/security";

const origin = "https://xcore.example";
const operationId = "20000000-0000-4000-8000-000000000001";
const session: sessions.WebSession = {
  _id: "hashed-session",
  discordId: "123456789012345678",
  username: "Test player",
  csrf: "current-session-csrf",
  createdAt: new Date(),
  expiresAt: new Date(Date.now() + 60_000),
};
const originalEnv = Object.fromEntries(
  ["WEB_ORIGIN", "DISCORD_CLIENT_ID", "DISCORD_CLIENT_SECRET", "TRUSTED_PROXY_IP_HEADER"]
    .map(key => [key, process.env[key]]),
);
const restoreSpies: (() => void)[] = [];
beforeEach(() => {
  process.env.WEB_ORIGIN = origin;
  process.env.DISCORD_CLIENT_ID = "123456789012345678";
  process.env.DISCORD_CLIENT_SECRET = "test-only";
  delete process.env.TRUSTED_PROXY_IP_HEADER;
});
afterEach(() => {
  for (const restore of restoreSpies.splice(0)) restore();
  for (const [key, value] of Object.entries(originalEnv)) {
    if (value === undefined) delete process.env[key];
    else process.env[key] = value;
  }
});

function confirmation(form: Record<string, string> = {}, headers: HeadersInit = {}) {
  return new NextRequest(`${origin}/api/accounts/confirm`, {
    method: "POST",
    headers: { origin, ...headers },
    body: new URLSearchParams({ lang: "uk", csrf: session.csrf, operationId, ...form }),
  });
}

function mockConfirmation() {
  const read = spyOn(sessions, "requireSession").mockResolvedValue(session);
  const confirm = spyOn(accounts, "confirmIntent").mockResolvedValue({ operationId, status: "success" });
  restoreSpies.push(() => read.mockRestore(), () => confirm.mockRestore());
  return { read, confirm };
}

function expectFormRedirect(response: Response, error?: string, lang = "uk") {
  expect(response.status).toBe(303);
  expect(response.headers.get("cache-control")).toBe("no-store");
  const location = new URL(response.headers.get("location")!);
  expect(location.origin).toBe(origin);
  expect(location.pathname).toBe(`/${lang}/me`);
  expect(location.searchParams.get("error")).toBe(error ?? null);
  return location;
}

describe("browser account confirmation", () => {
  test("successful form submission redirects to the operation result", async () => {
    const { confirm } = mockConfirmation();
    const location = expectFormRedirect(await confirmAccount(confirmation()));
    expect(location.searchParams.get("operation")).toBe(operationId);
    expect(confirm).toHaveBeenCalledWith(session, operationId);
  });

  test.each([
    [409, "operation-expired"],
    [429, "too-many-attempts"],
  ])("expected failure %s/%s redirects with the operation ID", async (status, code) => {
    const { confirm } = mockConfirmation();
    confirm.mockRejectedValue(new RequestError(status, code));
    const location = expectFormRedirect(await confirmAccount(confirmation()), code);
    expect(location.searchParams.get("operation")).toBe(operationId);
  });

  test("CSRF from a rotated session cannot dispatch an operation", async () => {
    const { confirm } = mockConfirmation();
    const location = expectFormRedirect(
      await confirmAccount(confirmation({ csrf: "previous-session-csrf" })),
      "invalid-csrf",
    );
    expect(location.searchParams.get("operation")).toBe(operationId);
    expect(confirm).not.toHaveBeenCalled();
  });

  test("an expired session preserves localized navigation but cannot dispatch", async () => {
    const { read, confirm } = mockConfirmation();
    read.mockRejectedValue(new RequestError(401, "sign-in-required"));
    const location = expectFormRedirect(await confirmAccount(confirmation()), "sign-in-required");
    expect(location.searchParams.get("operation")).toBe(operationId);
    expect(confirm).not.toHaveBeenCalled();
  });

  test("cross-origin forms and cross-site requests remain rejected", async () => {
    const { read, confirm } = mockConfirmation();
    for (const headers of [{ origin: "https://evil.invalid" }, { "sec-fetch-site": "cross-site" }] as HeadersInit[]) {
      expectFormRedirect(await confirmAccount(confirmation({}, headers)), "invalid-origin", "en");
    }
    expect(read).not.toHaveBeenCalled();
    expect(confirm).not.toHaveBeenCalled();
  });

  test("malformed operation IDs are not reflected and unsupported locales fall back", async () => {
    const { confirm } = mockConfirmation();
    confirm.mockRejectedValue(new RequestError(400, "invalid-operation"));
    const location = expectFormRedirect(
      await confirmAccount(confirmation({ lang: "//evil.invalid", operationId: "-".repeat(36) })),
      "invalid-operation", "en",
    );
    expect(location.searchParams.has("operation")).toBe(false);
  });

  test("unexpected failures are redacted and never become a successful result", async () => {
    const { confirm } = mockConfirmation();
    confirm.mockRejectedValue(new TypeError("private database credentials"));
    const response = await confirmAccount(confirmation());
    expectFormRedirect(response, "unavailable");
    expect(response.headers.get("location")).not.toContain("private");
  });

  test("JSON is only the fallback when the configured origin is unusable", async () => {
    const { confirm } = mockConfirmation();
    process.env.WEB_ORIGIN = "http://public.example";
    const response = await confirmAccount(confirmation());
    expect(response.status).toBe(503);
    expect(response.headers.get("location")).toBeNull();
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(await response.json()).toEqual({ error: "not-configured" });
    expect(confirm).not.toHaveBeenCalled();
  });
});

function mockOAuthStore() {
  const insert = mock(async () => ({}));
  const db = spyOn(sessions, "sessionDb").mockResolvedValue({
    collection: () => ({ insertOne: insert }),
  } as unknown as Db);
  const limit = spyOn(sessions, "rateLimit").mockResolvedValue(undefined);
  restoreSpies.push(() => db.mockRestore(), () => limit.mockRestore());
  return { insert, limit };
}

function oauthRequest(headers: HeadersInit = {}) {
  return new NextRequest(`${origin}/api/auth/discord?lang=ru`, { headers });
}

describe("OAuth rate-limit identity", () => {
  test("an unconfigured proxy never trusts supplied IP headers or a shared anonymous bucket", async () => {
    const { insert, limit } = mockOAuthStore();
    for (const headers of [{}, { "x-real-ip": "192.0.2.1", "x-forwarded-for": "192.0.2.2" }] as HeadersInit[]) {
      const response = await startOAuth(oauthRequest(headers));
      expect(new URL(response.headers.get("location")!).origin).toBe("https://discord.com");
    }
    expect(limit).not.toHaveBeenCalled();
    expect(insert).toHaveBeenCalledTimes(2);
  });

  test("missing or blank configured IPs skip limiting without falling back to other headers", async () => {
    const { limit } = mockOAuthStore();
    process.env.TRUSTED_PROXY_IP_HEADER = "x-trusted-client-ip";
    for (const headers of [{ "x-real-ip": "192.0.2.1" }, { "x-trusted-client-ip": "   " }] as HeadersInit[]) {
      await startOAuth(oauthRequest(headers));
    }
    expect(limit).not.toHaveBeenCalled();
  });

  test("only the configured proxy identity keys the limit, regardless of spoofed alternatives", async () => {
    const { limit } = mockOAuthStore();
    process.env.TRUSTED_PROXY_IP_HEADER = "X-Trusted-Client-IP";
    for (const ip of ["192.0.2.1", "192.0.2.2"]) {
      await startOAuth(oauthRequest({ "x-trusted-client-ip": "198.51.100.1", "x-real-ip": ip }));
    }
    expect(limit).toHaveBeenCalledTimes(2);
    expect(limit).toHaveBeenNthCalledWith(1, "oauth-start", "198.51.100.1", 100);
    expect(limit).toHaveBeenNthCalledWith(2, "oauth-start", "198.51.100.1", 100);
  });

  test("an exhausted trusted identity cannot start OAuth or store an attempt", async () => {
    const { insert, limit } = mockOAuthStore();
    process.env.TRUSTED_PROXY_IP_HEADER = "x-real-ip";
    limit.mockRejectedValue(new RequestError(429, "too-many-attempts"));
    const response = await startOAuth(oauthRequest({ "x-real-ip": "198.51.100.1" }));
    const location = new URL(response.headers.get("location")!);
    expect(location.pathname).toBe("/ru/me");
    expect(location.searchParams.get("error")).toBe("too-many-attempts");
    expect(response.cookies.get(sessions.OAUTH_COOKIE)).toBeUndefined();
    expect(insert).not.toHaveBeenCalled();
  });
});
