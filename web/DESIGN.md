---
name: XCore
description: A network of Mindustry servers, drawn in the in-game menu kit of XCore-plugin.
colors:
  ground: "#101217"
  inset: "#14161c"
  window: "#1a1d25"
  card: "#242833"
  head: "#2b3040"
  head-hover: "#30364a"
  edge: "#3a3f4f"
  ghost: "#555b70"
  text: "#eef0f5"
  muted: "#b0b6c6"
  success: "#24402b"
  error: "#4a2626"
  gold: "#ffd37f"
  gold-band: "#4d4026"
  blue: "#7fc8ff"
  blue-band: "#243c4f"
  green: "#98d982"
  green-band: "#2c4227"
  purple: "#d79bff"
  purple-band: "#402c4f"
  red: "#ff8f8f"
  red-band: "#4f2a2a"
  orange: "#ffb074"
  orange-band: "#4f3524"
  teal: "#7fe0d0"
  teal-band: "#24443f"
  gray: "#c9ced9"
  gray-band: "#353a48"
  discord: "#8f99ff"
typography:
  display:
    fontFamily: "Tektur, Arial Narrow, sans-serif"
    fontSize: "clamp(2rem, 1.3rem + 2.4vw, 3.25rem)"
    fontWeight: 800
    lineHeight: 1
  loud:
    fontFamily: "Tektur, Arial Narrow, sans-serif"
    fontSize: "clamp(4.5rem, 3rem + 6vw, 6rem)"
    fontWeight: 800
    lineHeight: 0.8
    fontFeature: "tnum"
  headline:
    fontFamily: "Tektur, Arial Narrow, sans-serif"
    fontSize: "clamp(1.875rem, 1.2rem + 2.4vw, 3rem)"
    fontWeight: 700
    lineHeight: 1.05
  title:
    fontFamily: "Tektur, Arial Narrow, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 700
    lineHeight: 1.15
  label:
    fontFamily: "Tektur, Arial Narrow, sans-serif"
    fontSize: "0.95rem"
    fontWeight: 600
  lede:
    fontFamily: "Golos Text, system-ui, sans-serif"
    fontSize: "clamp(1rem, 0.95rem + 0.3vw, 1.1875rem)"
    fontWeight: 400
    lineHeight: 1.6
  body:
    fontFamily: "Golos Text, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  command:
    fontFamily: "Fira Code, ui-monospace, monospace"
    fontSize: "0.8125rem"
    fontWeight: 500
    lineHeight: 1
    fontFeature: "liga 0"
rounded:
  none: "0px"
spacing:
  cell: "4px"
  gap: "8px"
  pad: "clamp(14px, 1.6vw, 20px)"
  gutter: "clamp(10px, 2.2vw, 28px)"
  section: "clamp(28px, 5vw, 64px)"
components:
  window:
    backgroundColor: "{colors.window}"
    rounded: "{rounded.none}"
  card:
    backgroundColor: "{colors.card}"
    rounded: "{rounded.none}"
  title-band:
    backgroundColor: "{colors.gold-band}"
    textColor: "{colors.gold}"
    typography: "{typography.label}"
    padding: "6px 10px"
    height: "42px"
  command-chip:
    backgroundColor: "{colors.inset}"
    textColor: "{colors.gold}"
    typography: "{typography.command}"
    rounded: "{rounded.none}"
    padding: "5px 7px"
  button:
    backgroundColor: "{colors.head}"
    textColor: "{colors.text}"
    rounded: "{rounded.none}"
    padding: "0 14px"
    height: "44px"
  button-hover:
    backgroundColor: "{colors.head-hover}"
  button-target:
    backgroundColor: "{colors.gold}"
    textColor: "{colors.inset}"
    rounded: "{rounded.none}"
    padding: "6px 20px"
    height: "56px"
  tab:
    backgroundColor: "{colors.inset}"
    textColor: "{colors.muted}"
    rounded: "{rounded.none}"
    width: "52px"
    height: "44px"
  tab-pressed:
    backgroundColor: "{colors.card}"
    textColor: "{colors.text}"
  field-row:
    backgroundColor: "{colors.inset}"
    textColor: "{colors.muted}"
    padding: "8px 10px 8px 12px"
  ghost-cell:
    backgroundColor: "transparent"
    textColor: "{colors.muted}"
    rounded: "{rounded.none}"
  lit-cell:
    backgroundColor: "{colors.orange-band}"
    textColor: "{colors.orange}"
    rounded: "{rounded.none}"
  dock:
    backgroundColor: "{colors.head}"
    height: "64px"
