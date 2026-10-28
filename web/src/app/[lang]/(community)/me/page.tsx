import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { hasLocale, type Locale } from "@/i18n/config";
import { communityCopy, errorCopy, type CommunityCopy } from "@/i18n/community";
import {
  Avatar,
  CommunityWindow,
  DataUnavailable,
  Ghost,
  hours,
  Notice,
  OnlineMark,
  SignIn,
  WindowLoading,
} from "@/components/community";
import { Band, Cmd, Glyph } from "@/components/kit";
import { glyph } from "@/content";
import { getLinkedProfiles } from "@/lib/server/stats";
import { getSession } from "@/lib/server/session";
import { oauthConfigured } from "@/lib/server/oauth";
import { getIntentResult } from "@/lib/server/accounts";

export const metadata = {
  title: "XCore — My profiles",
  robots: { index: false, follow: false },
};

/** The three real steps of the /discord link flow, so an empty account says what to do next. */
function LinkSteps({ t, signedIn = false }: { t: CommunityCopy; signedIn?: boolean }) {
  return (
    <ol className="cx-steps">
      <li data-done={signedIn || undefined}>
        <span>{t.stepSignIn}</span>
      </li>
      <li>
        <span>
          {t.stepCommand} <Cmd>/discord link</Cmd>
        </span>
      </li>
      <li>
        <span>{t.stepConfirm}</span>
      </li>
    </ol>
  );
}

async function Profiles({
  lang,
  searchParams,
}: {
  lang: Locale;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const t = communityCopy(lang),
    query = await searchParams;
  const session = await getSession().catch(() => undefined);
  if (session === undefined)
    return (
      <CommunityWindow title={t.me}>
        <DataUnavailable lang={lang} />
      </CommunityWindow>
    );
  if (!session)
    return (
      <CommunityWindow title={t.me} description={t.publicHint} command="/discord link">
        {typeof query.error === "string" ? (
          <Notice error>{errorCopy(query.error, lang)}</Notice>
        ) : null}
        <div className="cx-split">
          <div className="card">
            <Band as="h2" title={t.steps} />
            <div className="cx-card-body">
              <LinkSteps t={t} />
            </div>
          </div>
          <div className="card cx-signin">
            {oauthConfigured() ? (
              <SignIn lang={lang} />
            ) : (
              <Notice>{t.loginUnavailable}</Notice>
            )}
            <p className="muted">
              {t.browse}{" "}
              <Link className="text-link" href={`/${lang}/players`}>
                {t.players}
              </Link>
            </p>
          </div>
        </div>
      </CommunityWindow>
    );
  const data = await Promise.all([
    getLinkedProfiles(session.discordId),
    typeof query.operation === "string"
      ? getIntentResult(session, query.operation)
      : Promise.resolve(null),
  ]).catch(() => null);
  if (!data)
    return (
      <CommunityWindow title={t.me}>
        <DataUnavailable lang={lang} />
      </CommunityWindow>
    );
  const [profiles, result] = data;
  return (
    <CommunityWindow
      title={t.me}
      description={
        <>
          <Glyph c={glyph.discord} className="cx-discord" /> {t.signedIn}{" "}
          <strong>{session.username}</strong>
        </>
      }
      aside={
        <form method="post" action="/api/auth/logout">
          <input type="hidden" name="csrf" value={session.csrf} />
          <input type="hidden" name="lang" value={lang} />
          <button className="btn" type="submit">
            <Glyph c={glyph.undo} />
            {t.signOut}
          </button>
        </form>
      }
    >
      {result ? (
        <Notice
          error={result.status === "failed" || result.status === "rejected"}
          success={result.status === "success"}
        >
          {result.status === "success"
            ? result.action === "unlink"
              ? t.unlinked
              : t.linked
            : result.status === "unknown"
              ? t.pending
              : result.reason
                ? errorCopy(result.reason, lang)
                : t.failed}
          {result.status === "unknown" ? (
            <form method="post" action="/api/accounts/confirm" className="cx-actions">
              <input type="hidden" name="csrf" value={session.csrf} />
              <input type="hidden" name="operationId" value={result.operationId} />
              <input type="hidden" name="lang" value={lang} />
              <button className="btn" type="submit">
                {t.checkResult}
              </button>
              <Link className="text-link" href={`/${lang}/me`}>
                {t.refresh}
              </Link>
            </form>
          ) : null}
        </Notice>
      ) : null}
      {typeof query.error === "string" ? (
        <Notice error>{errorCopy(query.error, lang)}</Notice>
      ) : null}
      <div className="card">
        <Band
          as="h2"
          title={
            <>
              {t.profiles}
              <span className="band-note"> · {t.linkedCount}: {profiles.length}</span>
            </>
          }
        />
        <div className="cx-card-body">
          {profiles.length ? (
            <ul className="cx-profiles">
              {profiles.map((profile) => (
                <li className="cx-profile" key={profile.pid}>
                  <Avatar />
                  <div className="cx-profile-id">
                    <h3 className="cx-name">{profile.nickname}</h3>
                    <p className="cx-meta">
                      <span className="cx-code">PID {profile.pid}</span>
                      <span>{hours(profile.totalPlayTime, lang, t)}</span>
                      {profile.online ? <OnlineMark label={t.online} /> : null}
                    </p>
                  </div>
                  <div className="cx-actions">
                    <Link className="btn" href={`/${lang}/players/${profile.pid}`}>
                      <Glyph c={glyph.eye} />
                      {t.openProfile}
                    </Link>
                    <Link
                      className="text-link cx-danger"
                      prefetch={false}
                      href={`/${lang}/link?unlink=${profile.pid}`}
                    >
                      {t.unlink}
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <Ghost>
              <p>{t.noProfiles}</p>
              <LinkSteps t={t} signedIn />
            </Ghost>
          )}
          <div className="cx-actions">
            <Link className="btn-primary" href={`/${lang}/link`}>
              <Glyph c={glyph.link} />
              {profiles.length ? t.linkAnother : t.link}
            </Link>
            {result?.status === "unknown" ? null : (
              <Link className="text-link" href={`/${lang}/me`}>
                {t.refresh}
              </Link>
            )}
          </div>
        </div>
      </div>
    </CommunityWindow>
  );
}

export default async function Page({
  params,
  searchParams,
}: PageProps<"/[lang]/me">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  return (
    <Suspense fallback={<WindowLoading label={communityCopy(lang).loading} />}>
      <Profiles lang={lang} searchParams={searchParams} />
    </Suspense>
  );
}
