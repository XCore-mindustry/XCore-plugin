import { NextRequest, NextResponse } from "next/server";
import { pickLocale } from "@/i18n/config";
import { siteOrigin } from "@/lib/server/security";

export function GET(request: NextRequest) {
  const target = new URL(
    `/${pickLocale(request.headers.get("accept-language"))}/link`,
    siteOrigin(),
  );
  const code = request.nextUrl.searchParams.get("code");
  if (code && /^[A-Za-z2-9]{6}$/.test(code))
    target.searchParams.set("code", code.toUpperCase());
  return NextResponse.redirect(target);
}
