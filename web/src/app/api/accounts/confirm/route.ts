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
  let lang = localeOf(null), operationId = "";
  let issue: ReturnType<typeof publicError> | undefined;
  try {
    assertSameOrigin(request);
    const form = await request.formData();
    lang = localeOf(String(form.get("lang")));
    operationId = String(form.get("operationId") ?? "");
    const session = await requireSession();
    assertCsrf(form.get("csrf"), session.csrf);
    await confirmIntent(session, operationId);
  } catch (error) {
    issue = publicError(error);
  }

  let origin: URL;
  try {
    origin = siteOrigin();
  } catch (error) {
    const { status, code } = issue ?? publicError(error);
    return NextResponse.json(
      { error: code },
      { status, headers: { "Cache-Control": "no-store" } },
    );
  }
  const target = new URL(`/${lang}/me`, origin);
  if (/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(operationId))
    target.searchParams.set("operation", operationId);
  if (issue) target.searchParams.set("error", issue.code);
  return NextResponse.redirect(target, {
    status: 303,
    headers: { "Cache-Control": "no-store" },
  });
}
