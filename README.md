## XCore-plugin
#### Description
Multifunctional plugin for XCore Mindustry servers. Provides player profiles, cross-server chat, moderation, map voting, Discord linking, badges, private messaging, real-time translation, and a full menu-driven UI.

## Features

### Player Profile & Statistics
- Persistent player profiles with total playtime, PvP rating, and Hexed progression.
- Rich profile menu (`/player`, `/stats`) showing account creation date, formatted playtime, match history aggregates, and rank details.
- Player settings menu (`/settings`) with chat visibility toggles and language preferences.
- Paginated `/top` leaderboard menu with cached rankings.
- In-game leaderboard toggle (`/lb`).

### Badges
- Unlockable player badges: Developer, Translator, Map Maker, Contributor, Bug Finder, Event Winner, and Veteran.
- Badge browser showing locked, unlocked, and active states.
- Player-selectable active badge (`/badge set <id>`) with Mindustry `Iconc` glyph rendering.
- Admin grant/revoke commands for badge management.

### Chat & Social
- Team chat (`/t <message>`) and global cross-server chat (`/g <message>`).
- Configurable real-time translation pipeline with ordered fallback providers, caching, and metrics.
- Per-player translator language selection (`/tr <language>`) with `auto` detection.
- Translation stats command (`trstats`) for server console.
- Discord linking with one-time codes (`/discord link`, `/discord status`, `/discord unlink`).
- Private messaging with inbox, reply, and block/unblock flows (`/msg`, `/reply`, `/inbox`).

### Maps & Voting
- Map reputation system (`/like`, `/dislike`).
- Rock The Vote (`/rtv [map]`) for map changes, with admin-forced variant (`/artv`).
- Vote new wave (`/vnw`) for progression, with admin variant (`/avnw`).
- Map browser (`/maps`) and map statistics (`/map`).
- Map identity audit tooling for legacy vote collision detection.

### Moderation
- Ban, mute, unban, and unmute by player `#ID` or identifier.
- Temporary bans with duration support (e.g., `1d`, `30m`).
- Moderation audit history tracking actor, reason, and timestamps.
- Votekick (`/votekick <target> <reason>`) with playtime requirements.
- Ingress connection validation pipeline for join-time security checks.

### Events & Gamemodes
- Event system with voting and dedicated event menus.
- Hexed mini-game support with rank progression, points, AI control (`/ai`), and surrender.

### Cross-Server Transport
- Redis-based transport backend using the canonical `xcore-protocol` message model.
- Generated Java protocol DTOs for moderation, Discord linking, chat, heartbeat, and maps RPC.
- Remote command execution (`gcmd`) targeting specific servers or all servers.
- Player state synchronization across the network (badges, nicknames, admin changes).

### Administration
- Runtime command disabling/enabling (`disable-cmd`, `enable-cmd`).
- Runtime feature toggles (`disable-feature`, `enable-feature`) for RTV and vote-new-wave.
- Console configuration editing (`xconfig`, `edit-data`).
- Database info queries (`dbinfo`, `players`, `info`).
- Admin login via password (`/login`) with Discord role integration.

## Dependencies
- Java 25+
- MongoDB 6.0+
- Redis (for cross-server transport)
- Mindustry v158 (or compatible forks)
- `cloud-mindustry` `0.2.0+` from `https://maven.x-core.org/releases`
- `FluBundle` `1.3+` from `https://maven.x-core.org/releases`

## Architecture
- **Java 25** with **Gradle Kotlin DSL** build.
- **Avaje Inject** compile-time dependency injection; `XcorePlugin.container` is the shared `BeanScope`.
- **Incendo Cloud** command framework via `cloud-mindustry` for annotation-driven player and console commands.
- **MongoDB** sync driver for persistence; repositories handle all database access.
- **Lettuce** Redis client for transport backend.
- **xcore-protocol** generated DTOs for cross-service messaging.
- Localization via **FluBundle** with locale resolvers and fallback chains.

