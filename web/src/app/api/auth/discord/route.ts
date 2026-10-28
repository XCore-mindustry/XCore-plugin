import { NextRequest, NextResponse } from "next/server";
import * as oauth from "oauth4webapi";
import { authorizationUrl } from "@/lib/server/oauth";
import {
  OAUTH_COOKIE,
  cookieOptions,
  rateLimit,
  sessionDb,
  type OAuthAttempt,
} from "@/lib/server/session";
import {
  localeOf,
  publicError,
  randomToken,
  safeReturnTo,
  siteOrigin,
  tokenHash,
} from "@/lib/server/security";

export async function GET(request: NextRequest) {
  const lang = localeOf(request.nextUrl.searchParams.get("lang"));
  try {
    const ipHeader = process.env.TRUSTED_PROXY_IP_HEADER?.trim();
    const ip = ipHeader ? request.headers.get(ipHeader)?.trim() : undefined;
    if (ip) await rateLimit("oauth-start", ip, 100);
    const token = randomToken(),
      state = oauth.generateRandomState(),
      verifier = oauth.generateRandomCodeVerifier();
    const returnTo = safeReturnTo(
      request.nextUrl.searchParams.get("returnTo"),
      lang,
    );
    const url = await authorizationUrl(state, verifier);
    await (
      await sessionDb()
    )
      .collection<OAuthAttempt>("web_oauth")
      .insertOne({
        _id: tokenHash(token),
        state,
        verifier,
        returnTo,
        expiresAt: new Date(Date.now() + 600_000),
      });
    const response = NextResponse.redirect(url);
    response.cookies.set(OAUTH_COOKIE, token, cookieOptions(600));
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    const issue = publicError(error);
    try {
      const destination = new URL(`/${lang}/me`, siteOrigin());
      destination.searchParams.set("error", issue.code);
      return NextResponse.redirect(destination);
    } catch {
      return NextResponse.json({ error: issue.code }, { status: issue.status });
    }
  }
}
