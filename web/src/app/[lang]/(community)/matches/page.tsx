import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { hasLocale, type Locale } from "@/i18n/config";
import { communityCopy } from "@/i18n/community";
import {
  CommunityWindow,
  DataUnavailable,
  Pagination,
  WindowLoading,
} from "@/components/community";
import { Glyph } from "@/components/kit";
import { MatchList } from "@/components/match-list";
import { glyph } from "@/content";
import { listMatches } from "@/lib/server/stats";
import { pageNumber } from "@/lib/server/security";

export const metadata = { title: "XCore — Matches" };

async function Matches({
  lang,
  searchParams,
}: {
  lang: Locale;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const t = communityCopy(lang),
    query = await searchParams;
  const pid =
    typeof query.pid === "string" &&
    /^\d{1,10}$/.test(query.pid) &&
    Number(query.pid) > 0
      ? Number(query.pid)
      : undefined;
  const ladder =
    query.ladder === "pvp" || query.ladder === "minipvp" || query.ladder === "hexed"
      ? query.ladder === "pvp" ? "minipvp" : query.ladder
      : undefined;
  const result = await listMatches({
    pid,
    ladder,
    page: pageNumber(query.page),
  }).catch(() => null);
  const content = result ? (
    <>
      <MatchList
        lang={lang}
        matches={result.items}
        personal={pid !== undefined}
      />
      <Pagination
        lang={lang}
        page={result.page}
        pages={result.totalPages}
        pathname={`/${lang}/matches`}
        query={{ pid: pid ? String(pid) : undefined, ladder }}
      />
    </>
  ) : (
    <DataUnavailable lang={lang} />
  );
  return (
    <CommunityWindow
      title={pid ? `${t.playerMatches} · PID ${pid}` : t.allMatches}
      command="/matches"
      description={t.dataHint}
      tone="orange"
    >
      <form className="cx-filters" method="get">
        <div className="cx-field">
          <label htmlFor="matches-ladder">{t.mode}</label>
          <select id="matches-ladder" name="ladder" defaultValue={ladder ?? ""}>
            <option value="">{t.allModes}</option>
            <option value="minipvp">{t.pvp}</option>
            <option value="hexed">{t.hexed}</option>
          </select>
        </div>
        <div className="cx-field">
          <label htmlFor="matches-pid">{t.pid}</label>
          <input
            id="matches-pid"
            type="number"
            inputMode="numeric"
            name="pid"
            min={1}
            max={2147483647}
            defaultValue={pid}
          />
        </div>
        <button className="btn" type="submit">
          {t.filter}
        </button>
        {pid ? (
          <Link className="text-link" href={`/${lang}/players/${pid}`}>
            {t.openPlayer}
            <Glyph c={glyph.right} />
          </Link>
        ) : null}
      </form>
      {content}
    </CommunityWindow>
  );
}

export default async function Page({
  params,
  searchParams,
}: PageProps<"/[lang]/matches">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  return (
    <Suspense fallback={<WindowLoading label={communityCopy(lang).loading} />}>
      <Matches lang={lang} searchParams={searchParams} />
    </Suspense>
  );
}