## Installation
1. Install **Java SDK 25** (or newer).
2. Clone the repository:
   ```bash
   git clone https://github.com/XCore-mindustry/XCore-plugin.git
   cd XCore-plugin
   ```
3. Build the project using Gradle:
   ```bash
   ./gradlew shadowJar
   ```
4. Copy the resulting `.jar` file from `build/libs/` to your Mindustry server's `config/mods` folder.
5. Configure your MongoDB and Redis connections in `<server>/config/xcore.toml` (server-local) and `secrets.toml` (global/shared, in your home directory by default). If legacy `xcconfig.json` or `secrets.json` files already exist, XCore migrates them to TOML automatically on startup and keeps backup copies.

## Commands

### Player Commands
| Command | Description |
|---------|-------------|
| `/player [id]` `/stats` `/player-statistics` | Open player profile menu. |
| `/settings [id]` | Open player settings menu. |
| `/observer` | Enter spectator mode. |
| `/surrender` | Surrender in Hexed and enter spectator mode. |
| `/lb` | Toggle in-game leaderboard. |
| `/top` | Open paginated rankings menu. |
| `/t <message>` | Send a team chat message. |
| `/g <message>` | Send a global cross-server chat message. |
| `/discord` | Open Discord linking menu. |
| `/discord link` | Generate a Discord link code. |
| `/discord status` | Check Discord link status. |
| `/discord unlink` | Unlink Discord account. |
| `/tr <language>` | Set translator language (`off`, `auto`, or language code). |
| `/badge` | Open badge browser. |
| `/badge clear` | Clear active badge. |
| `/badge set <id>` | Set active badge. |
| `/event` | Open event menu. |
| `/events [page]` | Browse events. |
| `/help [page]` | Open help menu. |
| `/main` `/xcore` `/m` | Open main XCore menu. |
| `/information` `/info` | Open information menu. |
| `/map` `/map-stats` | Show current map statistics. |
| `/map <map>` | Show statistics for a specific map. |
| `/maps [page]` `/map-ui [page]` | Browse available maps. |
| `/rtv [map]` | Vote to change the map. |
| `/vnw` | Vote for a new wave. |
| `/like` `/+` | Like the current map. |
| `/dislike` `/-` | Dislike the current map. |
| `/votekick <target> <reason>` | Start a votekick. |
| `/vote <choice>` | Vote `yes`/`no` (or `c` to cancel as admin). |
| `/msg <id> <message>` | Send a private message. |
| `/reply <message>` | Reply to the last private message. |
| `/inbox` | Open message inbox. |
| `/inbox unread` | Show unread message count. |
| `/inbox blocked` | Show blocked players. |
| `/inbox block <id>` | Block a player. |
| `/inbox unblock <id>` | Unblock a player. |
| `/login <password>` | Log in as admin. |
| `/logout` | Log out from admin. |

### Admin Commands (in-game)
| Command | Description |
|---------|-------------|
| `/ban <id> <period> [reason]` | Ban a player by `#ID`. |
| `/unban <id>` | Unban a player by `#ID`. |
| `/mute <id> <period> [reason]` | Mute a player by `#ID`. |
| `/unmute <id>` | Unmute a player by `#ID`. |
| `/set-team [teamId] [pid]` | Move a player to a different team. |
| `/artv [map]` | Force an RTV vote. |
| `/avnw` | Force a vote-new-wave session. |

