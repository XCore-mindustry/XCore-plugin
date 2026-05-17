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
- Mindustry v157 (or compatible forks)
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
5. Configure your MongoDB and Redis connections in `<server>/config/xcconfig.json` (server-specific) and `~/secrets.json` (global/shared).

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
| `xconfig` | Show current XCore configuration. |
| `xconfig <field> <value>` | Edit a configuration field. |
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

Configuration is split into two files:

- **Server-specific**: `<server>/config/xcconfig.json` — created automatically on first start if missing.
- **Global/shared**: `~/secrets.json` — created automatically in the user's home directory if missing. Holds sensitive and shared settings such as the MongoDB connection string.

### `xcconfig.json` (server-specific)

| Field | Default | Description |
|-------|---------|-------------|
| `server` | `server` | Server identity name used for transport routing and cross-server recognition. |
| `discord_channel_id` | `0` | Discord channel ID for server relay output. |
| `redis_url` | `redis://127.0.0.1:6379` | Redis connection URI for the transport backend. |
| `redis_group_prefix` | `xcore:cg` | Consumer group prefix for Redis streams. |
| `redis_consumer_name` | `xcore-node` | Unique consumer name within the Redis consumer group. |
| `redis_reclaim_enabled` | `true` | Whether to reclaim pending stream messages on start. |
| `redis_reclaim_min_idle_ms` | `15000` | Minimum idle time (ms) before a message is considered orphaned and reclaimed. |
| `redis_reclaim_batch` | `50` | Maximum number of messages to reclaim per batch. |
| `redis_dlq_enabled` | `true` | Whether failed deliveries are sent to a dead-letter queue. |
| `redis_max_delivery_attempts` | `3` | Maximum delivery attempts before a message is moved to the DLQ. |
| `redis_dlq_prefix` | `xcore:dlq` | Redis key prefix for the dead-letter queue. |
| `console_enabled` | `true` | Whether the server console is enabled. |
| `player_limit` | `30` | Base player slot limit. Admin players do not count toward this limit. |
| `global_config_directory` | `null` | Override directory for the global `secrets.json`. If `null`, the user's home directory is used. |
| `game_started_timer` | `true` | Whether the game-start timer is active. |
| `disabled_commands` | `[]` | List of command paths disabled at runtime (e.g., `["rtv"]`). |
| `disabled_features` | `[]` | List of disabled feature keys (`rtv`, `vnw`). |
| `is_event_hub_map` | `false` | Whether the current map is the event hub. |
| `event_hub_map_id` | `""` | Internal map identifier for the event hub. |
| `translation` | (see below) | Translation pipeline settings. |

#### Translation settings (`translation` object)

| Field | Default | Description |
|-------|---------|-------------|
| `enabled` | `true` | Master switch for the translation pipeline. |
| `pipeline` | `["google"]` | Ordered list of provider IDs to try (e.g., `["google", "openai"]`). |
| `preserve_original_message_on_failure` | `true` | Whether to send the original untranslated message if all providers fail. |
| `cache.enabled` | `true` | Whether translation results are cached in Redis. |
| `cache.ttl_seconds` | `1800` | Cache entry lifetime in seconds. |
| `cache.max_text_length` | `500` | Maximum text length eligible for caching. |
| `metrics.enabled` | `true` | Whether translation metrics are collected. |
| `metrics.minute_buckets_enabled` | `true` | Whether per-minute metric buckets are maintained. |
| `metrics.minute_bucket_ttl_seconds` | `21600` | TTL for per-minute metric buckets. |
| `llm.preserve_formatting_tokens` | `true` | Whether Mindustry formatting tokens are preserved when sending text to LLM-based providers. |
| `llm.structured_output_required` | `true` | Whether structured JSON output is requested from LLM providers. |
| `llm.max_input_chars` | `500` | Maximum input characters for LLM translation requests. |
| `llm.max_output_chars` | `1200` | Maximum output characters for LLM translation responses. |
| `llm.strip_control_characters` | `true` | Whether control characters are stripped before translation. |

### `~/secrets.json` (global)

This file contains sensitive and shared settings. **It is created automatically** with defaults; the only required fields are `mongo_connection_string` and `database_name`.

| Field | Default | Description |
|-------|---------|-------------|
| `mongo_connection_string` | `null` | **Required.** MongoDB connection URI (e.g., `mongodb://localhost:27017`). |
| `database_name` | `null` | **Required.** MongoDB database name (e.g., `xcore`). |
| `discord_url` | `https://discord.gg/RUMCCa9QAC` | Public Discord invite URL. |
| `github_url` | `https://github.com/XCore-mindustry/` | Project GitHub URL. |
| `donatello_url` | `https://donatello.to/xcore` | Donation page URL. |
| `weblate_url` | `https://xcore.eradication.fun/` | Weblate translation platform URL. |
| `discord_red_vs_blue_url` | `https://discord.gg/UdnuFetcNt` | Red vs Blue Discord URL. |
| `min_play_time_for_votekick` | `60` | Minimum playtime (minutes) required to start a votekick. |
| `min_play_time_for_global_chat` | `240` | Minimum playtime (minutes) required to use global chat (`/g`). |
| `vote_kick_ban_duration_minutes` | `30` | Duration of a votekick ban in minutes. |
| `vote_duration_seconds` | `60.0` | How long a vote session remains open. |
| `map_switch_delay_seconds` | `10` | Delay before switching maps after a successful vote. |
| `events_per_page` | `10` | Events shown per page in the event menu. |
| `maps_per_page` | `10` | Maps shown per page in the map browser. |
| `commands_per_page` | `6` | Commands shown per page in the help menu. |
| `private_messages_per_page` | `10` | Messages shown per page in the inbox. |
| `max_history` | `16` | Maximum tracked message history per player. |
| `private_message_max_length` | `300` | Maximum length of a private message. |
| `private_message_cooldown_seconds` | `10` | Cooldown between private messages from the same player. |
| `private_message_unread_limit` | `30` | Maximum unread messages retained. |
| `private_message_blocked_limit` | `100` | Maximum blocked-player entries retained. |
| `is_data_base_read_only` | `false` | When `true`, database writes are suppressed. |
| `is_data_base_migration` | `false` | When `true`, migration logic runs on startup. |
| `translation_providers` | `{ "google": { ... } }` | Map of provider configurations keyed by provider ID. |

#### Translation provider config (`translationProviders.<id>`)

| Field | Default | Description |
|-------|---------|-------------|
| `type` | `google` | Provider type (`google`, `openai`, `nvidia`, etc.). |
| `enabled` | `true` | Whether this provider is active. |
| `api_key` | `null` | API key for the provider. |
| `base_url` | `https://api.openai.com/v1` | Base URL for the provider API. |
| `api_mode` | `null` | Provider-specific API mode. |
| `timeout_seconds` | `15` | Request timeout. |
| `max_retries` | `1` | Number of retries on transient failure. |
| `supported_languages` | `[]` | Set of language codes this provider supports. |

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
