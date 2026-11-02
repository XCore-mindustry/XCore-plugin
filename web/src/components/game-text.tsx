import { Fragment, type CSSProperties, type ReactNode } from "react";
import { parseGameText } from "@/lib/game-text";
import { glyphCells, GLYPH_COLUMNS } from "@/lib/glyph-cells";

// Private-use characters: content icons from the sheet, the rest from the game's icon font.
const PRIVATE_USE = /[\ue000-\uf8ff]/;

/** A player name or map name as the game draws it: colour tags, content icons and icon-font glyphs. */
export function GameText({ text }: { text: string }) {
  return parseGameText(text).map((run, i) => {
    const parts: ReactNode[] = [];
    let plain = "";
    for (const ch of run.text) {
      if (!PRIVATE_USE.test(ch)) {
        plain += ch;
        continue;
      }
      if (plain) parts.push(plain);
      plain = "";
      parts.push(icon(ch, run.color, parts.length));
    }
    if (plain) parts.push(plain);
    return run.color ? (
      <span key={i} style={{ color: run.color }}>
        {parts}
      </span>
    ) : (
      <Fragment key={i}>{parts}</Fragment>
    );
  });
}

function icon(ch: string, color: string | null, key: number) {
  const cell = glyphCells.get(ch.codePointAt(0)!);
  if (cell === undefined) {
    return (
      <span key={key} aria-hidden="true" className="glyph">
        {ch}
      </span>
    );
  }
  // The game tints icons with the text colour, so a white tag leaves them as drawn.
  const tint = color && !color.startsWith("#ffffff") ? color.slice(0, 7) : null;
  const style = {
    "--gx": cell % GLYPH_COLUMNS,
    "--gy": Math.floor(cell / GLYPH_COLUMNS),
    ...(tint ? { "--tint": tint } : {}),
    ...(color && color.length === 9 ? { opacity: parseInt(color.slice(7), 16) / 255 } : {}),
  } as CSSProperties;
  return <span key={key} aria-hidden="true" className="game-icon" data-tint={tint ? "" : undefined} style={style} />;
}
