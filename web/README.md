# xcore-web

The XCore website: landing page, Discord sign-in, player profiles, leaderboards and match history in English, Ukrainian and Russian, styled after the plugin's in-game menu kit.

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

## Player accounts and statistics

- `/{lang}/me`: sign in with Discord, choose linked profiles, and sign out.
- `/{lang}/link`: enter a game code and explicitly confirm the nickname/PID; also handles guarded unlink.
- `/{lang}/players`: search by nickname or PID; `/{lang}/players/{pid}` shows statistics and individual history.
- `/{lang}/top`: playtime, Hexed points, Mini-PvP and HexedCore Elo; seasonal ladders support season selection.
- `/{lang}/matches`: all recorded games, mode and player filters; `/{lang}/matches/{matchId}` shows the complete match.

Copy `.env.example` to `.env.local` and configure the two Mongo connections. Game credentials need **read only** access to the game database; website credentials need read/write and index creation only in `xcore_web` (sessions, one-time OAuth attempts, rate limits and confirmation intents). Public queries return explicit DTOs without UUIDs, IPs, credentials or Discord IDs. A Discord identity can have multiple game profiles; linking never merges statistics.

Register `${WEB_ORIGIN}/api/auth/discord/callback` in the Discord Developer Portal and provide `DISCORD_CLIENT_ID` and `DISCORD_CLIENT_SECRET` through the runtime environment. Only `identify` is requested. The server uses Authorization Code + PKCE/state, consumes the browser-bound attempt once, and stores a hashed opaque session token. Discord access tokens are used for identity lookup and are not persisted. Logout revokes the stored session. The public origin must be HTTPS outside loopback development. Set optional `TRUSTED_PROXY_IP_HEADER` only when a trusted proxy overwrites or removes caller-supplied values for that header before forwarding (for example, `X-Real-IP`). Leaving it unconfigured, or a request missing that header, skips the optional sign-in IP limit without a shared anonymous bucket.

Link/unlink additionally require `REDIS_URL`. Use an ACL-restricted web credential: read `xcore:discord-link:code:*`, append only to the allowed `xcore:rpc:req:{server}` streams, and read `xcore:rpc:resp:web` plus `xcore:evt:server:heartbeat`. `requested_by=web` is a routing label, not authentication. Set `XCORE_ACCOUNT_SERVERS` to the comma-separated **writable** fallback server names; read-only lobby servers should be absent. The code's issuing server executes link. Unlink chooses the profile's current server when allowed, then a configured writable fallback with a heartbeat in the last 90 seconds. If none is live, it reports unavailability before creating an intent. Confirmed intents keep their original executor for receipt lookup. Without a configured fallback, offline unlink is unavailable; no server name is guessed. The website never writes game link fields.

Set the plugin's `[discord] website_link_url` to `${WEB_ORIGIN}/discord/link`. The unlocalized URL redirects to the player's browser language; the manual Discord `/link` flow remains available. Run the plugin with the new account operation RPC contract before enabling this URL. Requests use a random business operation ID distinct from the transport correlation ID. Success requires a typed persisted result; timeout is unknown, and no mutation is automatically retried. The result page lets the user explicitly check the same operation again, including after its execution deadline; a fresh transport envelope retrieves the stored receipt without extending the mutation deadline. A missing receipt leaves the old outcome unknown. A consumed code can require reissuing after a storage failure.

Login, stored sessions and statistics use Mongo and remain independent of Redis, the bot and game-server uptime. Changing a binding requires Redis and a writable game executor. Discord roles synchronize asynchronously through the bot and its reconciliation loop.

The TypeScript account contract is generated in the sibling `xcore-protocol` checkout. Keep that submodule alongside `xcore-web` when building; do not hand-edit generated payload types. Until protocol 0.17.0 is released, the bot uses the sibling checkout and the plugin uses its `gradle/local-protocol.init.gradle` composite-build helper.

## Checks

```bash
bun run test
bun run lint
bunx tsc --noEmit
bun run build
```

Real Mongo/Redis checks **require disposable Mongo databases** with names beginning `xcore_web_test_` **and a dedicated disposable Redis instance**. Setup deletes the canonical `xcore:rpc:req:qa-game`, `xcore:rpc:resp:web` and `xcore:evt:server:heartbeat` streams. The Mongo database-name guard does not protect Redis; never use a shared Redis instance.

Start dedicated containers on unused loopback ports (27028 and 6388 here), without persistent volumes:

```bash
docker run --rm -d --name xcore-web-test-mongo -p 127.0.0.1:27028:27017 mongo:8
docker run --rm -d --name xcore-web-test-redis -p 127.0.0.1:6388:6379 redis:8-alpine
```

Once both services accept connections, run the tests. Set all three Mongo URI variables explicitly to override any existing environment configuration:

```bash
XCORE_WEB_INTEGRATION=1 \
MONGO_URI=mongodb://127.0.0.1:27028 \
GAME_MONGO_URI=mongodb://127.0.0.1:27028 \
WEB_MONGO_URI=mongodb://127.0.0.1:27028 \
MONGO_DB_NAME=xcore_web_test_game \
WEB_MONGO_DB_NAME=xcore_web_test_auth \
REDIS_URL=redis://127.0.0.1:6388 \
WEB_ORIGIN=http://localhost:3000 XCORE_ACCOUNT_SERVERS=qa-game \
bun test tests/integration.test.ts
```

These tests seed their own `players`/games/rating fixtures and simulate the executor's canonical Redis response. They do not use production data or perform real Discord authorization.

After testing, stop the disposable containers; `--rm` removes them and their data:

```bash
docker stop xcore-web-test-mongo xcore-web-test-redis
```

## Docker

From the workspace root: `docker compose --profile web up --build`. This profile includes Mongo initialization and Redis; the website has no startup dependency on the bot or Redis. Its default loopback port is 3100; change `WEB_ORIGIN` to the public origin behind Caddy.

For a standalone image build from this directory, provide the canonical protocol as a named BuildKit context:

```bash
docker build --build-context xcore_protocol=../xcore-protocol -t xcore-web .
```

BuildKit/buildx is required for named contexts. Runtime secrets are never passed into the image build or copied into the image.

## Deploy

x-core.org runs the standalone build on the game server: a systemd user unit `xcore-web` serves `~/services/xcore-web` on 127.0.0.1:3100, and Caddy proxies the domain to it. After a build and the two `cp` steps above:

```bash
rsync -az --delete .next/standalone/ <server>:services/xcore-web/
ssh <server> systemctl --user restart xcore-web
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