### Console Commands
| Command | Description |
|---------|-------------|
| `exit` | Terminate the server. |
| `set-team <teamId> <pid>` | Move a player to a team. |
| `set-gamemode <name>` | Change server gamemode. |
| `redis-reload` | Reload Redis transport connection. |
| `transport-reload` | Reload transport backend. |
| `gg-restart [state]` | Toggle auto-restart on game over. |
| `db-delete-bots` | Delete players with <2 minutes playtime. |
| `audit-map-votes` | Audit legacy map identity collisions. |
| `gcmd <command>` | Execute a command on remote servers. |
| `disable-cmd <command>` | Disable a command at runtime. |
| `enable-cmd <command>` | Re-enable a disabled command. |
| `disabled-cmds` | List disabled commands. |
| `disable-feature <feature>` | Disable a feature (`rtv`, `vnw`). |
| `enable-feature <feature>` | Re-enable a disabled feature. |
| `disabled-features` | List disabled features. |
| `xconfig` | Show current server-local XCore configuration. |
| `xconfig <field> <value>` | Edit a server-local config value by TOML path or legacy field alias. |
| `edit-data <player> <field> <value>` | Edit a player's database entry. |
| `dbinfo <player>` | Show raw player database JSON. |
| `players` | List online players with IDs and IPs. |
| `info <query>` | Find player info by name/IP/UUID/`#ID`. |
| `help [query]` | Show command help. |
| `trstats` | Show translation pipeline metrics. |
| `tempban <target> <period> [reason]` | Temporarily ban by name/UUID/IP/`#ID`. |
| `tempunban <type> <value>` | Unban by `uuid`, `ip`, or `id`. |
| `tempbans [search]` | List active temporary bans. |
| `mute <target> <period> [reason]` | Mute by `#ID`/UUID. |
| `unmute <target>` | Unmute by `#ID`/UUID. |
| `badge grant <player> <id>` | Grant a badge to a player. |
| `badge revoke <player> <id>` | Revoke a badge from a player. |
| `badge list` | List grantable badges. |

## Configuration

Configuration is split into two TOML files:

- **Server-local**: `<server>/config/xcore.toml` — created automatically on first start if missing.
- **Global/shared**: `secrets.toml` — created automatically in the user's home directory if missing, or in the directory configured by `paths.global_config_directory`.

If legacy `xcconfig.json` or `secrets.json` files are present, XCore migrates them to TOML on startup and keeps backup copies automatically.

### `xcore.toml` (server-local)

`xconfig` reads and writes this file. Prefer TOML-style dotted paths such as `transport.redis.url` or `translation.pipeline`; legacy flat field aliases are still accepted for compatibility. Runtime toggle paths under `runtime.disabled_commands` and `runtime.disabled_features` are intentionally managed by `disable-cmd` / `enable-cmd` and `disable-feature` / `enable-feature` instead of `xconfig`.

| Field | Default | Description |
|-------|---------|-------------|
| `version` | `1` | Config schema version for future migrations. |
| `server.name` | `server` | Server identity name used for transport routing and cross-server recognition. |
| `server.public_host_override` | `""` | Public host/IP override. Leave blank for auto-detect. |
| `server.player_limit` | `30` | Base player slot limit. Admin players do not count toward this limit. |
| `server.console_enabled` | `true` | Whether the server console is enabled. |
| `server.game_started_timer` | `true` | Whether the game-start timer is active. |
| `paths.global_config_directory` | `""` | Override directory for `secrets.toml`. Leave blank to use the user's home directory. |
| `discord.channel_id` | `0` | Discord channel ID for server relay output. |
| `transport.redis.url` | `redis://127.0.0.1:6379` | Redis connection URI for the transport backend. |
| `transport.redis.group_prefix` | `xcore:cg` | Consumer group prefix for Redis streams. |
| `transport.redis.consumer_name` | `xcore-node` | Unique consumer name within the Redis consumer group. |
| `transport.redis.reclaim.enabled` | `true` | Whether to reclaim pending stream messages on start. |
| `transport.redis.reclaim.min_idle_ms` | `15000` | Minimum idle time (ms) before a message is considered orphaned and reclaimed. |
| `transport.redis.reclaim.batch` | `50` | Maximum number of messages to reclaim per batch. |
| `transport.redis.dlq.enabled` | `true` | Whether failed deliveries are sent to a dead-letter queue. |
| `transport.redis.dlq.max_delivery_attempts` | `3` | Maximum delivery attempts before a message is moved to the DLQ. |
| `transport.redis.dlq.prefix` | `xcore:dlq` | Redis key prefix for the dead-letter queue. |
| `runtime.disabled_commands` | `[]` | Command paths disabled at runtime (for example `["rtv"]`). Prefer dedicated toggle commands for editing this list at runtime. |
| `runtime.disabled_features` | `[]` | Disabled feature keys (`rtv`, `vnw`). Prefer dedicated toggle commands for editing this list at runtime. |
| `event_hub.enabled` | `false` | Whether the current map is the event hub. |
| `event_hub.map_id` | `""` | Internal map identifier for the event hub. |

