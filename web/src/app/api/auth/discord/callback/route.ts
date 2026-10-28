import { NextRequest, NextResponse } from "next/server";
import { exchangeIdentity } from "@/lib/server/oauth";
import {
  SESSION_COOKIE,
  OAUTH_COOKIE,
  cookieOptions,
  createSession,
  revokeSession,
  sessionDb,
  type OAuthAttempt,
} from "@/lib/server/session";
import {
  RequestError,
  safeReturnTo,
  siteOrigin,
  tokenHash,
} from "@/lib/server/security";

export async function GET(request: NextRequest) {
  let destination = "/en/me";
  try {
    const token = request.cookies.get(OAUTH_COOKIE)?.value,
      state = request.nextUrl.searchParams.get("state");
    if (!token || !/^[\w-]{43}$/.test(token) || !state || state.length > 256)
      throw new RequestError(400, "invalid-oauth");
    const attempt = await (
      await sessionDb()
    )
      .collection<OAuthAttempt>("web_oauth")
      .findOneAndDelete({
        _id: tokenHash(token),
        state,
        expiresAt: { $gt: new Date() },
      });
    if (!attempt) throw new RequestError(400, "invalid-oauth");
    destination = safeReturnTo(attempt.returnTo);
    if (request.nextUrl.searchParams.has("error"))
      throw new RequestError(400, "oauth-cancelled");
    const identity = await exchangeIdentity(
      new URL(request.url),
      attempt.state,
      attempt.verifier,
    );
    const { token: sessionToken, maxAge } = await createSession(
      identity.discordId,
      identity.username,
    );
    await revokeSession(request.cookies.get(SESSION_COOKIE)?.value);
    const response = NextResponse.redirect(new URL(destination, siteOrigin()));
    response.cookies.set(SESSION_COOKIE, sessionToken, cookieOptions(maxAge));
    response.cookies.set(OAUTH_COOKIE, "", cookieOptions(0));
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch {
    const url = new URL(destination, siteOrigin());
    url.searchParams.delete("code");
    url.searchParams.set("error", "sign-in-failed");
    const response = NextResponse.redirect(url);
    response.cookies.set(OAUTH_COOKIE, "", cookieOptions(0));
    response.headers.set("Cache-Control", "no-store");
    return response;
  }
}