---

# Design System: XCore

## Overview

**Creative North Star: "The Player Pass"**

XCore's web surfaces are the in-game menu kit of XCore-plugin (`ui/kit`, built on xcore-ui) carried onto the web. A visitor should feel they have opened one more menu of the game they already play: dark windows sitting on a tiled floor of Mindustry sand, a title band across the top of each card, one colour per topic, square corners everywhere, and pixel sprites from the game itself. The landing page frames all of this as the player's pass, a single profile that follows them across every mode and server; later surfaces (docs, the TileLogger visualizer) reuse the same windows, bands, cells and chips without the pass framing.

Density is medium-high, as in a game menu: cards pack edge to edge with 4px and 8px gaps, labels are short, and every data field names the in-game command that shows it. Depth comes from stacked dark tones, never from shadows. Colour is spent sparingly: the neutrals carry the page and each topic contributes one accent and its dark band shade.

Things a player has not reached yet are drawn, never hidden. Locked badges, leagues above the current rating and modes not yet opened sit as dashed ghost cells with dimmed, desaturated sprites. When something is reached or seen, its cell lights in the topic's band and accent, and stays lit.

**Key Characteristics:**
- Dark tonal stack: floor, inset, window, card, head band.
- One accent and its band shade per topic, set by scope, never mixed inside a card.
- Square corners and flat fills; 3px edges and 3px accent lines are the only heavy strokes.
- A command chip on every field that has an in-game source.
- Ghost cells (dashed outline) for locked or unseen things; lit cells for reached ones.
- Mindustry pixel sprites at whole-number scales, taken from the game's own packer output, and Mindustry's own icon font for icons.
- One loud number per section, in the section's accent.

## Colors

A near-black blue-grey stack lit by eight topic accents, each paired with a dark band shade, exactly as the plugin's `Accent.java` pairs them.

### Primary
- **Core Gold** (gold): the house accent and the pass's topic colour. The wordmark, the pass rating, the current league rung, the route title, the primary action fill, the pressed-tab line, the focus ring and text selection.
- **Gold Band** (gold-band): the band under gold. The route strip of the pass, the current league rung's fill, gold title bands.

### Secondary
The topic accents. Each section or card takes one pair and applies it through two custom properties: `--ac` for text, lines and borders, `--band` for title bands and lit fills.
- **Signal Orange / Orange Band** (orange, orange-band): game modes; also Mini-Attack.
- **Chat Blue / Blue Band** (blue, blue-band): the network, chat and languages; also Red vs Blue.
- **Steel Gray / Gray Band** (gray, gray-band): fair play and moderation; the pass's badge card; the Event server, which is off between events.
- **Console Purple / Purple Band** (purple, purple-band): developers and libraries; also HexedCore.
- **Turret Green / Green Band** (green, green-band): Mini-Surv and the experimental slot (now Quadset); also string literals in code samples.
- **Coolant Teal / Teal Band** (teal, teal-band): Asthosus.
- **Crux Red / Red Band** (red, red-band): Mini-PvP.

Nine modes share eight accents, so the two survival modes share green. Siege takes Core Gold, the only mode on the house colour.

Badge colours (developer, translator, map maker and the rest) are game data copied from `Badge.java` and set per cell; they are not topic accents and do not join this palette.

