import { NextRequest, NextResponse } from "next/server";
import { confirmIntent } from "@/lib/server/accounts";
import { requireSession } from "@/lib/server/session";
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
    const operationId = String(form.get("operationId") ?? "");
    await confirmIntent(session, operationId);
    const target = new URL(
      `/${localeOf(String(form.get("lang")))}/me`,
      siteOrigin(),
    );
    target.searchParams.set("operation", operationId);
    return NextResponse.redirect(target, 303);
  } catch (error) {
    const { status, code } = publicError(error);
    return NextResponse.json(
      { error: code },
      { status, headers: { "Cache-Control": "no-store" } },
    );
  }
}