#### Translation settings (`[translation]`, `[translation.cache]`, `[translation.metrics]`, `[translation.llm]`)

| Field | Default | Description |
|-------|---------|-------------|
| `translation.enabled` | `true` | Master switch for the translation pipeline. |
| `translation.pipeline` | `["google"]` | Ordered list of provider IDs to try (for example `["google", "openai"]`). |
| `translation.preserve_original_message_on_failure` | `true` | Whether to send the original untranslated message if all providers fail. |
| `translation.cache.enabled` | `true` | Whether translation results are cached in Redis. |
| `translation.cache.ttl_seconds` | `1800` | Cache entry lifetime in seconds. |
| `translation.cache.max_text_length` | `500` | Maximum text length eligible for caching. |
| `translation.metrics.enabled` | `true` | Whether translation metrics are collected. |
| `translation.metrics.minute_buckets_enabled` | `true` | Whether per-minute metric buckets are maintained. |
| `translation.metrics.minute_bucket_ttl_seconds` | `21600` | TTL for per-minute metric buckets. |
| `translation.llm.preserve_formatting_tokens` | `true` | Whether Mindustry formatting tokens are preserved when sending text to LLM-based providers. |
| `translation.llm.structured_output_required` | `true` | Whether structured JSON output is requested from LLM providers. |
| `translation.llm.max_input_chars` | `500` | Maximum input characters for LLM translation requests. |
| `translation.llm.max_output_chars` | `1200` | Maximum output characters for LLM translation responses. |
| `translation.llm.strip_control_characters` | `true` | Whether control characters are stripped before translation. |

#### IP reputation settings (`[ip_reputation]`)

| Field | Default | Description |
|-------|---------|-------------|
| `ip_reputation.enabled` | `false` | Master switch for IP reputation checks. |
| `ip_reputation.block_proxy` | `true` | Whether proxy connections are blocked. |
| `ip_reputation.block_vpn` | `true` | Whether VPN connections are blocked. |
| `ip_reputation.block_tor` | `true` | Whether Tor exit nodes are blocked. |
| `ip_reputation.block_hosting` | `false` | Whether hosting-provider IP ranges are blocked. |
| `ip_reputation.cache_ttl_seconds` | `3600` | Cache lifetime for IP reputation lookups. |

### `secrets.toml` (global/shared)

This file contains sensitive and shared settings. **It is created automatically** with defaults. The required database settings are `database.mongo_connection_string` and `database.name` under the `[database]` section.

| Field | Default | Description |
|-------|---------|-------------|
| `version` | `1` | Config schema version for future migrations. |
| `database.mongo_connection_string` | `""` | **Required.** MongoDB connection URI (for example `mongodb://localhost:27017`). |
| `database.name` | `""` | **Required.** MongoDB database name (for example `xcore`). |
| `database.read_only` | `false` | When `true`, database writes are suppressed. |
| `database.migration_enabled` | `false` | When `true`, migration logic runs on startup. |
| `external_links.discord_url` | `https://discord.gg/RUMCCa9QAC` | Public Discord invite URL. |
| `external_links.github_url` | `https://github.com/XCore-mindustry/` | Project GitHub URL. |
| `external_links.donatello_url` | `https://donatello.to/xcore` | Donation page URL. |
| `external_links.weblate_url` | `https://xcore.eradication.fun/` | Weblate translation platform URL. |
| `external_links.discord_red_vs_blue_url` | `https://discord.gg/UdnuFetcNt` | Red vs Blue Discord URL. |
| `moderation.votekick.min_play_time_minutes` | `60` | Minimum playtime (minutes) required to start a votekick. |
| `moderation.votekick.ban_duration_minutes` | `30` | Duration of a votekick ban in minutes. |
| `moderation.votekick.vote_duration_seconds` | `60.0` | How long a vote session remains open. |
| `chat.global.min_play_time_minutes` | `240` | Minimum playtime (minutes) required to use global chat (`/g`). |
| `maps.voting.switch_delay_seconds` | `10` | Delay before switching maps after a successful vote. |
| `pagination.events_per_page` | `10` | Events shown per page in the event menu. |
| `pagination.maps_per_page` | `10` | Maps shown per page in the map browser. |
| `pagination.commands_per_page` | `6` | Commands shown per page in the help menu. |
| `pagination.private_messages_per_page` | `10` | Messages shown per page in the inbox. |
| `messages.history.max_history` | `16` | Maximum tracked message history per player. |
| `messages.private.max_length` | `300` | Maximum length of a private message. |
| `messages.private.cooldown_seconds` | `10` | Cooldown between private messages from the same player. |
| `messages.private.unread_limit` | `30` | Maximum unread messages retained. |
| `messages.private.blocked_limit` | `100` | Maximum blocked-player entries retained. |

