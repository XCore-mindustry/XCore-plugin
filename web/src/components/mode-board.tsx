"use client";

import { useEffect, useRef, type CSSProperties } from "react";
import { glyph, links, modes } from "@/content";
import type { Dictionary } from "@/i18n";
import { accent, Cmd, Glyph, Sprite } from "./kit";
import { markSeen, openMode, usePassState } from "./pass-store";

/** The mode board: a rail of every mode beside the chosen mode's panel; one column of rows on phones. */
export function ModeBoard({ t }: { t: Dictionary["modes"] }) {
  const { open } = usePassState();
  const board = useRef<HTMLDivElement>(null);

  // The mode that is open when the board scrolls into view counts as opened.
  useEffect(() => {
    const io = new IntersectionObserver(([e]) => e.isIntersecting && markSeen(`mode:${open}`), { threshold: 0.35 });
    if (board.current) io.observe(board.current);
    return () => io.disconnect();
  }, [open]);

  return (
    <div className="board" ref={board} style={{ "--modes": modes.length } as CSSProperties}>
      {modes.map((m) => {
        const item = t.items[m.id];
        const isOpen = m.id === open;
        const peek = m.sprites[m.sprites.length - 1];
        return (
          <div key={m.id} className="mode" data-open={isOpen} style={accent(m.accent)}>
            <h3 className="mode-h">
              <button
                type="button"
                className="mode-head"
                aria-expanded={isOpen}
                aria-controls={`mode-${m.id}`}
                onClick={() => openMode(m.id)}
              >
                <span className="mode-name">
                  <Glyph c={m.glyph} /> {item.name}
                </span>
                <span className="mode-line">{item.line}</span>
                <span className="mode-peek">
                  <Sprite src={peek.src} size={peek.size} />
                </span>
                <Glyph c={glyph.down} className="mode-chevron" />
              </button>
            </h3>
            <div id={`mode-${m.id}`} className="mode-body" hidden={!isOpen}>
              <div className="mode-stage">
                {m.sprites.map((s) => (
                  <Sprite key={s.src} src={s.src} size={s.size} scale={s.scale} />
                ))}
              </div>
              <div className="mode-text">
                <p className="mode-desc">{item.body}</p>
                <dl className="facts">
                  {item.facts.map(([k, v]) => (
                    <div key={k}>
                      <dt>{k}</dt>
                      <dd>{v}</dd>
                    </div>
                  ))}
                </dl>
                {item.commands.length > 0 && (
                  <>
                    <p className="cmds-title">{t.commands}</p>
                    <ul className="cmds">
                      {item.commands.map(([c, d]) => (
                        <li key={c}>
                          <Cmd>{c}</Cmd>
                          <span>{d}</span>
                        </li>
                      ))}
                    </ul>
                  </>
                )}
                {m.id === "rvb" && (
                  <a className="text-link" href={links.rvbDiscord}>
                    <Glyph c={glyph.discord} /> {t.rvbDiscord}
                  </a>
                )}
                {m.id === "asthosus" && (
                  <a className="text-link" href={links.asthosus}>
                    <Glyph c={glyph.github} /> {t.asthosusMod}
                  </a>
                )}
                {m.id === "lab" && (
                  <a className="text-link" href={links.discord}>
                    <Glyph c={glyph.discord} /> {t.labSuggest}
                  </a>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