### Neutral
- **Sand Floor** (ground): the page floor, under a tiled 256px sprite of darkened Mindustry floor tiles.
- **Inset Black** (inset): the darkest fill. Field rows, command chips, fact cells, log rows, code blocks, the footer. Also the browser theme colour and the text colour on gold.
- **Window** (window): the body of every window.
- **Card** (card): cards inside a window; the pressed tab.
- **Head Band** (head): window heads, the pass header, buttons, route steps, the dock. Its hover shade is **Head Lit** (head-hover).
- **Edge** (edge): the 3px window border, 1px button borders, the scrollbar thumb.
- **Ghost Line** (ghost): dashed outlines of ghost cells and the glyphs inside them. Never text.
- **Paper White** (text): primary text.
- **Dust** (muted): secondary text, field labels, notes, unpressed tabs.
- **Success / Error** (success, error): feedback fills only (copy succeeded or failed, the rollback line of the log).
- **Discord Blurple** (discord): the Discord glyph only.

### Named Rules
**The One Topic Rule.** Every section, card or cell declares one accent pair and everything inside it reads from `--ac` and `--band`. A card never shows two topic accents; nested scopes (a gray badge card inside the gold pass) override the pair, they do not mix it.

**The Band Not Fill Rule.** Accents are for text, lines and borders. Large surfaces take the band shade, never the bright accent. The single exception is the primary action, which is solid gold with inset-black text.

## Typography

**Display Font:** Tektur (with Arial Narrow fallback), loaded with Latin and Cyrillic and the `wdth` axis.
**Body Font:** Golos Text (with system-ui).
**Label/Mono Font:** Fira Code (with ui-monospace), ligatures off.
**Glyph Font:** Mindustry's `icon.ttf`, exposed as `--font-icons`.

**Character:** Tektur is the game-font voice: squared, technical, wide at heavy weights. Golos Text is a calm, highly legible Cyrillic-first sans that carries everything the visitor actually reads.

### Hierarchy
- **Display** (800, clamp(2rem → 3.25rem), line-height 1): the XCore wordmark only.
- **Loud** (800, clamp(4.5rem → 6rem), line-height 0.8, tabular figures): the one loud number in each section. The pass rating uses the same voice with -0.02em tracking.
- **Headline** (700, clamp(1.875rem → 3rem), line-height 1.05, balanced wrap): window headings, in the section accent. The closing call uses a larger 800 cut, up to 4.5rem.
- **Title** (700, 1.25rem, line-height 1.15): mode names, league name (1.5rem), row headings (600, 1.125rem).
- **Label** (600, 0.95rem): title bands, buttons, tabs, route steps, dock cells, cell names that are proper names.
- **Lede** (400, clamp(1rem → 1.1875rem), max 60ch): the paragraph under a window heading. A loud lede (Tektur 600, up to 2.125rem, max 28ch) may replace it when the section has no number.
- **Body** (400, 1rem, line-height 1.6, 54–62ch): descriptions, notes, fact values. Notes and labels drop to 0.875–0.9375rem in Dust.
- **Command** (Fira Code 500, 0.8125rem): command chips. Fira Code also sets other machine data: player id, rating thresholds, log timestamps, locale codes, library names, code samples.

### Named Rules
**The Readable Cyrillic Rule.** Running data and anything read as a sentence (fact values, notes, descriptions, badge names) sets in Golos Text. Tektur's Cyrillic «д» reads as a Latin "g", so Tektur is kept to names, headings, numbers and short display labels, where that form is accepted as the game-font look.

**The Machine Text Rule.** Fira Code is for what a machine or the game types: commands, ids, timestamps, coordinates, code. Never for prose or headings.

## Layout

Content sits in windows capped at 1320px and centred, with a fluid outer gutter (10–28px) and fluid vertical section padding (28–64px). Inside a window, cards are separated by an 8px gap and cells within a card by 4px; inner padding is fluid 14–20px. Grids are asymmetric two-column splits (5:7 or 7:5) that collapse to one column below 1100px.

The pass fills the first viewport on desktop and laptop (`min-height: 100svh` minus the gutter). A short-screen block, `(min-width: 900px) and (max-height: 860px)`, tightens paddings, cell heights and the rating size so the pass and its route fit 1280x800 and 1366x768. At 899px and below the pass becomes one column and the route strip moves under the header with CSS `order`, so phones see the way in on the first screen; in the DOM it stays after the body, so reading order is unchanged.

