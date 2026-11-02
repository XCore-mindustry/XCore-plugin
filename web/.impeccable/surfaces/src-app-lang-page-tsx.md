---
version: 1
slug: "src-app-lang-page-tsx"
primary_target: "src/app/[lang]/page.tsx"
related_targets: []
---

# Landing page

Scope: the site's front page, `/ru` and `/en`. Visitor mode: Persuade.

Audience and job: a Mindustry player deciding whether XCore is worth joining, often on a phone in portrait. The action is opening Mindustry and finding XCore in Community Servers, or joining Discord. Developers get one clearly separate section with the open-source libraries.

Proof on hand: the plugins' real commands, badges, modes and languages. No player counts, uptime, screenshots of live servers or testimonials exist; the sample player on the pass is synthetic and labeled as an example.

## Direction contract

THESIS: One profile for the whole network. The page is the player's XCore pass, carried through every mode and server. It rejects the usual game-server page: screenshot hero, grid of mode cards, Discord widget, online counter.

OWN-WORLD: the in-game kit on the web. Window ground #14161c, header band #2b3040, cards #242833. Each topic owns an accent and its dark band shade: gold #ffd37f / #4d4026, blue #7fc8ff / #243c4f, green #98d982 / #2c4227, purple #d79bff / #402c4f, red, orange, gray. Square corners. Title bands in the band shade with accent text. A 3px accent line under tabs. Tabs are flat toggle buttons that stay pressed. Mindustry pixel sprites at integer scale. The game's icon set drawn as masks. Tektur for labels and headings, Golos Text for body, Fira Code only for commands. Ghost cells: locked badges and unvisited modes drawn as dim outlines, never hidden.

STORY: the visitor learns that one profile (playtime, seasonal PvP rating, badges) follows them through eight modes, from Mini-PvP and HexedCore to Red vs Blue and the experimental Quadset. They believe it because every field names the in-game command that shows it (/stats, /top, /season, /badge). They act by following Play → Join Game → Community Servers → XCore, or by joining Discord.

FIRST VIEWPORT: the pass is the page, filling the first screen. Header band: XCore wordmark, one line, RU/EN switch. Body: a sample player marked "example": name with badge tag, seasonal rating set large as the one loud number, playtime, a badge row (lit and ghost cells), a mode row; each field carries its command chip. Footer strip of the pass: the four-step route, the last step "XCore" is the primary action in gold, Discord secondary. On a phone the pass is full width and the route stacks.

FORM: Player pass, assigned by the roll; my own list ranked the research tree first. seed:17177eb8b1e0ba90. Signature interaction: on scroll the pass docks into a sticky strip whose mode cells get stamped as each section is seen; stamps never clear (from ebru). Raises: ghost cells (segment); one loud number per section (massin); the mode board pushes the chosen panel forward in place (merz); every field traces to its command (jacquard); the season as the thing that matures, since Veteran is granted by hand, not by playtime (brine, translated).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open decisions

- Production domain not confirmed; no canonical URL yet.
- Docs and the TileLogger visualizer come later and reuse this world.
