# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

No changelog entries were recorded in the Unreleased section.

## [4.1.0] - 2026-05-24

### Changed
- Updated Mindustry to version 158.

## [4.0.0] - 2026-05-17

### Added
- Added actor moderation history views with actor identity capture on unban and unmute flows.
- Added a paginated `/top` leaderboard menu with top-rank highlighting, viewer position context, and Redis-backed cursor caching.
- Added a player-color badge symbol mode so badge displays can follow the player's active color styling.
- Added mobile-friendly reset actions for event names and player nicknames.

### Changed
- **BREAKING** Migrated plugin transport to the canonical `xcore-protocol` model and switched Redis routing to the shared protocol route catalog.
- **BREAKING** Replaced the deprecated socket event layer and removed the legacy compatibility surface for older transport integrations.
- Refactored startup coordination, logging, and menu runtime architecture, including declarative menu flows, namespaced flow actions, and refreshed UI flow contracts.
- Aligned CI and release validation with packaged artifacts and refreshed README and architecture documentation to reflect the current implementation.

### Fixed
- Fixed map-vote diagnostics and legacy map identity collisions, including an audit path for affected persisted records and votes.
- Fixed `/top` first-page navigation null handling and improved muted-player feedback when chat or private messages are blocked.
- Fixed null-safety and resource-leak issues across ingress, vote, RPC, and session runtime paths.
- Fixed MiniHexed reset flow, Hexed rank progress display and ordinal safety, player profile stat accuracy, stats tracking, badge menu text layout, localization parity, and startup host resolution/bootstrap hardening.

## [3.2.1] - 2026-04-12

### Added
- Added wave-vote flow for map progression and surfaced map ratings through the `maps.list` RPC.
- Added moderation audit history plus chat-visibility settings for player UX and admin parity.

### Changed
- Updated Mindustry to version 157.
- Refactored transport and translation services to further decompose runtime infrastructure.

### Fixed
- Fixed veteran badge icon rendering to use the overdrive icon.
- Fixed no-op chat translation delivery, cached-session iteration safety, and translation provider footprint.

## [3.2] - 2026-03-28

### Added
- Added a configurable translation pipeline with ordered fallback providers and an OpenAI-compatible provider path.
- Added translation cache, metrics, safety checks, debug logging, and a translation stats command for runtime visibility.
- Added translation delivery for team chat (`/t`) plus a compatibility-aware delivery path for likely Foo's Client users.
- Added reusable cached team-session helpers so team-aware features can stop duplicating online-player traversal.
- Added successful vote-kick event publishing for downstream consumers.
- Added a Discord link-code copy action for Mindustry 156.1 clients.
- Added a bug finder badge and localized badge copy updates.
- Added localization placeholder consistency coverage and user language preference tests.

### Changed
- Reworked localization internals around FluBundle locale resolvers, direct bundle injection, and unified language update flow.
- Improved translation pipeline behavior to better support NVIDIA Integrate / chat-completions-compatible providers.
- Automated release publishing to XCore Maven / Reposilite and aligned dependency resolution for `cloud-mindustry` and `flubundle` with XCore Maven.
- Redesigned moderation ban and mute notices with clearer localized presentation.

### Fixed
- Fixed vote choice parser aliases so only explicit yes/no inputs are accepted during voting.
- Fixed standard Cloud caption localization coverage and added missing placeholder support for bundled captions.
- Fixed player-menu PID lookup so online players are shown correctly.
- Fixed persisted map resolution to use exact persisted identity when opening details.
- Fixed Redis RPC response metric timing so observers do not see stale values after successful replies.
- Fixed cached session lookup edge cases across gameplay, menu, and voting flows.
- Fixed translation provider ordering so configured providers are used in the intended sequence.
- Fixed help command/controller lifecycle regressions affecting server and client help flows.

## [3.1.8] - 2026-03-13

No changelog entries were recorded in the Unreleased section.

## [3.1.7] - 2026-03-11

### Added
- Added a fallback temporary-ban moderation menu to keep admin workflows available when the primary path is unavailable.
- Added aggregated per-mode match statistics and expanded tests for game data, map rotation, moderation, and admin integration flows.

### Changed
- Renamed the MongoDB match-history collection from `games` to `games_v2` in code for future writes and queries.
- Reworked dependency injection wiring around Avaje factories and cleaned up duplicated service logic.