#### Translation provider config (`[translation.providers.<id>]`)

| Field | Default | Description |
|-------|---------|-------------|
| `translation.providers.<id>.type` | `google` | Provider type (`google`, `openai`, `nvidia`, etc.). |
| `translation.providers.<id>.enabled` | `true` | Whether this provider is active. |
| `translation.providers.<id>.api_key` | `""` | API key for the provider. |
| `translation.providers.<id>.base_url` | `https://api.openai.com/v1` | Base URL for the provider API. |
| `translation.providers.<id>.model` | `gpt-5.4` | Model identifier for provider integrations that need one. |
| `translation.providers.<id>.api_mode` | `""` | Provider-specific API mode. |
| `translation.providers.<id>.organization` | `""` | Optional organization header/value for provider APIs. |
| `translation.providers.<id>.project` | `""` | Optional project header/value for provider APIs. |
| `translation.providers.<id>.timeout_seconds` | `15` | Request timeout. |
| `translation.providers.<id>.max_retries` | `1` | Number of retries on transient failure. |
| `translation.providers.<id>.temperature` | `0.0` | Temperature passed to LLM-style providers when applicable. |
| `translation.providers.<id>.supported_languages` | `[]` | Set/list of language codes this provider supports. |

#### IP reputation provider config (`[ip_reputation.provider]`)

| Field | Default | Description |
|-------|---------|-------------|
| `ip_reputation.provider.base_url` | `http://ip-api.com/json` | Base URL for the IP reputation provider. |
| `ip_reputation.provider.timeout_seconds` | `10` | Request timeout for IP reputation lookups. |
| `ip_reputation.provider.max_retries` | `2` | Number of retries on transient IP reputation failures. |
| `ip_reputation.provider.rate_limit_per_minute` | `45` | Soft per-minute rate limit for provider calls. |

## Localization
Bundles are stored in `src/main/resources/bundles/` and distributed via FluBundle. Supported languages include English, Russian, Ukrainian, and Belarusian. Locale resolution follows player preference with automatic fallback.

## Testing
- Unit and integration tests use JUnit 5 with AssertJ.
- Avaje service tests use `BeanScope.builder().forTesting().mock(...)` setups.
- Run the full suite:
  ```bash
  ./gradlew test
  ```
- Build and package:
  ```bash
  ./gradlew test shadowJar
  ```

## Maven Publishing
- Snapshots are published to `https://maven.x-core.org/snapshots` on every non-PR push via GitHub Actions.
- Releases are published to `https://maven.x-core.org/releases` when a GitHub Release is published.
- Gradle repository names follow the Reposilite pattern: `xcoreRepositorySnapshots` and `xcoreRepositoryReleases`.
- GitHub Actions maps `XCORE_USERNAME` and `XCORE_PASSWORD` to matching Gradle properties.

## License
This project is licensed under the **MIT** License. For more details, see the [LICENSE](LICENSE.txt) file.
