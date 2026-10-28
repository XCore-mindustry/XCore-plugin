import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { hasLocale, type Locale } from "@/i18n/config";
import { communityCopy, errorCopy, type CommunityCopy } from "@/i18n/community";
import {
  Avatar,
  CommunityWindow,
  DataUnavailable,
  Notice,
  SignIn,
  WindowLoading,
} from "@/components/community";
import { Glyph } from "@/components/kit";
import { glyph } from "@/content";
import { getSession } from "@/lib/server/session";
import { oauthConfigured } from "@/lib/server/oauth";
import {
  previewLink,
  previewUnlink,
  type IntentView,
} from "@/lib/server/accounts";
import { publicError } from "@/lib/server/security";

export const metadata = {
  title: "XCore — Link profile",
  robots: { index: false, follow: false },
  referrer: "same-origin" as const,
};

/** Where the visitor is in the link flow, drawn as the kit's cells: lit when done, current, ghost ahead. */
function Progress({ t, at, who }: { t: CommunityCopy; at: 0 | 1 | 2; who?: string }) {
  const steps = [
    [t.stepDiscord, who],
    [t.stepCode, "/discord link"],
    [t.stepDone, undefined],
  ] as const;
  return (
    <ol className="cx-progress">
      {steps.map(([name, note], index) => (
        <li
          key={name}
          data-state={index < at ? "done" : index === at ? "now" : "ahead"}
          aria-current={index === at ? "step" : undefined}
        >
          <span className="cx-progress-n" aria-hidden="true">
            {index < at ? <Glyph c={glyph.ok} /> : index + 1}
          </span>
          <span className="cx-progress-name">{name}</span>
          {note ? <span className="cx-progress-note">{note}</span> : null}
        </li>
      ))}
    </ol>
  );
}

async function LinkProfile({
  lang,
  searchParams,
}: {
  lang: Locale;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const t = communityCopy(lang),
    query = await searchParams;
  const code = typeof query.code === "string" ? query.code.slice(0, 64) : "";
  const unlink = typeof query.unlink === "string" ? query.unlink : "";
  const session = await getSession().catch(() => undefined);
  if (session === undefined)
    return (
      <CommunityWindow title={t.link}>
        <DataUnavailable lang={lang} />
      </CommunityWindow>
    );
  if (!session) {
    const returnQuery = new URLSearchParams();
    if (code) returnQuery.set("code", code);
    if (unlink) returnQuery.set("unlink", unlink);
    return (
      <CommunityWindow title={t.link} description={t.signInFirst} command="/discord link">
        <Progress t={t} at={0} />
        <div className="cx-actions">
          {oauthConfigured() ? (
            <SignIn lang={lang} returnTo={`/${lang}/link?${returnQuery}`} />
          ) : (
            <Notice>{t.loginUnavailable}</Notice>
          )}
        </div>
      </CommunityWindow>
    );
  }
  let intent: IntentView | undefined, issue: string | undefined;
  try {
    if (unlink) intent = await previewUnlink(session, Number(unlink));
    else if (code) intent = await previewLink(session, code);
  } catch (error) {
    issue = publicError(error).code;
  }
  const unlinking = intent?.action === "unlink";
  return (
    <CommunityWindow
      title={unlinking ? t.confirmUnlink : t.link}
      tone={unlinking ? "red" : "gold"}
      command="/discord link"
    >
      {unlinking || (unlink && !intent) ? null : (
        <Progress t={t} at={intent ? 2 : 1} who={session.username} />
      )}
      {issue ? <Notice error>{errorCopy(issue, lang)}</Notice> : null}
      {intent ? (
        <div className="card cx-confirm">
          <p className="cx-confirm-q">{intent.action === "link" ? t.linkTo : t.unlinkFrom}</p>
          <div className="cx-confirm-who">
            <Avatar scale={2} />
            <div>
              <p className="cx-name cx-name-lg">{intent.nickname}</p>
              <p className="cx-code">PID {intent.pid}</p>
            </div>
          </div>
          <p>{intent.action === "link" ? t.confirmHint : t.unlinkHint}</p>
          <form className="cx-actions" action="/api/accounts/confirm" method="post">
            <input type="hidden" name="csrf" value={session.csrf} />
            <input type="hidden" name="operationId" value={intent._id} />
            <input type="hidden" name="lang" value={lang} />
            <button className={unlinking ? "btn cx-btn-danger" : "btn-primary"} type="submit">
              <Glyph c={unlinking ? glyph.undo : glyph.link} />
              {unlinking ? t.confirmUnlink : t.confirmLink}
            </button>
            <Link className="text-link" href={`/${lang}/me`}>
              {t.cancel}
            </Link>
          </form>
        </div>
      ) : (
        <div className="card cx-confirm">
          <p>{t.linkHint}</p>
          <form className="cx-form" method="get" action={`/${lang}/link`}>
            <div className="cx-field">
              <label htmlFor="link-code">{t.code}</label>
              <input
                id="link-code"
                className="cx-code-input"
                name="code"
                minLength={6}
                maxLength={6}
                pattern="[A-Za-z2-9]{6}"
                autoComplete="one-time-code"
                autoCapitalize="characters"
                spellCheck={false}
                required
                defaultValue={code}
              />
            </div>
            <button className="btn-primary" type="submit">
              {t.preview}
              <Glyph c={glyph.right} />
            </button>
          </form>
        </div>
      )}
      <Link className="text-link" href={`/${lang}/me`}>
        <Glyph c={glyph.right} className="cx-flip" />
        {t.me}
      </Link>
    </CommunityWindow>
  );
}

export default async function Page({
  params,
  searchParams,
}: PageProps<"/[lang]/link">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  return (
    <Suspense fallback={<WindowLoading label={communityCopy(lang).loading} />}>
      <LinkProfile lang={lang} searchParams={searchParams} />
    </Suspense>
  );
}