### Fixed
- Fixed ban persistence handling and several moderation edge cases.
- Fixed active event-map reload behavior and support for hyphenated restart event names.
- Fixed English compact stats menu text and aligned newer match-tracking behavior.

## [3.1.6] - 2026-03-10

### Added
- Redesigned the player statistics menu with a richer profile view, including account creation date, formatted playtime, PvP rating, and aggregated lifetime match stats.
- Added Hexed progression details to the player profile, including current rank, points, progress to the next rank, and max-rank state.
- Added a full badge browser so players can view all badges with their descriptions and current unlock or active state.
- Added admin and console `set-team` command support for moving online players between teams.
- Added a console `set-gamemode` command for runtime server administration.
- Added localized strings for the expanded player menu, playtime formatting, badge states, and translator or language selection in English, Russian, Ukrainian, and Belarusian.

### Changed
- Improved badge rendering by switching badge icons to Mindustry `Iconc` glyphs for more consistent display.
- Expanded badge browsing from owned badges only to a discoverable list with locked, unlocked, and active state visibility.
- Improved observer flow so the command clears the current unit before moving the player into spectator state.
- Reworked player and map persistence to use targeted partial database updates instead of frequent full-document saves.

### Fixed
- Fixed the `observer` command behavior.
- Fixed server-side `set-gamemode` command handling.
- Fixed reliability issues when persisting partial player data updates.
- Fixed player IP and nickname connection updates so both values are stored together when needed.
- Fixed badge grant or revoke persistence so active badge and unlocked badge state remain synchronized.
- Fixed admin confirmation and removal synchronization so in-game admin state and display refresh correctly after moderation socket events.
- Fixed private-message block and unblock persistence flow to match the new session update behavior.

### Tests
- Added coverage for connection handling, moderation socket admin flows, session partial-update helpers, aggregated player stats, and localized menu formatting.
- Updated existing tests for private messages, Redis stream routing, player display behavior, and maintain-controller command behavior.

## [3.1.5] - 2026-03-07

### Added
- Added persistent private messages with inbox/reply/block flows and cross-server delivery.
- Added unlockable player badges with player selection, server/admin management commands, and localized badge metadata.
- Added dedicated translator and map-maker badges alongside the initial badge set.

### Changed
- Redesigned chat badge rendering so official badges are shown separately from player-controlled nicknames.

### Fixed
- Reduced badge spoofing by blocking reserved badge glyphs in custom nicknames.
- Fixed admin badge refresh/state synchronization across login, logout, socket approval, and sync flows.

## [3.1.4] - 2026-03-06

### Added
- Routed moderation mute events into a dedicated Redis stream so downstream services can consume mute actions directly.

### Fixed
- Restored Discord mute log delivery by publishing `MuteData` as `moderation.mute` instead of falling back to raw events.
- Fixed release packaging to include root module classes and runtime dependencies in the release jar.
- Aligned votekick localization target arguments with the current vote flow.
- Corrected mute/help localization placeholders and added consistency coverage for localization bundles.

## [3.1.3] - 2026-03-05

### Added
- Added heartbeat public address propagation (`host:port`) for cross-service visibility.

### Fixed
- Fixed `gcmd` broadcast delivery so `ExecuteCommand` is consumed by every server, not only the first consumer.
- Blocked votekick targets from voting on their own kick via chat shortcuts (`y`/`n`).

## [3.1.2] - 2026-03-03

### Added
- Added a `disabledFeatures` configuration/control flow to block RTV from both menu and command paths.

### Changed
- Removed legacy transport cutover commands/config and additional unused global config fields.
- Updated Ukrainian translations from Weblate.

### Fixed
- Delivered Discord channel messages to the correct target server.
- Enforced vanilla nickname length limit for custom nicknames.
- Fixed mute bypass in `/t` and `/g` commands.

### Tests
- Added an integration test for `@RequiresMuteCheck` post-processor handling.

## [3.1.1] - 2026-03-01

### Fixed
- Eliminated an RPC response-listener startup race in `RedisNetworkBackend` that could cause intermittent request/response timeouts in CI.

## [3.1.0] - 2026-03-01

