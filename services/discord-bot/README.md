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
  - `/map list <server> [creator]` → `maps.list`
  - `/map remove <server> <map>` → `maps.remove`
  - `/map upload <server> <file1> [file2] [file3] [creator] [creator1] [creator2] [creator3]` → per-file confirmed upload results; individual creators override the shared default
    The uploader is recorded automatically; `creator` selects a Discord user for every attached map.
    File authors stay separate, and `/map list` shows attribution with profile links and upload dates.
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
    `player_id` accepts a signed PID (also with `#`) or an in-game `@username` directly; type a nickname
    and click an autocomplete suggestion to open that player without running `/search` first.
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
- `PERMISSIONS_MODE` (`legacy` by default, or `roles`)
- `PERMISSIONS_CONFIG_PATH` (required in `roles`; path to the shared `permissions.toml`)
- `PERMISSIONS_RPC_SERVER` (required in `roles`; exact name of the server answering security RPCs)
- `MONGO_URI` (default: `mongodb://127.0.0.1:27017`). Account merges write in one transaction, so
  they need a replica set (`...?replicaSet=rs0`); against a standalone server a merge is refused
  and nothing is changed
- `MONGO_DB_NAME` (default: `xcore`)

## Permission roles rollout

Leave `PERMISSIONS_MODE=legacy` until the plugin and shared role configuration are deployed.
Legacy mode keeps the existing admin reconciliation and Mongo password reset behavior; the
permissions file and RPC target are not required.

