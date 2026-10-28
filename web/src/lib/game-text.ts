/**
 * Mindustry's colour markup, read the way the game client reads it (Arc's GlyphLayout#parseColorMarkup).
 *
 * `[name]` and `[#hex]` push a colour, `[]` pops one, `[[` is a literal bracket. A tag the game
 * does not understand stays in the text as typed.
 */

export type TextRun = { text: string; color: string | null };

// Arc's Colors plus the ones Mindustry's UI adds. Lookup is case-sensitive: Arc stores each
// colour as LIGHT_GRAY and lightgray, nothing else.
const named: Record<string, string> = {
  clear: "#00000000",
  black: "#000000",
  white: "#ffffff",
  lightgray: "#bfbfbf",
  gray: "#7f7f7f",
  darkgray: "#3f3f3f",
  lightgrey: "#bfbfbf",
  grey: "#7f7f7f",
  darkgrey: "#3f3f3f",
  blue: "#4169e1",
  navy: "#00007f",
  royal: "#4169e1",
  slate: "#708090",
  sky: "#87ceeb",
  cyan: "#00ffff",
  teal: "#007f7f",
  green: "#38d667",
  acid: "#7fff00",
  lime: "#32cd32",
  forest: "#228b22",
  olive: "#6b8e23",
  yellow: "#ffff00",
  gold: "#ffd700",
  goldenrod: "#daa520",
  orange: "#ffa500",
  brown: "#8b4513",
  tan: "#d2b48c",
  brick: "#b22222",
  red: "#e55454",
  scarlet: "#ff341c",
  crimson: "#dc143c",
  coral: "#ff7f50",
  salmon: "#fa8072",
  pink: "#ff69b4",
  magenta: "#ff00ff",
  purple: "#a020f0",
  violet: "#ee82ee",
  maroon: "#b03060",
};
const colors = new Map<string, string>();
for (const [name, value] of Object.entries(named)) {
  colors.set(name, value);
  colors.set(name.replace(/^(light|dark)(gr[ae]y)$/, "$1_$2").toUpperCase(), value);
}
colors.set("accent", "#ffd37f");
colors.set("unlaunched", "#8982ed");
colors.set("highlight", "#ffe0a5");
colors.set("stat", "#ffd37f");
colors.set("negstat", "#e55454");

/** `[#…]` with 1 to 8 hex digits, but not 7. Up to 6 digits fill RRGGBB from the left. */
function hexColor(digits: string): string | null {
  if (!/^[0-9a-fA-F]{1,8}$/.test(digits) || digits.length === 7) return null;
  const rgba = digits.length === 8 ? digits : digits.padEnd(6, "0") + "ff";
  return `#${rgba.toLowerCase()}`;
}

export function parseGameText(source: string): TextRun[] {
  const runs: TextRun[] = [];
  const stack: (string | null)[] = [null];
  let text = "";
  const flush = () => {
    if (text) runs.push({ text, color: stack[stack.length - 1] });
    text = "";
  };

  for (let i = 0; i < source.length; i++) {
    const ch = source[i];
    if (ch !== "[") {
      text += ch;
      continue;
    }
    const next = source[i + 1];
    if (next === "[") {
      text += "[";
      i++;
      continue;
    }
    if (next === "]") {
      flush();
      if (stack.length > 1) stack.pop();
      i++;
      continue;
    }
    const close = source.indexOf("]", i + 1);
    const tag = close < 0 ? null : source.slice(i + 1, close);
    const color = tag === null ? null : tag.startsWith("#") ? hexColor(tag.slice(1)) : colors.get(tag) ?? null;
    if (color === null) {
      text += ch;
      continue;
    }
    flush();
    stack.push(color);
    i = close;
  }
  flush();
  return runs;
}

