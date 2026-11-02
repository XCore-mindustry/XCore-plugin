import { NextRequest, NextResponse } from "next/server";
import {
  SESSION_COOKIE,
  cookieOptions,
  requireSession,
  revokeSession,
} from "@/lib/server/session";
import {
  assertCsrf,
  assertSameOrigin,
  localeOf,
  publicError,
  siteOrigin,
} from "@/lib/server/security";

export async function POST(request: NextRequest) {
  try {
    assertSameOrigin(request);
    const [session, form] = await Promise.all([
      requireSession(),
      request.formData(),
    ]);
    assertCsrf(form.get("csrf"), session.csrf);
    await revokeSession(request.cookies.get(SESSION_COOKIE)?.value);
    const response = NextResponse.redirect(
      new URL(`/${localeOf(String(form.get("lang")))}/me`, siteOrigin()),
      303,
    );
    response.cookies.set(SESSION_COOKIE, "", cookieOptions(0));
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    const { status, code } = publicError(error);
    return NextResponse.json(
      { error: code },
      { status, headers: { "Cache-Control": "no-store" } },
    );
  }
}
