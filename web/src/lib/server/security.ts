import "server-only";
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import { hasLocale, type Locale } from "@/i18n/config";

export class RequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
  ) {
    super(code);
  }
}

export const randomToken = () => randomBytes(32).toString("base64url");
export const tokenHash = (value: string) =>
  createHash("sha256").update(value).digest("hex");

export function siteOrigin(): URL {
  const configured =
    process.env.WEB_ORIGIN ??
    (process.env.NODE_ENV !== "production" ? "http://localhost:3000" : "");
  if (!configured) throw new RequestError(503, "not-configured");
  const url = new URL(configured);
  const local = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
  if (
    (url.protocol !== "https:" && !(url.protocol === "http:" && local)) ||
    url.username ||
    url.password ||
    url.pathname !== "/" ||
    url.search ||
    url.hash
  ) {
    throw new RequestError(503, "not-configured");
  }
  return url;
}

export function safeReturnTo(
  value: string | null | undefined,
  lang: Locale = "en",
): string {
  if (
    !value ||
    value.length > 2_048 ||
    !value.startsWith("/") ||
    value.startsWith("//") ||
    /[\\\r\n]/.test(value)
  )
    return `/${lang}/me`;
  const base = "https://return.invalid";
  const url = new URL(value, base);
  const segments = url.pathname.split("/");
  if (
    url.origin !== base ||
    !hasLocale(segments[1] ?? "") ||
    !["me", "link", "players", "top", "matches"].includes(segments[2] ?? "")
  )
    return `/${lang}/me`;
  return `${url.pathname}${url.search}`;
}

export function localeOf(value: string | null | undefined): Locale {
  return value && hasLocale(value) ? value : "en";
}

export function constantEqual(a: string, b: string): boolean {
  const left = Buffer.from(a),
    right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}

export function assertSameOrigin(request: Request): void {
  if (
    request.headers.get("origin") !== siteOrigin().origin ||
    request.headers.get("sec-fetch-site") === "cross-site"
  ) {
    throw new RequestError(403, "invalid-origin");
  }
}

export function assertCsrf(value: unknown, expected: string): void {
  if (
    typeof value !== "string" ||
    value.length > 128 ||
    !constantEqual(value, expected)
  )
    throw new RequestError(403, "invalid-csrf");
}

export function publicError(error: unknown): { status: number; code: string } {
  return error instanceof RequestError
    ? { status: error.status, code: error.code }
    : { status: 503, code: "unavailable" };
}

export function pageNumber(value: unknown): number {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0
    ? Math.min(parsed, 1_000)
    : 1;
}
