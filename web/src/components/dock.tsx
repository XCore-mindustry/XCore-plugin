"use client";

import { useEffect, useState } from "react";
import { glyph, links, sample, type Accent } from "@/content";
import type { Dictionary } from "@/i18n";
import { accent, Glyph, Sprite } from "./kit";
import { markSeen, usePassState } from "./pass-store";

const sections: { id: "modes" | "network" | "fair" | "dev"; glyph: string; accent: Accent }[] = [
  { id: "modes", glyph: glyph.modePvp, accent: "orange" },
  { id: "network", glyph: glyph.chat, accent: "blue" },
  { id: "fair", glyph: glyph.eye, accent: "gray" },
  { id: "dev", glyph: glyph.terminal, accent: "purple" },
];

/** The pass, folded into a strip once the visitor scrolls past it. Each section stamps its cell when seen. */
export function Dock({ t }: { t: Dictionary["dock"] }) {
  const { seen } = usePassState();
  const [docked, setDocked] = useState(false);

  useEffect(() => {
    // Watch the whole pass, not a marker: a zero-height marker misses jumps (anchors, reloads mid-page).
    const pass = document.getElementById("pass");
    const fold = new IntersectionObserver(([e]) => setDocked(!e.isIntersecting && e.boundingClientRect.top < 0));
    if (pass) fold.observe(pass);

    const spy = new IntersectionObserver(
      (entries) => entries.forEach((e) => e.isIntersecting && markSeen(e.target.id)),
      { rootMargin: "0px 0px -45% 0px" },
    );
    sections.forEach((s) => {
      const el = document.getElementById(s.id);
      if (el) spy.observe(el);
    });
    return () => {
      fold.disconnect();
      spy.disconnect();
    };
  }, []);

  return (
    <nav className="dock" data-docked={docked} aria-label={t.label} inert={!docked}>
      <a href="#pass" className="dock-id">
        <Sprite src="/sprites/alpha.png" size={48} />
        <span className="sr-only">{t.pass}: </span>
        <span className="dock-name">{sample.name}</span>
        <span className="dock-rating">{sample.rating}</span>
      </a>
      <ol className="dock-cells">
        {sections.map((s) => (
          <li key={s.id}>
            <a href={`#${s.id}`} className="dock-cell" data-on={seen.has(s.id)} style={accent(s.accent)} aria-label={t[s.id]}>
              <Glyph c={s.glyph} />
              <span className="dock-label">{t[s.id]}</span>
            </a>
          </li>
        ))}
      </ol>
      <a className="dock-discord" href={links.discord} aria-label="Discord">
        <Glyph c={glyph.discord} />
        <span className="dock-label">Discord</span>
      </a>
    </nav>
  );
}
