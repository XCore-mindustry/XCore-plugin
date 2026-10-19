import Image from "next/image";
import Link from "next/link";
import type { CSSProperties } from "react";
import { notFound } from "next/navigation";
import { CopyName } from "@/components/copy-name";
import { Dock } from "@/components/dock";
import { accent, Band, Cmd, Glyph, Sprite } from "@/components/kit";
import { ModeBoard } from "@/components/mode-board";
import { PassModes } from "@/components/pass-modes";
import { badges, glyph, leagues, libraries, links, modes, sample } from "@/content";
import { getDictionary, type Dictionary } from "@/i18n";
import { hasLocale, locales } from "@/i18n/config";

const menuLanguages = ["en", "ru", "uk", "be", "pl", "cs", "de", "es", "fr"];

function BadgeMark({ badge }: { badge: (typeof badges)[number] }) {
  if (badge.glyph) return <Glyph c={badge.glyph} />;
  if (badge.sprite) return <Sprite src={badge.sprite} size={32} />;
  return <span aria-hidden="true" className="mask" style={{ maskImage: `url(${badge.mask})`, WebkitMaskImage: `url(${badge.mask})` }} />;
}

function Route({ t }: { t: Dictionary["route"] }) {
  return (
    <div className="route">
      <p className="route-title">{t.title}</p>
      <ol className="route-steps">
        {t.steps.map((step) => (
          <li key={step} className="step">
            <span className="step-name">{step}</span>
            <Glyph c={glyph.right} className="step-arrow" />
          </li>
        ))}
        <li className="step step-target">
          <CopyName name={t.target} label={t.copy} copied={t.copied} failed={t.failed} />
        </li>
      </ol>
      <p className="route-note">{t.note}</p>
    </div>
  );
}

