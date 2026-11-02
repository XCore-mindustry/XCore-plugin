import Link from "next/link";
import type { ReactNode } from "react";
import type { Locale } from "@/i18n/config";
import { getDictionary } from "@/i18n";
import { communityCopy, type CommunityCopy } from "@/i18n/community";
import { accent, Cmd, Glyph, Sprite } from "./kit";
import { glyph, leagues, type Accent } from "@/content";

/** A page's window: head in the topic accent, the 3px accent line, then a body of cards. */
export function CommunityWindow({
  title,
  command,
  children,
  description,
  tone = "gold",
  aside,
}: {
  title: ReactNode;
  command?: string;
  children: ReactNode;
  description?: ReactNode;
  tone?: Accent;
  aside?: ReactNode;
}) {
  return (
    <section className="cx-window window" style={accent(tone)}>
      <header className="cx-head">
        <div className="cx-head-text">
          <h1>{title}</h1>
          {description ? <p className="cx-lede">{description}</p> : null}
        </div>
        {aside ?? (command ? <Cmd>{command}</Cmd> : null)}
      </header>
      <div className="line" />
      <div className="cx-body">{children}</div>
    </section>
  );
}

export function Notice({
  children,
  error = false,
  success = false,
}: {
  children: ReactNode;
  error?: boolean;
  success?: boolean;
}) {
  const tone = error ? "error" : success ? "success" : "info";
  return (
    <div className="cx-notice" data-tone={tone} role={error ? "alert" : "status"}>
      <Glyph c={error ? glyph.warning : success ? glyph.ok : glyph.inbox} />
      <div>{children}</div>
    </div>
  );
}

/** Something that is not there yet, drawn as the kit's dashed ghost cell rather than left blank. */
export function Ghost({ children }: { children: ReactNode }) {
  return <div className="cx-ghost">{children}</div>;
}

export function SignIn({
  lang,
  returnTo,
}: {
  lang: Locale;
  returnTo?: string;
}) {
  const query = new URLSearchParams({
    lang,
    returnTo: returnTo ?? `/${lang}/me`,
  });
  return (
    <a className="btn btn-discord" href={`/api/auth/discord?${query}`}>
      <Glyph c={glyph.discord} />
      {communityCopy(lang).signIn}
    </a>
  );
}

export function Pagination({
  lang,
  page,
  pages,
  pathname,
  query = {},
}: {
  lang: Locale;
  page: number;
  pages: number;
  pathname: string;
  query?: Record<string, string | undefined>;
}) {
  const t = communityCopy(lang);
  if (pages <= 1 && page <= 1) return null;
  const url = (value: number) => {
    const params = new URLSearchParams();
    for (const [key, item] of Object.entries(query))
      if (item) params.set(key, item);
    params.set("page", String(value));
    return `${pathname}?${params}`;
  };
  return (
    <nav className="cx-pages" aria-label={t.page}>
      {page > 1 ? (
        <Link className="btn" href={url(page - 1)} rel="prev">
          <Glyph c={glyph.right} className="cx-flip" />
          {t.previous}
        </Link>
      ) : (
        <span />
      )}
      <span className="cx-pages-at">
        {t.page} {page} {t.pageOf} {Math.max(pages, page)}
      </span>
      {page < pages ? (
        <Link className="btn" href={url(page + 1)} rel="next">
          {t.next}
          <Glyph c={glyph.right} />
        </Link>
      ) : (
        <span />
      )}
    </nav>
  );
}

export function DataUnavailable({ lang }: { lang: Locale }) {
  return <Notice error>{communityCopy(lang).unavailable}</Notice>;
}

/** The window's outline while its data loads, so the page does not jump when it arrives. */
export function WindowLoading({ label }: { label: string }) {
  return (
    <section className="cx-window window cx-loading" role="status" aria-busy="true">
      <header className="cx-head">
        <span className="cx-loading-bar" />
      </header>
      <div className="line" />
      <div className="cx-body">
        <span className="sr-only">{label}</span>
        <span className="cx-loading-bar" />
        <span className="cx-loading-bar" />
        <span className="cx-loading-bar" />
      </div>
    </section>
  );
}

/** A ladder's name and topic colour, as the landing's mode board paints them. */
export function ladderOf(
  ladder: string | null,
  gameMode: string | null,
  t: CommunityCopy,
): { label: string; tone: Accent } {
  if (ladder === "hexed") return { label: t.hexed, tone: "purple" };
  if (ladder === "pvp" || ladder === "minipvp") return { label: t.pvp, tone: "red" };
  return { label: gameMode ?? t.unknown, tone: "gray" };
}

/** The league a rating has reached (rating/RatingLeague.java), as its item sprite and name. */
export function LeagueMark({
  rating,
  lang,
  named = false,
}: {
  rating: number;
  lang: Locale;
  named?: boolean;
}) {
  const league = leagues[leagues.findLastIndex((l) => rating >= l.from)];
  if (!league) return named ? <span className="cx-league muted">{communityCopy(lang).noLeague}</span> : null;
  const name = getDictionary(lang).leagues[league.id];
  return (
    <span className="cx-league" title={name}>
      <Sprite src={`/sprites/items/${league.id}.png`} size={32} />
      {named ? <span>{name}</span> : <span className="sr-only">{name}</span>}
    </span>
  );
}

export function Avatar({ scale = 1 }: { scale?: 1 | 2 }) {
  return (
    <span className="cx-avatar" data-scale={scale}>
      <Sprite src="/sprites/alpha.png" size={48} scale={scale} />
    </span>
  );
}

export function OnlineMark({ label }: { label: string }) {
  return (
    <span className="cx-online">
      <span aria-hidden="true" className="cx-dot" />
      {label}
    </span>
  );
}

export const hours = (minutes: number, lang: Locale, t: CommunityCopy) =>
  `${Math.floor(minutes / 60).toLocaleString(lang)} ${t.hours}`;