### Changed
- Migrated networking internals to Redis and removed legacy Sock/Discord pathways.
- Migrated root module dependency declarations to the Gradle version catalog.

### Fixed
- Added additional null-safety checks in runtime paths.

### Tests
- Added and expanded Avaje-powered and unit test coverage for voting, moderation, ingress, and repository/security logic.

## [3.0.5] - 2026-02-22

### Fixed
- Fixed version checks in `getCachedAdminTools` by using string-based comparison.
- Added a null-check for missing sessions in the `MiniHexedService` update loop.

## [3.0.4] - 2026-02-22

### Added
- Added custom nickname and description support for players.
- Added a reset-nickname command.

### Changed
- Updated Gradle to 9.3.1.

### Fixed
- Changed the default `pid` to `-1` so `0` remains a valid player ID.

## [3.0.3] - 2026-02-22

### Changed
- Removed the legacy `init()` method path.
- Improved the translation system behavior.

### Fixed
- Fixed a null-related runtime bug.

## [3.0.2] - 2026-02-22

### Fixed
- Fixed additional stability bugs.

## [3.0.1] - 2026-02-21

### Changed
- Updated `README.md`.
- Updated Weblate branch configuration to `main`.
- Synced Russian translations from Weblate.

### Fixed
- Fixed a database issue with `local_language`.

## [3.0.0] - 2026-02-21

### Added
- Migrated to dependency injection with avaje-inject and exposed `BeanScope` for dependent plugins.
- Introduced database migration infrastructure, including atomic migrations and initial schema/data migrations.
- Added command-system upgrades: Cloud framework integration, JLine suggestions, and disabled-command controls.
- Expanded game and admin UI with settings, main/help menus, player list, map GUI, and additional menu flow improvements.
- Added event and gameplay features, including event system enhancements and statistics collection.
- Added explicit MongoDB configuration requirements and server mapping support via `BiMap`.
- Added Weblate integration (`weblate.yaml`) and large translation updates (Russian, Ukrainian, Belarusian, Polish).
- Added release-oriented build optimization task (`shadowJarRelease`).

### Changed
- Major architecture refactor across the plugin: package restructuring and decomposition into smaller services/handlers.
- Replaced the old `DatabaseService` model with repositories and session-oriented data access.
- Reworked localization flow to session-based locale resolution and broader bundle-driven messaging.
- Reworked Discord, socket, and plugin-event handling into modular components.
- Updated runtime/tooling baseline to Java 25, Mindustry 155.4, and avaje-inject 12.3.
- Improved moderation and time handling semantics (`Instant` to `Duration`, seconds support, optional negative durations).
- Improved command/controller lifecycle management and automated command controller discovery.

### Fixed
- Fixed localization consistency and formatting issues across multiple bundles, including fallback-to-English behavior.
- Fixed startup/help regressions, including `HelpMenu` initialization timing issues.
- Fixed voting and moderation bugs (RTV/event vote flow, `VoteKick` time conversion, temporary-state checks).
- Fixed multiple repository/data handling edge cases, null-safety issues, and parse-time handling bugs.
- Improved shutdown reliability by closing the MongoDB client on plugin shutdown.

### Security
- Added ingress-based connection verification to harden request entry points.

[Unreleased]: https://github.com/XCore-mindustry/XCore-plugin/compare/4.1.0...HEAD
[4.1.0]: https://github.com/XCore-mindustry/XCore-plugin/compare/4.0.0...4.1.0
[4.0.0]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.2.1...4.0.0
[3.2.1]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.2...3.2.1
[3.2]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.8...3.2
[3.1.8]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.7...3.1.8
[3.1.7]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.6...3.1.7
[3.1.6]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.5...3.1.6
[3.1.5]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.4...3.1.5
[3.1.4]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.3...3.1.4
[3.1.3]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.2...3.1.3
[3.1.2]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.1...3.1.2
[3.1.1]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.1.0...3.1.1
[3.1.0]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.7...3.1.0
[3.0.5]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.4...3.0.5
[3.0.4]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.3...3.0.4
[3.0.3]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.2...3.0.3
[3.0.2]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.1...3.0.2
[3.0.1]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.0...3.0.1
[3.0.0]: https://github.com/XCore-mindustry/XCore-plugin/compare/2.9.0...3.0.0
