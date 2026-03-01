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
  - `/maps <server>` → `maps.list`
  - `/remove-map <server> <map>` → `maps.remove`
  - `/upload-map <server> <file1> [file2] [file3]` (+ `.msav` attachments) → `maps.load`
- Moderation/admin slash commands (Mongo-backed):
  - `/stats`, `/search`, `/bans`
  - `/ban`, `/unban`, `/mute`, `/unmute`
  - `/remove-admin <player-id>`, `/reset-password`
- Admin request approvals:
  - consumes `xcore:evt:admin:request`
  - sends confirmation event to `xcore:cmd:admin-confirm:<server>`

## Environment variables

Required:

- `DISCORD_BOT_TOKEN`
- `DISCORD_ADMIN_ROLE_ID`
- `DISCORD_PRIVATE_CHANNEL_ID`
- `SERVER_CHANNEL_MAP_JSON` — JSON object like:
  - `{"mini-pvp": 123456789012345678, "mini-hexed": 234567890123456789}`

Optional:

- `DISCORD_GUILD_ID` (default: `0`; set non-zero for fast guild-scoped slash sync)
- `DISCORD_GENERAL_ADMIN_ROLE_ID` (default: `DISCORD_ADMIN_ROLE_ID`)
- `DISCORD_MAP_REVIEWER_ROLE_ID` (default: `DISCORD_ADMIN_ROLE_ID`)
- `REDIS_URL` (default: `redis://127.0.0.1:6379`)
- `REDIS_GROUP_PREFIX` (default: `xcore:cg`)
- `REDIS_CONSUMER_NAME` (default: `discord-bot`)
- `RPC_TIMEOUT_MS` (default: `5000`)
- `MONGO_URI` (default: `mongodb://127.0.0.1:27017`)
- `MONGO_DB_NAME` (default: `xcore`)

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