Other breakpoints: at 899px dock cells drop their labels and become 44px squares; at 639px the pass header wraps, the ladder goes to 5 columns, the badge row to 2 and the mode row to 3, route steps stack vertically with rotated arrows, and the primary action goes full width. The pass's mode row is 5 columns (9 cells in two rows, like the badges) so it adds no height. The open mode panel puts stage and text side by side through container queries: on the board when it is 1040px or wider, on the mode itself at 640px in the phone column.

**The First Screen Rule.** On every viewport the first screen holds the way in: the route and the primary action. Fit it with a scoped media query and `order`, not by moving it in the DOM.

## Elevation & Depth

The system has no shadows. Depth is tonal: floor, inset, window, card and head band step up in lightness, and a window is set off from the floor by a 3px Edge border. Heavier separators are 3px lines: an accent line under each window head, a gold line on top of the route strip, an Edge line under the dock and above the footer. The fixed dock is lifted only by its head-band fill and that bottom line.

### Named Rules
**The Flat Stack Rule.** Separate layers by tone and 3px lines, never by `box-shadow` or blur.

## Shapes

Every corner is square (0px). Strokes come in three weights with fixed meanings: 1px for chips, buttons and cells; 2px for the current item in a ladder; 3px for window borders and accent lines. Solid 1px means lit or interactive; dashed 1px in Ghost Line means locked, unseen or illustrative. The tile highlight on the TileLogger map is a 3px gold outline that blinks in two hard steps.

Sprites are Mindustry pixel art rendered with `image-rendering: pixelated` at whole-number scales (1x, 2x, 3x of their native size), never fractional.

**The Whole Pixel Rule.** A sprite's rendered size is its native size times an integer. If a slot does not fit, change the scale or the slot, not the ratio.

## Components

### Windows and Cards
- **Corner Style:** square (0px).
- **Background:** Window, with Card for inner cards and Head Band for the window head.
- **Border:** 3px Edge on windows; none on cards.
- **Structure:** a head (heading in the accent, lede, optional loud number at the right), a 3px accent line, then a body of cards on an 8px gap.

### Title Band
The kit's signature card header. A strip at least 42px tall in the band shade, title in Tektur 600 in the accent, optional note after a middle dot in a 45% accent tint, and the card's command chips at the right.

### Command Chips
- **Style:** Fira Code 500, inset-black fill, accent text, 1px border mixed 30% accent into Edge, no ligatures, no wrapping.
- **Use:** on every field, band or row whose content a player can pull up in game (`/stats`, `/top`, `/season`, `/badge`). A field without a real command gets no chip.

### Buttons
- **Shape:** square (0px), at least 44px tall.
- **Default:** Head Band fill, 1px Edge border, Tektur 600 Paper White label, a leading glyph in the accent.
- **Hover:** border turns to the accent and fill to Head Lit (0.2s).
- **Primary action:** solid gold fill, inset-black Tektur 800 name with a smaller Golos hint under it, 56px tall; hover lightens to a pale gold, press drops 2px. One per screen.
- **Discord:** a default button with the Discord Blurple glyph, 52–56px tall.
- **Text link:** accent text, 40%-accent underline that goes solid on hover, 44px hit area.

### Tabs
Flat toggles that stay pressed, as in the kit. Unpressed: inset fill, Dust label. Pressed: Card fill, Paper White label and a 3px accent line underneath. Used for the language switch.

### Cells
The shared grid unit for badges, leagues, modes, languages and dock sections.
- **Ghost:** transparent, 1px dashed Ghost Line, Dust label, sprite at 30% opacity and fully desaturated (glyphs in Ghost Line).
- **Lit:** band fill, 1px solid accent border, accent or Paper White label, full-colour sprite.
- **Current:** band fill and a 2px solid accent border (the reached league).
- **Stamp:** when a cell lights from a visitor action it plays a 0.6s stamp: the mark lands at 1.6x scale, rotated -8deg and brightened, then settles. Stamps are only ever added during a visit, never cleared.

### Fields and Facts
- **Field row:** inset fill, label in Dust (Golos), value in Tektur 600, command chip at the end.
- **Fact cell:** inset fill, small Dust label, value in Golos 600 in the accent (see The Readable Cyrillic Rule).

