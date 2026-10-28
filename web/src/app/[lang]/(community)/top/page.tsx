import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { hasLocale, type Locale } from "@/i18n/config";
import { communityCopy } from "@/i18n/community";
import {
  CommunityWindow,
  DataUnavailable,
  Ghost,
  hours,
  LeagueMark,
  Pagination,
  WindowLoading,
} from "@/components/community";
import { accent, Glyph } from "@/components/kit";
import { glyph, type Accent } from "@/content";
import { getLeaderboard } from "@/lib/server/stats";
import { pageNumber } from "@/lib/server/security";
import type { LeaderboardCategory } from "@/lib/server/stats-types";

export const metadata = { title: "XCore — Top players" };

/** Each board in the colour of its mode; legacy Hexed points stay gray so they never pass for HexedCore Elo. */
const boards: { id: LeaderboardCategory; tone: Accent; glyph: string }[] = [
  { id: "pvp", tone: "red", glyph: glyph.modePvp },
  { id: "hexed-elo", tone: "purple", glyph: glyph.admin },
  { id: "hexed", tone: "gray", glyph: glyph.map },
  { id: "playtime", tone: "gold", glyph: glyph.play },
];

async function Top({
  lang,
  searchParams,
}: {
  lang: Locale;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const t = communityCopy(lang),
    query = await searchParams;
  const category: LeaderboardCategory =
    query.category === "hexed" || query.category === "hexed-elo"
      ? query.category
      : query.category === "playtime"
        ? "playtime"
        : "pvp";
  const seasonal = category === "pvp" || category === "hexed-elo";
  const season =
    typeof query.season === "string" &&
    /^\d{1,6}$/.test(query.season) &&
    Number(query.season) > 0
      ? Number(query.season)
      : undefined;
  const result = await getLeaderboard(
    category,
    pageNumber(query.page),
    season,
  ).catch(() => null);
  const board = boards.find((b) => b.id === category)!;
  const label = {
    pvp: [t.pvp, t.catPvp, t.aboutPvp],
    "hexed-elo": [t.hexed, t.catHexedElo, t.aboutHexedElo],
    hexed: [t.hexedPoints, t.catHexedPoints, t.aboutHexedPoints],
    playtime: [t.playtime, t.catPlaytime, t.aboutPlaytime],
  } satisfies Record<LeaderboardCategory, string[]>;
  const valueName =
    category === "playtime" ? t.playtime : category === "hexed" ? t.points : t.rating;
  const content = result ? (
    <>
      {result.items.length ? (
        <div className="cx-table-wrap">
          <table className="cx-table cx-top">
            <caption className="sr-only">
              {label[category][0]}
              {result.season ? ` · ${t.season} ${result.season}` : ""}
            </caption>
            <thead>
              <tr>
                <th scope="col" className="cx-rank-col">
                  {t.rank}
                </th>
                <th scope="col">{t.nickname}</th>
                <th scope="col" className="number">
                  {valueName}
                </th>
                {seasonal ? (
                  <th scope="col" className="number cx-hide-sm">
                    {t.matches}
                  </th>
                ) : null}
              </tr>
            </thead>
            <tbody>
              {result.items.map((entry, index) => (
                <tr key={`${entry.pid ?? "unknown"}-${index}`} data-podium={entry.rank <= 3 || undefined}>
                  <td className="cx-rank">{entry.rank}</td>
                  <td className="cx-top-name">
                    {entry.pid ? (
                      <Link href={`/${lang}/players/${entry.pid}`}>{entry.nickname}</Link>
                    ) : (
                      entry.nickname
                    )}
                    {seasonal ? (
                      <span className="cx-show-sm muted">
                        {t.matches}: {entry.matches ?? 0}
                      </span>
                    ) : null}
                  </td>
                  <td className="number cx-value">
                    {seasonal ? <LeagueMark rating={entry.value} lang={lang} /> : null}
                    {category === "playtime"
                      ? hours(entry.value, lang, t)
                      : entry.value.toLocaleString(lang)}
                  </td>
                  {seasonal ? (
                    <td className="number cx-hide-sm">{entry.matches ?? 0}</td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <Ghost>{t.emptyTop}</Ghost>
      )}
      <Pagination
        lang={lang}
        page={result.page}
        pages={result.totalPages}
        pathname={`/${lang}/top`}
        query={{ category, season: season ? String(season) : undefined }}
      />
    </>
  ) : (
    <DataUnavailable lang={lang} />
  );
  return (
    <CommunityWindow title={t.top} command="/top" tone={board.tone}>
      <nav className="cx-boards" aria-label={t.top}>
        {boards.map((item) => (
          <Link
            key={item.id}
            href={`/${lang}/top?category=${item.id}`}
            aria-current={item.id === category ? "page" : undefined}
            style={accent(item.tone)}
          >
            <Glyph c={item.glyph} />
            <span className="cx-board-name">{label[item.id][0]}</span>
            <span className="cx-board-kind">{label[item.id][1]}</span>
          </Link>
        ))}
      </nav>
      <div className="cx-board-head">
        <p className="cx-board-about">
          {result?.season ? (
            <strong>
              {t.season} {result.season}.{" "}
            </strong>
          ) : null}
          {label[category][2]}
        </p>
        {seasonal ? (
          <form className="cx-season" method="get">
            <input type="hidden" name="category" value={category} />
            <label htmlFor="top-season">{t.season}</label>
            <input
              id="top-season"
              name="season"
              type="number"
              inputMode="numeric"
              min={1}
              max={999999}
              defaultValue={season}
              placeholder={result?.season ? String(result.season) : undefined}
            />
            <button className="btn" type="submit">
              {t.show}
            </button>
          </form>
        ) : null}
      </div>
      {content}
    </CommunityWindow>
  );
}

export default async function Page({
  params,
  searchParams,
}: PageProps<"/[lang]/top">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  return (
    <Suspense fallback={<WindowLoading label={communityCopy(lang).loading} />}>
      <Top lang={lang} searchParams={searchParams} />
    </Suspense>
  );
}