To switch the bot, set `PERMISSIONS_MODE=roles`, `PERMISSIONS_CONFIG_PATH=/path/to/permissions.toml`,
and `PERMISSIONS_RPC_SERVER=mini-pvp` (use your server's exact name). Set `DISCORD_GUILD_ID` to the
file's `discord.guildId`; a mismatch or invalid/missing configuration fails startup. The target
server must have roles enabled: a legacy server answers sync with `UNAVAILABLE`. Both security
RPCs target this server; it writes shared grants and notifies the other servers.

In roles mode, the bot sends only IDs listed in `discord.bindings`, with duplicates removed.
It syncs role changes, confirmed account links (including DM links), confirmed unlinks for the
old UUID, explicit guild departures, and all linked accounts every 10 minutes. Requests are
serialized and limited to four attempts per second; RPC retries reuse the same `operationId`,
while new sync attempts receive new IDs. Timeout/connection errors and `UNAVAILABLE` get one
retry; other RPC errors are logged or reported to the command caller.

Event syncs fetch the member and guild roles. Unknown Member (Discord code 10007) confirms
absence and sends an empty role list; other API failures or missing configured roles send nothing.
Each scheduled pass fetches guild roles once and exhausts the full member list before syncing.
If any member-list page fails, the entire pass is skipped. Accounts absent from the complete
snapshot receive an empty role list. Passes do not overlap, and event syncs can run between
scheduled requests. A newer event takes precedence over an older scheduled snapshot.
Expected RPC failures are summarized without per-account tracebacks; a target server with
roles switched off stops the pass early. Explicit departure/unlink events need no member fetch.
Enable the **Server Members Intent** for the bot in the Discord Developer Portal so role-change
and departure events arrive.

`/reset-password` uses `security.staff.reset-password.request`. The plugin clears staff credentials
and remembered devices. The bot does not write `is_admin`, `admin_source`, `password_hash`, or
`permission_grants` in roles mode, including during account merges. `/admin add|remove` continues
to update the configured Discord admin role and then syncs the member's game roles; include that
Discord role in the shared bindings if it should grant game access. `/admin sync` fetches all linked
accounts. `/admin list` still displays legacy stored admin flags during rollout.

Slash-command access remains controlled by the existing `DISCORD_*_ROLE_ID` settings. Game roles
never grant bot command access or log a player into the game. Roll back by setting
`PERMISSIONS_MODE=legacy`; the plugin's grants are left alone.

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
uv run ruff check
uv run ruff format --check
```

## Type check

`mypy` runs in CI against `mypy-baseline.txt`, the errors that existed when it was added.
A new error fails the build, and so does a fixed one that is still in the baseline:

```bash
uv run mypy | uv run mypy-baseline filter
# after fixing errors, shrink the baseline and commit it
uv run mypy | uv run mypy-baseline sync
```

## Cases

Reports, appeals, badge requests, staff complaints and map review share one core
(`src/xcore_discord_bot/cases/`). Everything is off by default. The core ships without flow
modules and without the protocol 0.15 transport; those are separate packages. A switch that is
on without its flow module or with a wrong Discord setup logs the reason and leaves that flow
off; the rest of the bot, and the other flows, keep running.

Environment variables:

- `CASES_REPORTS_ENABLED`, `CASES_APPEALS_ENABLED`, `CASES_BADGES_ENABLED`,
  `CASES_COMPLAINTS_ENABLED`, `CASES_MAPS_ENABLED`: `false`. One switch per flow.
- `CASES_PANEL_CHANNEL_ID`: `0`. The public panel and the parent of private user threads.
- `CASES_STAFF_FORUM_ID`: `0`. Private forum for reports, appeals and badge requests.
- `CASES_SENIOR_FORUM_ID`: `0`. Senior-only forum for complaints.
- `CASES_COMPLAINT_THREAD_CHANNEL_ID`: `0`. Parent of complaint user threads; `0` uses the
  panel channel. No non-senior role may have Manage Threads there.
- `CASES_BLACKLIST_ROLE_ID`: `0`. Members with this role cannot open cases from Discord.
- `CASES_STAFF_LOCALE`: `en` (`en`, `ru`, `uk`). Language of staff cards. Map cards are
  public and use the author's language.
- `CASES_OPEN_COOLDOWN_SECONDS`: `60`. Per Discord user and kind.
- `CASES_MAX_OPEN_PER_USER`: `3`. Open cases per Discord user and kind.
- `CASES_REPORT_BURST_LIMIT`: `20`, `CASES_REPORT_BURST_WINDOW_SECONDS`: `600`. Used by the
  report flow.
- `CASES_APPEAL_COOLDOWN_DAYS`: `14`. Used by the appeal flow.
- `CASES_BADGES_REQUESTABLE`:
  `translator,map-maker,contributor,bug-finder,event-winner,veteran,developer`. Used by the
  badge flow.
- `CASES_EVIDENCE_RETENTION_DAYS`: `180`. Chat evidence of a closed case is cleared after this.
- `CASES_REPORTER_COOLDOWN_SECONDS`: `60`. Cross-server reporter cooldown. Keep it at or below every
  server's `[cases.reports] cooldown_seconds`, or a report the server accepted is refused later.
- `CASES_NOTICE_TTL_DAYS`: `30`. Lifetime of an undelivered in-game notice.
- `DISCORD_MAPS_FORUM_ID`: `0`. The existing public maps forum.
- `MAPS_TAG_WAITING`, `MAPS_TAG_ACCEPTED`, `MAPS_TAG_REJECTED`: `Waiting`, `Accepted`,
  `Rejected`. Tag names in the maps forum; they must already exist there.
- `MAPS_IGNORED_TAGS`: `Report a map,Balance a map`.
- `MAPS_TAG_PROFILES`: `Tower Defence=tower-defense,PvP=pvp,Survival=survival,Attack=attack`.
- `MAPS_MAX_FILE_BYTES`: `4194304`.
- `DISCORD_MAP_MAKER_ROLE_ID`: `0`. Discord role given with the Map Maker badge.

A channel or role id of `0` or blank means "not configured". Staff roles are the existing
admin, general-admin (head-admin) and map-reviewer settings.

At start-up each switched-on flow is checked: channel types and guild, that the staff forum is
hidden from `@everyone` and visible to the staff roles, that the complaint forum is visible to
senior roles only, and the bot's permissions (view, send, send in threads, read history, manage
threads; create private threads in the panel channel). The staff forums need the tags `Report`,
`Appeal`, `Badge`, `Complaint`, `Open`, `Taken`, `Resolved`, `Rejected`; the bot creates the
missing ones if it has Manage Channels there, otherwise the log names them.

Commands (registered only while at least one switch is on):

- `/cases panel publish` (head admin) publishes the panel, or edits the published message in
  place. After that the bot keeps it up to date by itself when switches change.
- `/cases find` (any staff role), `/cases reopen` and `/cases subject` (head admin).
- `/language` and the panel's language button store a person's language choice. Without a
  choice the bot follows the language of their Discord client.

Case numbers start at 2001, above the numbers of the old ticket bot.

With every switch off no command, daemon, listener or database write of this feature runs. A
panel published earlier keeps its buttons; they answer that the feature is unavailable. Remove
that message by hand if the switch-off is permanent.

Flow modules are written against the [flow and transport contract](src/xcore_discord_bot/cases/README.md);
[`tests/cases_dummy_flow.py`](tests/cases_dummy_flow.py) is a complete minimal flow.