### Navigation (the Dock)
A fixed 64px strip in Head Band with a 3px Edge bottom line. It is the pass folded: avatar sprite, player name and rating at the left (name hidden under 640px), section cells at the right, then Discord. It slides in (0.5s) once the whole pass section is above the viewport; the observer watches the section itself so anchor jumps and mid-page reloads work. Section cells are ghost cells that light and stamp when their section is seen. It is `inert` while hidden.

### Mode Board
A rail and a panel. All nine modes stand as rows in a 20rem rail, in the order the Discord #information channel lists the servers: a 48px sprite, the name in the mode's accent, a one-line summary and a chevron pointing at the panel. The open row takes its band fill and a 3px accent bar on the left. The open panel spans the whole rail height beside it: a sprite stage on the floor texture, a description, a fact grid and the mode's commands (omitted when a mode has none). The markup stays an accordion (each `.mode` holds its head button and panel); on the rail `.mode` is `display: contents` so both land on the board's grid. Below 900px it becomes a vertical list, each mode its own box, with chevrons pointing down.

### Log Rows
TileLogger history: inset rows with a Fira Code timestamp, a bold player name and the action; the resolving line (rollback) takes the Success fill.

### Example Marker
A dashed gold chip that marks synthetic data as an example. It is reserved for that honesty label; it is never a section label or a heading decoration.

## Do's and Don'ts

### Do:
- **Do** scope every section and card to one accent pair through `--ac` and `--band`.
- **Do** put a command chip on every field that has an in-game command, and only there.
- **Do** draw locked, unreached or unseen things as ghost cells (1px dashed Ghost Line, 30% desaturated sprite) instead of hiding them.
- **Do** give each section exactly one loud number in its accent, and only a real count (9 modes, 9 menu languages, 8 open repositories) or a value inside a labelled example (rating 1487, 34 restored blocks).
- **Do** render sprites with `image-rendering: pixelated` at whole-number scales.
- **Do** draw icons from Mindustry's icon font (`--font-icons`) or as masks of game sprites, marked `aria-hidden` next to a text label.
- **Do** keep every touch target at least 44px.
- **Do** keep the theming of browser surfaces: gold selection with inset text, 2px gold focus outline offset 2px, Edge-on-inset scrollbar, dark color-scheme.
- **Do** honour the reduced-motion kill switch: under `prefers-reduced-motion: reduce` all animations and transitions are off, and smooth scrolling and the tile blink are opt-in.
- **Do** ease motion with `cubic-bezier(0.16, 1, 0.3, 1)`.

### Don't:
- **Don't** round corners or add shadows; depth is tone and 3px lines.
- **Don't** fill large areas with a bright accent; use its band shade. Only the primary action is solid gold.
- **Don't** set fact values, notes or other running Russian text in Tektur.
- **Don't** use Fira Code for prose or headings.
- **Don't** scale sprites by fractional ratios or let them smooth.
- **Don't** composite sprites from raw `core/assets-raw` layers: they lack outlines, weapons, legs and the team cell, and read as old versions of the game. Run the game's packer (`./gradlew tools:pack`) and take the `*-full` icons; recolour a team only by swapping the cell colours as the game tints them.
- **Don't** substitute emoji or another icon set for the game's icons.
- **Don't** clear a stamp or a lit cell within a visit.
- **Don't** show live player counts, uptime or other figures the plugins cannot back up, even as a loud number.

## Known Gaps

Left open after the finish review; these describe the current build, not rules to copy.
- The stamp is not visible in the pass's own mode row: those cells light while the pass is scrolled away, so the visitor never sees them land.
- Dock cells do not use the kit's pressed-tab sign (a 3px accent line underneath); only the language switch does.
- The Одна сеть (Network) and Для разработчиков (Developers) sections use no sprites.
- The fair-play text card leaves empty space at 1440px.
- Rail sprites (48px) and the pass's mode cells (40px) fit large sprites such as Quad (220px) and the accelerator (224px) by fractional ratios, against The Whole Pixel Rule.
