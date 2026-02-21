# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

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

[Unreleased]: https://github.com/XCore-mindustry/XCore-plugin/compare/3.0.0...HEAD
[3.0.0]: https://github.com/XCore-mindustry/XCore-plugin/compare/2.9.0...3.0.0
