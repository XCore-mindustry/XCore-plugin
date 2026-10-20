# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Next.js + React (user's choice). Built with `output: "standalone"` and shipped as a Docker image on the project's own server, next to `maven.x-core.org`. Two languages from the start: Russian and English, with a switcher; more locales can come later through the project's Weblate.

## Users

1. **Mindustry players**, the primary audience. They look for a server worth joining and want to know what game modes XCore runs, what makes it different from other community servers, and how to get in. Many play on phones, so the site is often read in portrait on a small screen.
2. **Plugin developers**, the secondary audience. Mindustry server owners and plugin authors who want the open-source libraries XCore is built on (xcore-ui, the plugin template, the Maven artifacts) and the docs for them.
3. **Server staff** (moderators and admins), later. They are the users of the TileLogger visualizer: investigating who built or broke what, and when.

## Product Purpose

XCore is a network of Mindustry servers and the open-source ecosystem of plugins that runs it. The site has three parts, built in this order:

1. **Landing page**: players first, with a separate section for developers.
2. **Docs**: player-facing (commands, modes, rules) and developer-facing (libraries, APIs, guides).
3. **TileLogger visualizer**: a tool for viewing TileLogger's block history.

Success: a player who lands here understands what XCore offers and opens Mindustry to play on it, or joins the Discord. A developer finds the libraries and how to use them.

## Positioning

XCore is a full infrastructure built specifically for Mindustry, not a single server with a few plugins: shared player profiles and PvP rating across servers, cross-server chat with real-time translation, Discord linking, its own game modes, and moderation tools that include block-level history and rollback (TileLogger). All of it is open source under the XCore-mindustry GitHub organization.

## Operating Context

- Players join through Mindustry's built-in public server list: **Play → Join Game → Community Servers**, group **XCore** (RU UI: Играть → Сетевая игра → Серверы сообщества). The site explains how to find it and does not show an IP address. As of 2026-10-18 the group lists 8 servers in `Anuken/MindustryServerList` `servers_v8.json`.
- The community lives on Discord. Red vs Blue has its own Discord.
- Players manage accounts through the `/discord link` flow in the game.
- Translations of game content go through Weblate.
- Developers get artifacts from `maven.x-core.org` (releases and snapshots) and code from GitHub.

## Capabilities and Constraints

**Game modes in the ecosystem**: eight public servers, in the order the Discord #information channel lists them (159.195.205.212, ports 7001-7007 and 7009; 7010 is a test server):
- Mini-PvP (XCore-plugin): team PvP on small maps; rated matches, seasons.
- Mini-Surv: classic co-op survival; vote for the next wave.
- Mini-Attack: storm an AI-run enemy base.
- Event: runs only during network events.
- HexedCore (aethercore-plugin): free-for-all hex capture with a core in every hex; Elo by placement, seasons, leagues Scrap to Surge Alloy. Not the legacy Hexed with Newbie-to-Devastator ranks.
- Red vs Blue (rvsb-plugin): survivors against the infected; has its own Discord.
- Siege (TheSiege): attackers against defenders on a timer.
- Experimental slot (port 7009): its mode changes about once a month. Now Quadset, a free-for-all with new content that players suggest on the #quadset-content forum; before it, Tower Defence (Tower-defence-plugin). The site must present it as experimental, not as a permanent mode.

Mini-PvP and HexedCore have separate rating ladders with the same leagues; season 1 ends January 1, 2027.

**Shared player features (XCore-plugin):**
- Persistent profiles with playtime, rated matches (`/matches`) and season rating; `/top` and `/season`.
- Badges: Developer, Translator, Map Maker, Contributor, Bug Finder, Event Winner, Veteran.
- Team chat and cross-server global chat; real-time translation of chat with per-player language.
- Private messages; Discord linking.
- Map likes and dislikes, Rock The Vote.
- Votekick and moderation.
- Game menus in 9 languages: en, ru, uk, be, pl, cs, de, es, fr.

**Developer libraries:** xcore-ui (reactive, server-driven menu UI framework for Mindustry v160), xcore-plugin-template, mindustry-testkit, cloud-mindustry, FluBundle and fluent (Project Fluent localization), xcore-protocol. Java 25, Mindustry v160.

**TileLogger:** real-time logging of block changes, rollback, grief inspection; Java with a C++20 core.

**Open decisions:**
- Where the visualizer gets its data (file upload, live from a server, or something else). Not decided; do not design around one source.
- Exactly which game modes and servers are publicly online right now. Not confirmed; do not state live counts or uptime.
- The production domain for the site. `x-core.org` is in use for Maven; the site's own address is not confirmed.

## Brand Commitments

- Name: **XCore**.
- Binding visual reference from the user: the style of XCore-plugin's in-game menu kit, built on xcore-ui (`XCore-plugin/src/main/java/org/xcore/plugin/ui/kit/`). It should be recognizably related, not a pixel-for-pixel copy, and it must look good.

## Evidence on Hand

- Links (from `XCore-plugin` config defaults): Discord `https://discord.gg/RUMCCa9QAC`, Red vs Blue Discord `https://discord.gg/UdnuFetcNt`, GitHub `https://github.com/XCore-mindustry/`, donations `https://donatello.to/xcore`, Weblate `https://xcore.eradication.fun/`.
- Feature list: `XCore-plugin/README.md`. Ecosystem list: the workspace `README.md` (submodule inventory).
- xcore-ui README with its feature list and install snippets.
- Absent, and not to be invented: player counts, server uptime, testimonials, screenshots of live servers, press, statistics, pricing. No logo file has been found in the repositories.

## Product Principles

1. **Players first, without hiding the code.** The main path leads to playing; the open-source side is one clear step away, never mixed into the player pitch.
2. **Show the real game.** Describe modes and features as they actually work in the plugins; nothing that the code cannot back up.
3. **One family.** The site, the docs and the visualizer share one visual language, related to the in-game menus, so moving from the game to the site feels continuous.
4. **Works on a phone.** Every page must be fully usable in portrait on a small screen, because that is how many Mindustry players will see it.

## Accessibility & Inclusion

- Must work on phones in portrait as well as on desktop, with touch targets sized for fingers, matching the in-game kit's rule.
- Russian and English at launch, with room for the other 7 game locales.
