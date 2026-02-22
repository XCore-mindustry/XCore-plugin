# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

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

[Unreleased]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.5...HEAD
[3.0.5]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.4...3.0.5
[3.0.4]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.3...3.0.4
[3.0.3]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.2...3.0.3
[3.0.2]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.1...3.0.2
[3.0.1]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.0...3.0.1
[3.0.0]: https://github.com/XCore-mindustry/XCore-plugin/compare/2.9.0...3.0.0
