import { afterEach, describe, expect, test } from "bun:test";
import {
  assertCsrf,
  assertSameOrigin,
  constantEqual,
  pageNumber,
  randomToken,
  safeReturnTo,
  siteOrigin,
  tokenHash,
} from "../src/lib/server/security";
import { cookieOptions, readSession } from "../src/lib/server/session";

const originalOrigin = process.env.WEB_ORIGIN;
afterEach(() => {
  if (originalOrigin === undefined) delete process.env.WEB_ORIGIN;
  else process.env.WEB_ORIGIN = originalOrigin;
});

describe("navigation and request boundaries", () => {
  test("return targets cannot redirect to a foreign origin or auth endpoint", () => {
    for (const input of [
      "https://evil.invalid",
      "//evil.invalid",
      "/\\evil.invalid",
      "/en/me\nLocation: evil",
      "/api/auth/logout",
      "/en",
      "javascript:alert(1)",
    ])
      expect(safeReturnTo(input, "ru")).toBe("/ru/me");
    expect(safeReturnTo("/uk/link?code=ABC234", "en")).toBe(
      "/uk/link?code=ABC234",
    );
    expect(safeReturnTo("/en/players/123")).toBe("/en/players/123");
  });
  test("mutations require configured origin and reject cross-site requests", () => {
    process.env.WEB_ORIGIN = "https://xcore.example";
    for (const origin of ["null", ""]) {
      expect(() => assertSameOrigin(new Request("https://xcore.example/api/accounts/confirm", {
        method: "POST", headers: { origin },
      }))).toThrow();
    }
    expect(() =>
      assertSameOrigin(
        new Request("https://xcore.example/api/accounts/confirm", {
          method: "POST",
          headers: { origin: "https://evil.invalid" },
        }),
      ),
    ).toThrow();
    expect(() =>
      assertSameOrigin(
        new Request("https://xcore.example/api/accounts/confirm", {
          method: "POST",
          headers: {
            origin: "https://xcore.example",
            "sec-fetch-site": "cross-site",
          },
        }),
      ),
    ).toThrow();
    expect(() =>
      assertSameOrigin(
        new Request("https://xcore.example/api/accounts/confirm", {
          method: "POST",
          headers: { origin: "https://xcore.example" },
        }),
      ),
    ).not.toThrow();
  });
  test("only HTTPS or loopback origins are accepted", () => {
    for (const value of [
      "http://public.example",
      "https://xcore.example/evil",
      "https://user:pass@xcore.example",
      "ftp://xcore.example",
    ]) {
      process.env.WEB_ORIGIN = value;
      expect(() => siteOrigin()).toThrow();
    }
    process.env.WEB_ORIGIN = "http://localhost:3000";
    expect(siteOrigin().origin).toBe("http://localhost:3000");
  });
  test("CSRF proof is mandatory and page limits are bounded", () => {
    const proof = randomToken();
    expect(() => assertCsrf(null, proof)).toThrow();
    expect(() => assertCsrf(`${proof}x`, proof)).toThrow();
    expect(() => assertCsrf(proof, proof)).not.toThrow();
    expect(constantEqual("aa", "ab")).toBe(false);
    expect(pageNumber("999999999")).toBe(1000);
    expect(pageNumber("$gt")).toBe(1);
  });
});

describe("session boundaries", () => {
  test("session tokens are opaque, hashed for storage, and invalid tokens do not access Mongo", async () => {
    const token = randomToken();
    expect(token).toMatch(/^[\w-]{43}$/);
    expect(tokenHash(token)).toMatch(/^[0-9a-f]{64}$/);
    expect(tokenHash(token)).not.toContain(token);
    expect(await readSession("attacker-session")).toBeNull();
    expect(await readSession(undefined)).toBeNull();
  });
  test("production cookies are host-only, Secure, HttpOnly and SameSite Lax", () => {
    process.env.WEB_ORIGIN = "https://xcore.example";
    expect(cookieOptions(3600)).toEqual({
      httpOnly: true,
      secure: true,
      sameSite: "lax",
      path: "/",
      maxAge: 3600,
    });
    expect(cookieOptions(600, "/api/auth").path).toBe("/api/auth");
  });
});
