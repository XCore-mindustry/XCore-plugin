import Link from "next/link";
import type { Locale } from "@/i18n/config";
import { communityCopy } from "@/i18n/community";
import { formatDate, formatDuration } from "@/lib/format";
import type { PublicMatchSummary } from "@/lib/server/stats-types";
import { accent, Glyph } from "./kit";
import { glyph } from "@/content";
import { Ghost, ladderOf } from "./community";

/** Matches as log rows: the whole row opens the match, its left edge carries the mode's colour. */
export function MatchList({
  lang,
  matches,
  personal = false,
}: {
  lang: Locale;
  matches: PublicMatchSummary[];
  personal?: boolean;
}) {
  const t = communityCopy(lang);
  if (!matches.length) return <Ghost>{t.emptyMatches}</Ghost>;
  return (
    <ol className="cx-rows">
      {matches.map((match) => {
        const mode = ladderOf(match.ladder, match.gameMode, t);
        const outcome = match.playerOutcome;
        return (
          <li key={`${match.ladder ?? "game"}:${match.matchId}`} style={accent(mode.tone)}>
            <Link
              className="cx-row cx-match"
              href={`/${lang}/matches/${encodeURIComponent(match.matchId)}${match.ladder ? `?ladder=${encodeURIComponent(match.ladder)}` : ""}`}
            >
              <span className="cx-row-main">
                <span className="cx-row-title">
                  {match.map ?? `${t.match} ${match.matchId.slice(0, 8)}`}
                </span>
                <span className="cx-row-meta">
                  <span className="cx-mode">{mode.label}</span>
                  {match.season ? ` · ${t.season} ${match.season}` : ""} ·{" "}
                  {formatDate(match.endedAt ?? match.startedAt, lang)}
                </span>
              </span>
              <span className="cx-row-stat">
                <span className="cx-row-label">{t.duration}</span>
                <span className="cx-num">{formatDuration(match.durationSeconds)}</span>
              </span>
              {personal ? (
                <span className="cx-row-stat">
                  <span className="cx-row-label">{t.result}</span>
                  {outcome ? (
                    <span className="cx-num" data-result={outcome.win ? "win" : "loss"}>
                      {outcome.win ? t.winner : outcome.placement ? `#${outcome.placement}` : t.loss}
                      {outcome.ratingDelta !== null ? (
                        <span className="cx-delta" data-sign={Math.sign(outcome.ratingDelta)}>
                          {` ${outcome.ratingDelta > 0 ? "+" : ""}${outcome.ratingDelta}`}
                        </span>
                      ) : null}
                    </span>
                  ) : (
                    <span className="cx-num">—</span>
                  )}
                </span>
              ) : (
                <span className="cx-row-stat">
                  <span className="cx-row-label">{t.participants}</span>
                  <span className="cx-num">{match.playerCount}</span>
                </span>
              )}
              <Glyph c={glyph.right} className="cx-row-go" />
            </Link>
          </li>
        );
      })}
    </ol>
  );
}
