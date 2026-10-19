# xcore-web

The XCore website: a landing page in Russian and English, styled after the plugin's in-game menu kit.

Next.js 16, React 19, Tailwind v4, bun.

## Run

```bash
bun install
bun run dev          # http://localhost:3000, redirects to /ru or /en by Accept-Language
```

## Build

The build uses `output: "standalone"`, so `next start` does not serve it. Run the standalone server instead:

```bash
bun run build
cp -r public .next/standalone/
cp -r .next/static .next/standalone/.next/
PORT=3000 node .next/standalone/server.js
```

## Where things live

- `src/content.ts`: facts shared by both languages: links, accents, badges, leagues, modes, the sample player.
- `src/i18n/en.ts`, `src/i18n/uk.ts`, `src/i18n/ru.ts`: all copy. `uk.ts` and `ru.ts` must match the shape of `en.ts`.
- `src/app/[lang]/globals.css`: the whole visual system. `DESIGN.md` describes it.
- `PRODUCT.md`: what the site is for and the facts it may state.

The modes follow the server list in the Discord #information channel. Port 7009 is an experimental slot whose mode changes about once a month (now Quadset); it has the id `lab`, so a new mode there only needs new copy and a sprite.

## Sprites

Sprites come from Mindustry's own packer, not from the raw layers in `core/assets-raw`, which lack outlines, weapons and team colours:

```bash
cd Mindustry && ./gradlew tools:pack
# icons: core/assets-raw/sprites_out/generated/unit-<name>-full.png, block-<name>-full.png
```

Team colours are swapped the way the game tints a unit's cell: `#ffa664` becomes the team colour, `#d06b53` the team colour times `#dcc6c6`. Every raster carries its origin in PNG metadata.

Mindustry and its art are by Anuken, GPL-3.0.