export default async function Page({ params }: PageProps<"/[lang]">) {
  const { lang } = await params;
  if (!hasLocale(lang)) notFound();
  const t = getDictionary(lang);

  const reached = leagues.findLastIndex((l) => sample.rating >= l.from);
  const league = leagues[reached];
  const next = leagues[reached + 1];
  const tagBadge = badges.find((b) => b.id === sample.activeBadge)!;
  const modeNames = Object.fromEntries(modes.map((m) => [m.id, t.modes.items[m.id].name])) as Record<(typeof modes)[number]["id"], string>;

  return (
    <>
      <a href="#pass" className="skip">
        {t.skip}
      </a>
      <Dock t={t.dock} />

      <main>
        <section id="pass" className="pass-wrap" aria-labelledby="pass-h">
          <article className="pass window" style={accent("gold")}>
            <header className="pass-head">
              <div className="brand">
                <Sprite src="/sprites/core-shard.png" size={96} className="brand-mark" />
                <h1 id="pass-h" className="wordmark">
                  XCore
                </h1>
              </div>
              <p className="tagline">{t.pass.tagline}</p>
              <nav className="lang" aria-label={t.langLabel}>
                {locales.map((l) => (
                  <Link key={l} href={`/${l}`} hrefLang={l} lang={l} aria-current={l === lang ? "page" : undefined}>
                    {l.toUpperCase()}
                  </Link>
                ))}
              </nav>
            </header>

            <div className="pass-body">
              <div className="card pass-id">
                <p className="example">
                  <span className="example-chip">{t.pass.example}</span>
                  <span className="example-note">{t.pass.exampleNote}</span>
                </p>
                <div className="who">
                  <div className="avatar">
                    <Sprite src="/sprites/alpha.png" size={48} scale={2} />
                  </div>
                  <div>
                    <p className="player">
                      <span className="tag" style={{ color: tagBadge.color }}>
                        &lt;
                        <BadgeMark badge={tagBadge} />
                        &gt;
                      </span>{" "}
                      {sample.name}
                    </p>
                    <p className="player-id">#{sample.id}</p>
                  </div>
                </div>
                <dl className="fields">
                  <div>
                    <dt>{t.pass.playtime}</dt>
                    <dd>{t.pass.hours(sample.hours)}</dd>
                    <Cmd>/stats</Cmd>
                  </div>
                  <div>
                    <dt>{t.pass.matches}</dt>
                    <dd>{sample.matches}</dd>
                    <Cmd>/matches</Cmd>
                  </div>
                </dl>
              </div>

              <div className="card pass-season">
                <Band as="p" title={t.pass.rating} cmds={["/top", "/season"]} />
                <div className="season-body">
                  <p className="rating">
                    <span className="rating-num">{sample.rating}</span>
                    <span className="rating-unit">ELO</span>
                  </p>
                  <div className="league">
                    <Sprite src={`/sprites/items/${league.id}.png`} size={32} scale={2} />
                    <p>
                      <span className="league-label">{t.pass.league}</span>
                      <span className="league-name">{t.leagues[league.id]}</span>
                      <span className="league-next">{t.pass.nextLeague(next.from - sample.rating, t.leagues[next.id])}</span>
                    </p>
                  </div>
                  <ol className="ladder">
                    {leagues.map((l, i) => (
                      <li key={l.id} className="rung" data-on={i <= reached} aria-current={i === reached ? "step" : undefined}>
                        <Sprite src={`/sprites/items/${l.id}.png`} size={32} />
                        <span className="sr-only">{t.leagues[l.id]}</span>
                        <span className="rung-from">{l.from}</span>
                      </li>
                    ))}
                  </ol>
                  <p className="season-note">{t.pass.ladderNote}</p>
                </div>
              </div>

              <div className="card pass-badges" style={accent("gray")}>
                <Band as="p" title={t.pass.badges} cmds={["/badge"]} />
                <ul className="badge-cells">
                  {badges.map((b) => {
                    const on = sample.unlocked.includes(b.id);
                    return (
                      <li key={b.id} className="badge-cell" data-on={on} style={{ "--bc": b.color } as CSSProperties}>
                        <span className="badge-mark">
                          <BadgeMark badge={b} />
                        </span>
                        <span className="badge-name">{t.badges[b.id]}</span>
                        {!on && <span className="sr-only">, {t.locked}</span>}
                      </li>
                    );
                  })}
                </ul>
              </div>

              <div className="card pass-modes" style={accent("orange")}>
                <Band
                  as="p"
                  title={
                    <>
                      {t.pass.modes} <span className="band-note">· {t.pass.modesNote}</span>
                    </>
                  }
                />
                <PassModes names={modeNames} />
              </div>
            </div>

            <footer className="pass-route">
              <Route t={t.route} />
              <a className="btn btn-discord" href={links.discord}>
                <Glyph c={glyph.discord} /> {t.route.discord}
              </a>
            </footer>
          </article>
        </section>

        <section id="modes" className="section" style={accent("orange")} aria-labelledby="modes-h">
          <div className="window">
            <header className="window-head">
              <h2 id="modes-h">{t.modes.title}</h2>
              <p className="lede">{t.modes.lede}</p>
              <p className="loud">
                <span className="loud-num">{modes.length}</span>
                <span className="loud-label">{t.modes.count}</span>
              </p>
            </header>
            <div className="line" />
            <div className="window-body">
              <ModeBoard t={t.modes} />
            </div>
          </div>
        </section>

        <section id="network" className="section" style={accent("blue")} aria-labelledby="network-h">
          <div className="window">
            <header className="window-head">
              <h2 id="network-h">{t.network.title}</h2>
              <p className="lede">{t.network.lede}</p>
            </header>
            <div className="line" />
            <div className="network-grid">
              <div className="card langs">
                <p className="loud">
                  <span className="loud-num">{menuLanguages.length}</span>
                  <span className="loud-label">{t.network.languages}</span>
                </p>
                <ul className="lang-cells">
                  {menuLanguages.map((code) => (
                    <li key={code} lang={code}>
                      {code}
                    </li>
                  ))}
                </ul>
                <p className="muted">{t.network.languagesNote}</p>
                <a className="text-link" href={links.weblate}>
                  <Glyph c={glyph.link} /> {t.network.weblate}
                </a>
              </div>
              <ul className="rows">
                {t.network.rows.map(([cmd, title, text]) => (
                  <li key={cmd} className="row">
                    <Cmd>{cmd}</Cmd>
                    <div>
                      <h3>{title}</h3>
                      <p>{text}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        <section id="fair" className="section" style={accent("gray")} aria-labelledby="fair-h">
          <div className="window">
            <header className="window-head">
              <h2 id="fair-h">{t.fair.title}</h2>
              <p className="lede lede-loud">{t.fair.lede}</p>
            </header>
            <div className="line" />
            <div className="fair-grid">
              <figure className="tilemap">
                <div className="tilemap-frame">
                  <Image src="/sprites/tilelog-map.png" width={384} height={192} alt="" unoptimized className="sprite tilemap-img" />
                  <span className="tile-mark" aria-hidden="true" />
                </div>
                <figcaption className="card">
                  <Band
                    as="p"
                    title={
                      <>
                        {t.fair.logTitle} <span className="band-note">· {t.fair.example}</span>
                      </>
                    }
                  />
                  <ol className="log">
                    {t.fair.log.map(([time, who, what, block]) => (
                      <li key={time}>
                        <time>{time}</time>
                        <span className="who">{who}</span>
                        <span>
                          {what} {block}
                        </span>
                      </li>
                    ))}
                  </ol>
                </figcaption>
              </figure>
              <div className="card fair-text">
                <p className="loud">
                  <span className="loud-num">34</span>
                  <span className="loud-label">
                    {t.fair.count} <span className="band-note">· {t.fair.example}</span>
                  </span>
                </p>
                <p>{t.fair.body}</p>
                <p>
                  <Cmd>/votekick</Cmd> {t.fair.votekick}
                </p>
                <a className="text-link" href={`${links.github}/TileLogger`}>
                  <Glyph c={glyph.github} /> TileLogger
                </a>
              </div>
            </div>
          </div>
        </section>

        <section id="dev" className="section" style={accent("purple")} aria-labelledby="dev-h">
          <div className="window">
            <header className="window-head">
              <h2 id="dev-h">{t.dev.title}</h2>
              <p className="lede">{t.dev.lede}</p>
              <p className="loud">
                <span className="loud-num">{libraries.length}</span>
                <span className="loud-label">{t.dev.count}</span>
              </p>
            </header>
            <div className="line" />
            <div className="dev-grid">
              <ul className="libs">
                {libraries.map((l) => (
                  <li key={l.name}>
                    <a className="lib" href={`${links.github}/${l.repo}`}>
                      <code>{l.name}</code>
                      <Glyph c={glyph.github} />
                      <span>{t.dev.libs[l.name]}</span>
                    </a>
                  </li>
                ))}
              </ul>
              <div className="card snippet">
                <Band as="p" title={t.dev.snippet} />
                <pre>
                  <code>
                    {"repositories {\n    mavenCentral()\n    maven("}
                    <span className="s-str">&quot;https://maven.x-core.org/releases&quot;</span>
                    {")\n}\n\ndependencies {\n    compileOnly("}
                    <span className="s-str">&quot;org.xcore:xcore-ui:0.1.0&quot;</span>
                    {")\n}"}
                  </code>
                </pre>
                <div className="dev-links">
                  <a className="btn" href={links.maven}>
                    <Glyph c={glyph.download} /> {t.dev.maven}
                  </a>
                  <a className="btn" href={links.github}>
                    <Glyph c={glyph.github} /> {t.dev.github}
                  </a>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="close" style={accent("gold")} aria-labelledby="close-h">
          <div className="close-inner">
            <h2 id="close-h">{t.close.title}</h2>
            <Route t={t.route} />
            <a className="btn btn-discord" href={links.discord}>
              <Glyph c={glyph.discord} /> {t.close.discord}
            </a>
          </div>
        </section>
      </main>

      <footer className="footer">
        <div className="footer-inner">
          <ul className="footer-links">
            <li>
              <a className="btn" href={links.github}>
                <Glyph c={glyph.github} /> GitHub
              </a>
            </li>
            <li>
              <a className="btn" href={links.discord}>
                <Glyph c={glyph.discord} /> Discord
              </a>
            </li>
            <li>
              <a className="btn" href={links.rvbDiscord}>
                <Glyph c={glyph.discord} /> {t.footer.rvb}
              </a>
            </li>
            <li>
              <a className="btn" href={links.weblate}>
                <Glyph c={glyph.bookOpen} /> {t.footer.translate}
              </a>
            </li>
            <li>
              <a className="btn" href={links.donate}>
                <Glyph c={glyph.star} /> {t.footer.donate}
              </a>
            </li>
          </ul>
          <p className="legal">{t.footer.legal}</p>
        </div>
      </footer>
    </>
  );
}
