# xcore-discord-bot

Standalone Discord bot for XCore transport migration.

## Stack

- Python (managed with `uv`)
- `discord.py`
- `redis` (asyncio) with Redis Streams

## Features (current scaffold)

- Discord → game bridge:
  - publishes to `xcore:cmd:discord-message:<server>`
- Game chat → Discord bridge:
  - consumes `xcore:evt:chat:message` via consumer group
- Slash commands over streams:
  - `/map list <server>` → `maps.list`
  - `/map remove <server> <map>` → `maps.remove`
  - `/map upload <server> <file1> [file2] [file3]` (+ `.msav` attachments) → `maps.load`
  - `/subnet list|check|allow|deny|remove|reload|import|sweep <server>` (general admin; protocol 0.6.0)
- Rating seasons (Mini-PvP and HexedCore ladders; protocol 0.9.0):
  - consumes `xcore:evt:rating:season-started|season-ending-soon|season-ended|season-rescheduled`
    and posts each announcement once to `DISCORD_SEASONS_CHANNEL_ID` (podium winners are mentioned)
  - `/season info [ladder]`, `/season top <ladder> [season]`
  - `/season extend|end-at|end-now <ladder>` (head admins, i.e. `DISCORD_GENERAL_ADMIN_ROLE_ID`) → `rating.season.reschedule.request`
  - `/season prize set|clear|list|delivered` (head admins): a `badge` prize is unlocked by the game server when
    the season ends; a `custom` prize (e.g. Nitro) waits for an admin to hand it over and record it with `delivered`.
    `set`/`clear`/`delivered` go over `rating.season.prizes.set.request` and `rating.prize.grant.update.request`;
    `list` reads `rating_seasons` and `rating_prize_grants`; `delivered` takes an optional `player` (PID) to settle one winner. Prizes are listed in the ending-soon and results posts
  - `/stats [player_id] [user]` is the player's profile: league, rating, place and the way to the next
    league on every ladder of the current season (`rating_standings`), the games played mode by mode
    (`games_v2`), playtime, badges and presence. Without arguments it opens the caller's linked account.
    The Discord link, the lookup by `user` and the ban/mute notes are shown to the player and the admins only
  - account merges move rating standings through `rating.accounts.merge.request`; if no server
    answers, the merge is queued in `rating_merge_pending` and retried every minute
- Moderation/admin slash commands (Mongo-backed):
  - `/stats`, `/search`, `/bans`
  - `/ban`, `/unban`, `/mute`, `/unmute`
  - `/admin add|remove|list|sync`, `/reset-password`
  - `/badge grant|revoke`
- Admin request approvals:
  - consumes `xcore:evt:admin:request`
  - sends confirmation event to `xcore:cmd:admin-confirm:<server>`

## Environment variables

Required:

- `DISCORD_BOT_TOKEN`
- `DISCORD_ADMIN_ROLE_ID`
- `DISCORD_PRIVATE_CHANNEL_ID`

Optional:

- `DISCORD_GUILD_ID` (default: `0`; set non-zero for fast guild-scoped slash sync)
- `DISCORD_CLEAR_STALE_COMMANDS` (default: `false`; one-shot cleanup of stale global/guild slash commands before sync)
- `DISCORD_GENERAL_ADMIN_ROLE_ID` (default: `DISCORD_ADMIN_ROLE_ID`)
- `DISCORD_MAP_REVIEWER_ROLE_ID` (default: `DISCORD_ADMIN_ROLE_ID`)
- `DISCORD_SEASONS_CHANNEL_ID` (default: `0`; season announcements are disabled while it is `0`)
- `REDIS_URL` (default: `redis://127.0.0.1:6379`)
- `REDIS_GROUP_PREFIX` (default: `xcore:cg`)
- `REDIS_CONSUMER_NAME` (default: `discord-bot`)
- `RPC_TIMEOUT_MS` (default: `5000`)
- `MONGO_URI` (default: `mongodb://127.0.0.1:27017`)
- `MONGO_DB_NAME` (default: `xcore`)

## Local setup

For a fresh clone, install dev dependencies and enable the git hook once:

```bash
uv sync --all-groups
uv run pre-commit install --install-hooks
```

After that, `pre-commit` runs automatically on every `git commit`.

## Local run

```bash
uv sync
uv run xcore-discord-bot
```

The bot automatically loads environment variables from a local `.env` file on startup.

## Docker smoke run

```bash
cp .env.example .env
docker compose up --build -d
docker compose logs -f bot
```

## Tests

```bash
uv run pytest -q
```

## Canary rollout runbook

See `docs/canary-rollout.md`.

## Lint

```bash
uvx ruff check
uvx ruff format --check
```
