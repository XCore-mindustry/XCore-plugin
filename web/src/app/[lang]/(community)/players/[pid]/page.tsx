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
  const overview = profile.overview.overall;
  const facts = [
    [t.games, overview.gamesPlayed.toLocaleString(lang)],
    [t.wins, overview.gamesWon.toLocaleString(lang)],
    [
      t.winRate,
      overview.gamesPlayed
        ? `${Math.round((overview.gamesWon * 100) / overview.gamesPlayed)}%`
        : "—",
    ],
    [t.built, overview.blocksBuilt.toLocaleString(lang)],
    [t.destroyed, overview.blocksDestroyed.toLocaleString(lang)],
    [t.units, overview.unitsDestroyed.toLocaleString(lang)],
  ];
  const since =
    profile.createdAt && !Number.isNaN(Date.parse(profile.createdAt))
      ? new Intl.DateTimeFormat(lang, { dateStyle: "medium", timeZone: "UTC" }).format(
          new Date(profile.createdAt),
        )
      : null;
  const other = profile.unlockedBadges.filter((id) => !badges.some((b) => b.id === id));
  return (
    <CommunityWindow
      title={profile.nickname}
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
              {facts.map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
          </section>
        </div>
        <div className="cx-col">
          {profile.standings.length ? (
            profile.standings.map((standing) => {
              const mode = ladderOf(standing.ladder, null, t);
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
                    <dl className="cx-facts">
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
                        <dt>{t.wins}</dt>
                        <dd>{standing.wins}</dd>
                      </div>
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

export default async function Page({
  params,
}: PageProps<"/[lang]/players/[pid]">) {
  const { lang, pid } = await params;
  if (
    !hasLocale(lang) ||
    !/^\d{1,10}$/.test(pid) ||
    Number(pid) < 1 ||
    !Number.isSafeInteger(Number(pid))
  )
    notFound();
  return (
    <Suspense fallback={<WindowLoading label={communityCopy(lang).loading} />}>
      <Player lang={lang} pid={Number(pid)} />
    </Suspense>
  );
}
