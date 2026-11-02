import Link from "next/link";
import { Suspense, type ReactNode } from "react";
import { notFound } from "next/navigation";
import { hasLocale } from "@/i18n/config";
import { communityCopy } from "@/i18n/community";
import { CommunityNav, CommunityNavLinks } from "@/components/community-nav";
import { Glyph, Sprite } from "@/components/kit";
import { glyph, links } from "@/content";
import "./community.css";

export default async function CommunityLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ lang: string }>;
}) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const t = communityCopy(lang);
  return (
    <div className="cx">
      <a className="skip" href="#community-main">
        {t.skip}
      </a>
      <header className="cx-bar">
        <Link href={`/${lang}`} className="cx-brand" aria-label={`XCore — ${t.home}`}>
          <Sprite src="/sprites/alpha.png" size={48} />
          <span>XCore</span>
        </Link>
        <Suspense fallback={<CommunityNavLinks lang={lang} />}>
          <CommunityNav lang={lang} />
        </Suspense>
      </header>
      <main id="community-main" className="cx-main">
        {children}
      </main>
      <footer className="cx-foot">
        <div className="cx-foot-inner">
          <Link className="text-link" href={`/${lang}`}>
            XCore · {t.home}
          </Link>
          <a className="text-link" href={links.discord} rel="noreferrer">
            <Glyph c={glyph.discord} />
            {t.community}
          </a>
        </div>
      </footer>
    </div>
  );
}
