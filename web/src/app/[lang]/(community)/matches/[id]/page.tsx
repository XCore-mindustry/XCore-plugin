import { Suspense } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { hasLocale, type Locale } from "@/i18n/config";
import { communityCopy } from "@/i18n/community";
import {
  CommunityWindow,
  DataUnavailable,
  Ghost,
  ladderOf,
  WindowLoading,
} from "@/components/community";
import { Glyph } from "@/components/kit";
import { glyph } from "@/content";
import { getMatchDetail } from "@/lib/server/stats";
import { formatDate, formatDuration } from "@/lib/format";
import { GameText } from "@/components/game-text";

export const metadata = { title: "XCore — Match" };

async function Match({
  lang,
  id,
  searchParams,
}: {
  lang: Locale;
  id: string;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const t = communityCopy(lang),
    query = await searchParams;
  let match;
  try {
    match = await getMatchDetail(
      id,
      query.ladder === "pvp" || query.ladder === "minipvp" || query.ladder === "hexed"
        ? query.ladder
        : undefined,
    );
  } catch {
    return (
      <CommunityWindow title={t.details}>
        <DataUnavailable lang={lang} />
      </CommunityWindow>
    );
  }
  const back = (
    <Link className="text-link" href={`/${lang}/matches`}>
      <Glyph c={glyph.right} className="cx-flip" />
      {t.allMatches}
    </Link>
  );
  if (!match)
    return (
      <CommunityWindow title={t.details} tone="orange">
        <Ghost>{t.noMatch}</Ghost>
        {back}
      </CommunityWindow>
    );
  const mode = ladderOf(match.ladder, match.gameMode, t);
  const facts: [string, string | number][] = [
    [t.mode, mode.label],
    [t.duration, formatDuration(match.durationSeconds)],
    [t.participants, match.playerCount],
  ];
  if (match.season) facts.push([t.season, match.season]);
  if (match.wavesReached !== null) facts.push([t.waves, match.wavesReached]);
  if (match.serverName) facts.push([t.server, match.serverName]);
  const rated = match.participants.some((p) => p.delta !== null);
  return (
    <CommunityWindow
      title={match.map ? <GameText text={match.map} /> : t.details}
      description={
        <span className="cx-meta">
          <span>{formatDate(match.endedAt ?? match.startedAt, lang)}</span>
          <span className="cx-code">
            {t.match} {match.matchId}
          </span>
        </span>
      }
      command="/matches"
      tone={mode.tone}
    >
      <dl className="cx-facts cx-facts-wide">
        {facts.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <div className="cx-table-wrap">
        <table className="cx-table cx-stack">
          <caption className="sr-only">{t.participants}</caption>
          <thead>
            <tr>
              <th scope="col">{t.nickname}</th>
              <th scope="col">{t.result}</th>
              {rated ? (
                <>
                  <th scope="col" className="number">{t.before}</th>
                  <th scope="col" className="number">{t.delta}</th>
                  <th scope="col" className="number">{t.after}</th>
                </>
              ) : null}
              <th scope="col" className="number">{t.built}</th>
            </tr>
          </thead>
          <tbody>
            {match.participants.map((player, index) => (
              <tr key={`${player.pid ?? "unknown"}-${index}`} data-win={player.win || undefined}>
                <th scope="row" className="cx-top-name">
                  {player.pid !== null ? (
                    <Link href={`/${lang}/players/${player.pid}`}><GameText text={player.name} /></Link>
                  ) : (
                    <GameText text={player.name} />
                  )}
                </th>
                <td data-label={t.result}>
                  <span data-result={player.win ? "win" : "loss"}>
                    {player.win
                      ? t.winner
                      : player.placement
                        ? `#${player.placement}`
                        : t.loss}
                  </span>
                </td>
                {rated ? (
                  <>
                    <td className="number" data-label={t.before}>{player.ratingBefore ?? "—"}</td>
                    <td className="number" data-label={t.delta}>
                      <span className="cx-delta" data-sign={player.delta ? Math.sign(player.delta) : 0}>
                        {player.delta !== null
                          ? `${player.delta > 0 ? "+" : ""}${player.delta}`
                          : "—"}
                      </span>
                    </td>
                    <td className="number" data-label={t.after}>{player.ratingAfter ?? "—"}</td>
                  </>
                ) : null}
                <td className="number" data-label={t.built}>{player.blocksBuilt ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {back}
    </CommunityWindow>
  );
}

export default async function Page({
  params,
  searchParams,
}: PageProps<"/[lang]/matches/[id]">) {
  const { lang, id } = await params;
  if (!hasLocale(lang) || id.length > 128 || /[\x00-\x1f]/.test(id)) notFound();
  return (
    <Suspense fallback={<WindowLoading label={communityCopy(lang).loading} />}>
      <Match lang={lang} id={id} searchParams={searchParams} />
    </Suspense>
  );
}
