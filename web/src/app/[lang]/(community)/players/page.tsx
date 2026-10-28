import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { hasLocale, type Locale } from "@/i18n/config";
import { communityCopy } from "@/i18n/community";
import {
  Avatar,
  CommunityWindow,
  DataUnavailable,
  Ghost,
  hours,
  OnlineMark,
  Pagination,
  WindowLoading,
} from "@/components/community";
import { Glyph } from "@/components/kit";
import { glyph } from "@/content";
import { searchPlayers } from "@/lib/server/stats";
import { pageNumber } from "@/lib/server/security";
import { GameText } from "@/components/game-text";

export const metadata = { title: "XCore — Players" };

async function Players({
  lang,
  searchParams,
}: {
  lang: Locale;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const t = communityCopy(lang),
    query = await searchParams,
    q = typeof query.q === "string" ? query.q.slice(0, 64) : "";
  const result = q
    ? await searchPlayers(q, pageNumber(query.page)).catch(() => undefined)
    : null;
  const content =
    result === undefined ? (
      <DataUnavailable lang={lang} />
    ) : result ? (
      result.items.length ? (
        <>
          <ol className="cx-rows">
            {result.items.map((player) => (
              <li key={player.pid}>
                <Link className="cx-row cx-player" href={`/${lang}/players/${player.pid}`}>
                  <Avatar />
                  <span className="cx-row-main">
                    <span className="cx-row-title"><GameText text={player.nickname} /></span>
                    <span className="cx-row-meta">
                      <span className="cx-code">PID {player.pid}</span>
                      {player.username ? ` · @${player.username}` : ""}
                      {player.online ? (
                        <>
                          {" · "}
                          <OnlineMark label={t.online} />
                        </>
                      ) : null}
                    </span>
                  </span>
                  <span className="cx-row-stat">
                    <span className="cx-row-label">{t.playtime}</span>
                    <span className="cx-num">{hours(player.totalPlayTime, lang, t)}</span>
                  </span>
                  <Glyph c={glyph.right} className="cx-row-go" />
                </Link>
              </li>
            ))}
          </ol>
          <Pagination
            lang={lang}
            page={result.page}
            pages={result.totalPages}
            pathname={`/${lang}/players`}
            query={{ q }}
          />
        </>
      ) : (
        <Ghost>{t.emptyPlayers}</Ghost>
      )
    ) : (
      <p className="muted cx-browse">
        {t.browse}{" "}
        <Link className="text-link" href={`/${lang}/top`}>
          {t.top}
        </Link>{" "}
        ·{" "}
        <Link className="text-link" href={`/${lang}/matches`}>
          {t.allMatches}
        </Link>
      </p>
    );
  return (
    <CommunityWindow title={t.players} description={t.searchIntro} tone="blue" command="/stats">
      <form method="get" className="cx-search" role="search">
        <label htmlFor="player-search" className="sr-only">
          {t.search}
        </label>
        <input
          id="player-search"
          name="q"
          type="search"
          placeholder={t.searchHint}
          defaultValue={q}
          maxLength={64}
          required
          autoComplete="off"
          spellCheck={false}
        />
        <button className="btn-primary" type="submit">
          {t.find}
        </button>
      </form>
      {content}
    </CommunityWindow>
  );
}

export default async function Page({
  params,
  searchParams,
}: PageProps<"/[lang]/players">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  return (
    <Suspense fallback={<WindowLoading label={communityCopy(lang).loading} />}>
      <Players lang={lang} searchParams={searchParams} />
    </Suspense>
  );
}
