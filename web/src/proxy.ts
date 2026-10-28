import { NextResponse, type NextRequest } from "next/server";
import { hasLocale, pickLocale } from "@/i18n/config";

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (hasLocale(pathname.split("/")[1] ?? "")) return;

  request.nextUrl.pathname = `/${pickLocale(request.headers.get("accept-language"))}${pathname}`;
  return NextResponse.redirect(request.nextUrl);
}

export const config = {
  matcher: ["/((?!api(?:/|$)|discord/link(?:/|$)|_next/|.*\\.[a-z0-9]+$).*)"],
};
