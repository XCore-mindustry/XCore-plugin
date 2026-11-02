import { Suspense, type CSSProperties } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { hasLocale, type Locale } from "@/i18n/config";
import { getDictionary } from "@/i18n";
import { communityCopy } from "@/i18n/community";
import {
  Avatar,
  CommunityWindow,
  DataUnavailable,
  Ghost,
  hours,
  ladderOf,
  LeagueMark,
  OnlineMark,
  WindowLoading,
} from "@/components/community";
import { accent, Band, BadgeMark, Cmd, Glyph } from "@/components/kit";
import { MatchList } from "@/components/match-list";
import { badges, glyph } from "@/content";
import { getPlayerProfile, listMatches } from "@/lib/server/stats";
import type {
  PlayerProfile,
  PublicMatchSummary,
} from "@/lib/server/stats-types";
import { GameText } from "@/components/game-text";
import { parsePid } from "@/lib/pid";

export const metadata = { title: "XCore — Player profile" };

async function Player({ lang, pid }: { lang: Locale; pid: number }) {
  const t = communityCopy(lang),
    names = getDictionary(lang);
  let profile: PlayerProfile | null, matches: PublicMatchSummary[];
  try {
    [profile, matches] = await Promise.all([
      getPlayerProfile(pid),
      listMatches({ pid, pageSize: 10 }).then((page) => page.items),
    ]);
  } catch {
    return (
      <CommunityWindow title={t.profile}>
        <DataUnavailable lang={lang} />
      </CommunityWindow>
    );
  }
  if (!profile)
    return (
      <CommunityWindow title={t.profile} tone="blue">
        <Ghost>{t.noPlayer}</Ghost>
        <Link className="text-link" href={`/${lang}/players`}>
          {t.search}
        </Link>
      </CommunityWindow>
    );
  const { overall: overview, pvp, survival, hexed } = profile.overview;
  const count = (n: number) => n.toLocaleString(lang);
  // Wins sit next to the games they came from, so the rate can be checked at a glance.
  const facts: [string, string, string?][] = [
    [t.games, count(overview.gamesPlayed)],
    [
      t.wins,
      count(overview.gamesWon),
      overview.gamesPlayed ? `${t.ofPlayers} ${count(overview.gamesPlayed)}` : undefined,
    ],
    [t.winRate, percent(overview.gamesWon, overview.gamesPlayed)],
    [t.built, count(overview.blocksBuilt)],
    [t.destroyed, count(overview.blocksDestroyed)],
    [t.units, count(overview.unitsDestroyed)],
  ];
  // The all-modes rate mixes PvP with survival and Hexed, where a win is much rarer; per mode it reads plainly.
  // Event games have no bucket of their own: they are what the three modes leave of the total.
  const otherGames = Math.max(0, overview.gamesPlayed - pvp.gamesPlayed - survival.gamesPlayed - hexed.gamesPlayed);
  const otherWins = Math.max(0, overview.gamesWon - pvp.gamesWon - survival.gamesWon - hexed.gamesWon);
  const modes = [
    { label: t.modePvp, games: pvp.gamesPlayed, wins: pvp.gamesWon, win: t.winPvp },
    {
      label: t.modeSurvival,
      games: survival.gamesPlayed,
      wins: survival.gamesWon,
      win: survival.bestWave ? `${t.winSurvival} · ${t.bestWave} ${count(survival.bestWave)}` : t.winSurvival,
    },
    { label: t.modeHexed, games: hexed.gamesPlayed, wins: hexed.gamesWon, win: t.winHexed },
    { label: t.modeOther, games: otherGames, wins: otherWins, win: t.winOther },
  ].filter((mode) => mode.games > 0);
  const since =
    profile.createdAt && !Number.isNaN(Date.parse(profile.createdAt))
      ? new Intl.DateTimeFormat(lang, { dateStyle: "medium", timeZone: "UTC" }).format(
          new Date(profile.createdAt),
        )
      : null;
  const other = profile.unlockedBadges.filter((id) => !badges.some((b) => b.id === id));
  return (
    <CommunityWindow
      title={<GameText text={profile.nickname} />}
      description={
        <span className="cx-meta">
          <span className="cx-code">PID {profile.pid}</span>
          {profile.username ? <span className="cx-code">@{profile.username}</span> : null}
          {profile.online ? (
            <OnlineMark
              label={profile.onlineServer ? `${t.onlineOn} ${profile.onlineServer}` : t.online}
            />
          ) : null}
        </span>
      }
      command="/stats"
    >
      <div className="cx-pass">
        <div className="cx-col">
          <div className="card cx-id">
            <div className="cx-id-who">
              <Avatar scale={2} />
              {profile.description ? <p className="cx-bio">{profile.description}</p> : null}
            </div>
            <dl className="fields">
              <div>
                <dt>{t.playtime}</dt>
                <dd>{hours(profile.totalPlayTime, lang, t)}</dd>
                <Cmd>/stats</Cmd>
              </div>
              {since ? (
                <div>
                  <dt>{t.joined}</dt>
                  <dd className="cx-dd-plain">{since}</dd>
                  <span />
                </div>
              ) : null}
            </dl>
          </div>
          <section className="card" style={accent("gray")}>
            <Band as="h2" title={t.overall} />
            <dl className="cx-facts cx-facts-wide">
              {facts.map(([label, value, note]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>
                    {value}
                    {note ? <span className="muted"> {note}</span> : null}
                  </dd>
                </div>
              ))}
            </dl>
            {overview.gamesPlayed ? <p className="cx-facts-note">{t.overallNote}</p> : null}
          </section>
          {modes.length ? (
            <section className="card" style={accent("gray")}>
              <Band as="h2" title={t.byMode} />
              <dl className="cx-facts cx-facts-modes">
                {modes.map((mode) => (
                  <div key={mode.label}>
                    <dt>{mode.label}</dt>
                    <dd>
                      {percent(mode.wins, mode.games)}
                      <span className="muted">
                        {" "}
                        {count(mode.wins)} {t.ofPlayers} {count(mode.games)}
                      </span>
                    </dd>
                    <dd className="cx-fact-note">{mode.win}</dd>
                  </div>
                ))}
              </dl>
              <p className="cx-facts-note">{t.byModeNote}</p>
            </section>
          ) : null}
        </div>
        <div className="cx-col">
          {profile.standings.length ? (
            profile.standings.map((standing) => {
              const mode = ladderOf(standing.ladder, null, t);
              // A ladder that counts top-3 finishes is a free-for-all, where a win is first place out of everyone.
              const placement = standing.top3 !== null;
              return (
                <section
                  key={`${standing.ladder}:${standing.seasonNumber}`}
                  className="card cx-standing"
                  style={accent(mode.tone)}
                >
                  <Band
                    as="h2"
                    title={
                      <>
                        {mode.label}
                        <span className="band-note">
                          {" "}
                          · {t.season} {standing.seasonNumber}
                        </span>
                      </>
                    }
                    cmds={["/season"]}
                  />
                  <div className="cx-standing-body">
                    <p className="cx-rating">
                      <span className="cx-rating-num">{standing.rating.toLocaleString(lang)}</span>
                      <span className="cx-rating-unit">Elo</span>
                    </p>
                    <LeagueMark rating={standing.rating} lang={lang} named />
                    <dl className={placement ? "cx-facts cx-facts-five" : "cx-facts"}>
                      <div>
                        <dt>{t.rank}</dt>
                        <dd>
                          {standing.rank ? `#${standing.rank}` : "—"}
                          {standing.rank && standing.participants ? (
                            <span className="muted">
                              {" "}
                              {t.ofPlayers} {standing.participants}
                            </span>
                          ) : null}
                        </dd>
                      </div>
                      <div>
                        <dt>{t.matches}</dt>
                        <dd>{standing.matches}</dd>
                      </div>
                      <div>
                        <dt>{placement ? t.firsts : t.wins}</dt>
                        <dd>
                          {standing.wins}
                          <span className="muted"> {percent(standing.wins, standing.matches)}</span>
                        </dd>
                      </div>
                      {standing.top3 !== null ? (
                        <div>
                          <dt>{t.top3}</dt>
                          <dd>
                            {standing.top3}
                            <span className="muted"> {percent(standing.top3, standing.matches)}</span>
                          </dd>
                        </div>
                      ) : null}
                      <div>
                        <dt>{t.peak}</dt>
                        <dd>{standing.peakRating.toLocaleString(lang)}</dd>
                      </div>
                    </dl>
                  </div>
                </section>
              );
            })
          ) : (
            <section className="card">
              <Band as="h2" title={t.rating} cmds={["/season"]} />
              <div className="cx-card-body">
                <Ghost>{t.noStandings}</Ghost>
              </div>
            </section>
          )}
          <section className="card" style={accent("gray")}>
            <Band as="h2" title={t.badges} cmds={["/badge"]} />
            <ul className="badge-cells">
              {badges.map((b) => (
                <li
                  key={b.id}
                  className="badge-cell"
                  data-on={profile.unlockedBadges.includes(b.id)}
                  aria-current={profile.activeBadge === b.id ? "true" : undefined}
                  style={{ "--bc": b.color } as CSSProperties}
                >
                  <span className="badge-mark">
                    <BadgeMark badge={b} />
                  </span>
                  <span className="badge-name">{names.badges[b.id]}</span>
                  {profile.unlockedBadges.includes(b.id) ? null : (
                    <span className="sr-only">{names.locked}</span>
                  )}
                </li>
              ))}
              {other.map((id) => (
                <li key={id} className="badge-cell" data-on="true" style={{ "--bc": "var(--muted)" } as CSSProperties}>
                  <span className="badge-mark">
                    <Glyph c={glyph.star} />
                  </span>
                  <span className="badge-name">{id}</span>
                </li>
              ))}
            </ul>
          </section>
        </div>
        <section className="card cx-pass-wide" style={accent("orange")}>
          <Band as="h2" title={t.playerMatches} cmds={["/matches"]} />
          <div className="cx-card-body">
            <MatchList lang={lang} matches={matches} personal />
            {matches.length ? (
              <Link className="text-link" href={`/${lang}/matches?pid=${pid}`}>
                {t.allMatches}
                <Glyph c={glyph.right} />
              </Link>
            ) : null}
          </div>
        </section>
      </div>
    </CommunityWindow>
  );
}

function percent(part: number, whole: number): string {
  return whole ? `${Math.round((part * 100) / whole)}%` : "—";
}

export default async function Page({
  params,
}: PageProps<"/[lang]/players/[pid]">) {
  const { lang, pid } = await params;
  const number = parsePid(pid);
  if (!hasLocale(lang) || number === null) notFound();
  return (
    <Suspense fallback={<WindowLoading label={communityCopy(lang).loading} />}>
      <Player lang={lang} pid={number} />
    </Suspense>
  );
}
