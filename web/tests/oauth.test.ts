import { afterEach, expect, spyOn, test } from "bun:test";
import { authorizationUrl, exchangeIdentity } from "../src/lib/server/oauth";

const original = {
  origin: process.env.WEB_ORIGIN,
  id: process.env.DISCORD_CLIENT_ID,
  secret: process.env.DISCORD_CLIENT_SECRET,
};
afterEach(() => {
  for (const [key, value] of Object.entries({
    WEB_ORIGIN: original.origin,
    DISCORD_CLIENT_ID: original.id,
    DISCORD_CLIENT_SECRET: original.secret,
  })) {
    if (value === undefined) delete process.env[key];
    else process.env[key] = value;
  }
});
function configure() {
  process.env.WEB_ORIGIN = "https://xcore.example";
  process.env.DISCORD_CLIENT_ID = "123456789012345678";
  process.env.DISCORD_CLIENT_SECRET = "unit-test-only";
}

test("authorization uses state, S256 PKCE and only the identify scope", async () => {
  configure();
  const url = await authorizationUrl("browser-state", "a".repeat(43));
  expect(url.origin).toBe("https://discord.com");
  expect(url.searchParams.get("state")).toBe("browser-state");
  expect(url.searchParams.get("code_challenge_method")).toBe("S256");
  expect(url.searchParams.get("code_challenge")).not.toBe("a".repeat(43));
  expect(url.searchParams.get("scope")).toBe("identify");
  expect(url.searchParams.get("redirect_uri")).toBe(
    "https://xcore.example/api/auth/discord/callback",
  );
  expect(url.href).not.toContain("unit-test-only");
});

test("state and issuer mismatch stop before token exchange", async () => {
  configure();
  const fetch = spyOn(globalThis, "fetch");
  try {
    await expect(
      exchangeIdentity(
        new URL(
          "https://xcore.example/api/auth/discord/callback?code=code&state=foreign&iss=https%3A%2F%2Fdiscord.com",
        ),
        "browser-state",
        "a".repeat(43),
      ),
    ).rejects.toThrow();
    await expect(
      exchangeIdentity(
        new URL(
          "https://xcore.example/api/auth/discord/callback?code=code&state=browser-state&iss=https%3A%2F%2Fevil.invalid",
        ),
        "browser-state",
        "a".repeat(43),
      ),
    ).rejects.toThrow();
    expect(fetch).not.toHaveBeenCalled();
  } finally {
    fetch.mockRestore();
  }
});

test("successful exchange returns only validated identity, never OAuth tokens", async () => {
  configure();
  const fetch = spyOn(globalThis, "fetch").mockImplementation((async (
    input: URL | RequestInfo,
  ) => {
    if (String(input).endsWith("/oauth2/token"))
      return Response.json({
        access_token: "not-a-real-token",
        token_type: "Bearer",
        expires_in: 3600,
        scope: "identify",
      });
    return Response.json({
      id: "123456789012345678",
      username: "player",
      global_name: "<Player>",
      email: "private@example.invalid",
      access_token: "should-not-leak",
    });
  }) as typeof globalThis.fetch);
  try {
    const identity = await exchangeIdentity(
      new URL(
        "https://xcore.example/api/auth/discord/callback?code=code&state=browser-state&iss=https%3A%2F%2Fdiscord.com",
      ),
      "browser-state",
      "a".repeat(43),
    );
    expect(identity).toEqual({
      discordId: "123456789012345678",
      username: "<Player>",
    });
    expect(fetch).toHaveBeenCalledTimes(2);
  } finally {
    fetch.mockRestore();
  }
});
