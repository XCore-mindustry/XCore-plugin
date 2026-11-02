"use client";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { locales, type Locale } from "@/i18n/config";
import { communityCopy } from "@/i18n/community";
import { glyph } from "@/content";
import { Glyph } from "./kit";

const items = [
  { id: "players", glyph: glyph.players },
  { id: "top", glyph: glyph.star },
  { id: "matches", glyph: glyph.modePvp },
  { id: "me", glyph: glyph.link },
] as const;

export function CommunityNav({ lang }: { lang: Locale }) {
  const path = usePathname();
  const query = useSearchParams().toString();
  return <CommunityNavLinks lang={lang} path={path} query={query} />;
}

export function CommunityNavLinks({ lang, path = "", query = "" }: { lang: Locale; path?: string; query?: string }) {
  const t = communityCopy(lang);
  const short = { players: t.players, top: t.navTop, matches: t.matches, me: t.navMe };
  // Keep link codes and filters when changing language.
  const here = (locale: Locale) => {
    const pathname = path ? path.replace(/^\/[a-z]{2}(?=\/|$)/, `/${locale}`) : `/${locale}/players`;
    return query ? `${pathname}?${query}` : pathname;
  };
  return (
    <>
    <nav className="cx-nav" aria-label="XCore">
      {items.map((item) => {
        const base = `/${lang}/${item.id}`;
        const on =
          path === base ||
          path.startsWith(`${base}/`) ||
          (item.id === "me" && path === `/${lang}/link`);
        return (
          <Link key={item.id} href={base} aria-current={on ? "page" : undefined}>
            <Glyph c={item.glyph} />
            <span>{short[item.id]}</span>
          </Link>
        );
      })}
    </nav>
    <nav className="lang cx-lang" aria-label="Language">
      {locales.map((locale) => (
        <Link
          key={locale}
          href={here(locale)}
          hrefLang={locale}
          lang={locale}
          aria-current={locale === lang ? "page" : undefined}
        >
          {locale.toUpperCase()}
        </Link>
      ))}
    </nav>
    </>
  );
}
