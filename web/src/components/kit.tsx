import Image from "next/image";
import type { CSSProperties, ReactNode } from "react";
import { accents, type Accent, type badges } from "@/content";

/** The colour of a topic, for everything inside the element: --ac for text and lines, --band for its surfaces. */
export const accent = (a: Accent) => ({ "--ac": accents[a][0], "--band": accents[a][1] }) as CSSProperties;

/** A glyph of Mindustry's icon font. Decorative: the text next to it carries the meaning. */
export const Glyph = ({ c, className = "" }: { c: string; className?: string }) => (
  <span aria-hidden="true" className={`glyph ${className}`}>
    {c}
  </span>
);

/** A game sprite at a whole-pixel scale. */
export const Sprite = ({ src, size, scale = 1, alt = "", className = "" }: { src: string; size: number; scale?: number; alt?: string; className?: string }) => (
  <Image src={src} width={size * scale} height={size * scale} alt={alt} unoptimized className={`sprite ${className}`} />
);

/** An in-game command, as the player types it. */
export const Cmd = ({ children }: { children: ReactNode }) => <code className="cmd">{children}</code>;

/** A card's title band in the topic's colour, with the commands that show its content. */
export const Band = ({ title, cmds, as: Tag = "h3" }: { title: ReactNode; cmds?: string[]; as?: "h2" | "h3" | "p" }) => (
  <div className="band">
    <Tag className="band-title">{title}</Tag>
    {cmds && (
      <span className="band-cmds">
        {cmds.map((c) => (
          <Cmd key={c}>{c}</Cmd>
        ))}
      </span>
    )}
  </div>
);

/** A badge's mark: its glyph, item sprite or a mask of a status sprite, as on the in-game badge menu. */
export function BadgeMark({ badge }: { badge: (typeof badges)[number] }) {
  if (badge.glyph) return <Glyph c={badge.glyph} />;
  if (badge.sprite) return <Sprite src={badge.sprite} size={32} />;
  return <span aria-hidden="true" className="mask" style={{ maskImage: `url(${badge.mask})`, WebkitMaskImage: `url(${badge.mask})` }} />;
}
