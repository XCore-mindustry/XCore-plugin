<div align="center">

<img src="web/src/app/icon.png" width="96" alt="">

# XCore

The plugins, libraries, services and website behind the XCore Mindustry servers.

[![CI](https://github.com/XCore-mindustry/xcore/actions/workflows/ci.yml/badge.svg)](https://github.com/XCore-mindustry/xcore/actions/workflows/ci.yml)
[![XCore release](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.x-core.org%2Freleases%2Forg%2Fxcore%2Fplugin%2FXCore-plugin%2Fmaven-metadata.xml&label=XCore)](https://github.com/XCore-mindustry/xcore/releases)
[![Discord](https://img.shields.io/discord/1058023472661549176?label=Discord&logo=discord&logoColor=white&color=5865F2)](https://discord.gg/RUMCCa9QAC)
[![Translations](https://img.shields.io/badge/translate-Weblate-2ECCAA)](https://weblate.x-core.org/)

[Website](https://x-core.org) · [Discord](https://discord.gg/RUMCCa9QAC) · [Releases](https://github.com/XCore-mindustry/xcore/releases) · [Maven](https://maven.x-core.org)

</div>

## What's here

### Server plugins

| Plugin | |
|---|---|
| [**XCore**](plugins/xcore) | The main plugin: player profiles and ratings, cross-server chat, moderation, map voting, badges, Discord linking and the in-game menus. |
| [**TileLogger**](plugins/tilelogger) | Tile history, player activity evidence and rollback, backed by a native store. Runs alongside XCore. |

### Libraries

Published to [maven.x-core.org](https://maven.x-core.org). Usable in any Mindustry plugin, not only XCore.

| Library | Coordinates | Latest | |
|---|---|---|---|
| [FluBundle](libs/flubundle) | `com.ospx:flubundle` | ![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.x-core.org%2Freleases%2Fcom%2Fospx%2Fflubundle%2Fmaven-metadata.xml&label=) | Localization with [Project Fluent](https://projectfluent.org): plurals, numbers and dates per locale |
| [cloud-mindustry](libs/cloud-mindustry) | `org.xcore:cloud-mindustry` | ![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.x-core.org%2Freleases%2Forg%2Fxcore%2Fcloud-mindustry%2Fmaven-metadata.xml&label=) | [Incendo Cloud](https://github.com/Incendo/cloud) commands for Mindustry |
| [xcore-ui](libs/xcore-ui) | `org.xcore:xcore-ui` | ![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.x-core.org%2Freleases%2Forg%2Fxcore%2Fxcore-ui%2Fmaven-metadata.xml&label=) | Type-safe, reactive server-driven menus |
| [mindustry-testkit](libs/mindustry-testkit) | `org.xcore.testkit:core`, `:ui`, `:fixtures` | ![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.x-core.org%2Freleases%2Forg%2Fxcore%2Ftestkit%2Fui%2Fmaven-metadata.xml&label=) | Test helpers, plus a renderer that opens menus in the real game client |
| [protocol](protocol) | `org.xcore:xcore-protocol-java`, PyPI `xcore-protocol` | ![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fmaven.x-core.org%2Freleases%2Forg%2Fxcore%2Fxcore-protocol-java%2Fmaven-metadata.xml&label=) | The message spec the servers, bot, gateway and website share, with generated Java, Python and TypeScript |

```kotlin
repositories {
    maven("https://maven.x-core.org/releases")
}

dependencies {
    implementation("com.ospx:flubundle:<version>")
}
```

### Services

| Service | |
|---|---|
| [Discord bot](services/discord-bot) | Links the game servers with the community Discord: chat bridge, moderation, account linking |
| [Metrics gateway](services/metrics-gateway) | Collects server telemetry for Prometheus and Grafana |
| [Website](web) | [x-core.org](https://x-core.org): player profiles, leaderboards, match history and Discord sign-in, in English, Ukrainian and Russian |

## Running a server

Take the jars from [Releases](https://github.com/XCore-mindustry/xcore/releases) and put them into the server's `config/mods`. The server must run Mindustry v160 on Java 25. XCore also needs MongoDB 6.0+ running as a replica set (it uses transactions) and Redis. TileLogger ships one jar per platform: Linux x64, Linux ARM64 and Windows x64.

Configuration is covered in each plugin's README: [XCore](plugins/xcore/README.md), [TileLogger](plugins/tilelogger/README.md).

## Development

You need Java 25, Docker Compose v2 and Python 3.11+. TileLogger's native part also needs a C++20 compiler, and the website needs [Bun](https://bun.sh).

The quickest way to try a change is `./dev`. Name what you are working on, and it builds that and starts it together with everything it depends on:

```sh
./dev up xcore         # game server with XCore, MongoDB and Redis
./dev up tilelogger    # TileLogger; XCore gets built too, since TileLogger needs it
./dev up web           # the website with demo data, no game server
./dev reload           # rebuild; the server restarts only if a jar changed
./dev down             # stop everything, keep the data
```

Settings such as Discord OAuth or paths to plugins from other repositories go into `devtools/local.toml`, which is not tracked. The [development guide](devtools/README.md) covers the rest.

Building directly:

```sh
./gradlew build                            # every JVM component, with tests
./gradlew :plugins:xcore:shadowJar         # plugins/xcore/build/libs/XCore-plugin-*.jar
./gradlew :plugins:tilelogger:jarRelease   # TileLogger for this platform

uv sync --locked --all-packages --all-groups --all-extras   # protocol, bot and gateway
cd web && bun install && bun run dev
```

Inside this repository everything builds against the code in the same commit: a change to a library is immediately what XCore uses.

## Repository layout

```
plugins/
  xcore/              main server plugin
  tilelogger/         tile history plugin, with native/ code
libs/
  flubundle/          localization
  cloud-mindustry/    commands
  xcore-ui/           menus
  mindustry-testkit/  test helpers and menu renderer
protocol/             message spec and generated bindings
services/
  discord-bot/
  metrics-gateway/
web/                  x-core.org
devtools/             the ./dev command, Compose stack and seed data
ops/                  production units and the deploy script
build-logic/          shared Gradle conventions
docs/                 notes on the monorepo migration
```

One Gradle build covers the JVM code (`settings.gradle.kts`), and one uv workspace covers the Python code (`pyproject.toml`).

## Releasing

Every component has its own version. To release one, bump the version, merge, and push a tag:

| Component | Version lives in | Tag |
|---|---|---|
| XCore | `plugins/xcore/build.gradle.kts` | `xcore/vX.Y.Z` |
| TileLogger | `plugins/tilelogger/plugin.json` | `tilelogger/vX.Y.Z` |
| FluBundle | `libs/flubundle/build.gradle` | `flubundle/vX.Y.Z` |
| cloud-mindustry | `libs/cloud-mindustry/build.gradle.kts` | `cloud-mindustry/vX.Y.Z` |
| xcore-ui | `libs/xcore-ui/build.gradle.kts` | `xcore-ui/vX.Y.Z` |
| mindustry-testkit | `libs/mindustry-testkit/build.gradle.kts` | `testkit/vX.Y.Z` |
| protocol | `protocol/pyproject.toml` (Java and Python share it) | `protocol/vX.Y.Z` |

The release workflow checks that the tag matches the version, publishes to Maven (and PyPI for the protocol), and creates the GitHub release with the jars. The bot, the gateway and the website are not released; they are deployed from `main` with [`ops/deploy`](ops/README.md).

## Contributing

Issues and pull requests go to this repository. A pull request may touch several components at once. CI runs only the checks for what changed, and `ci-ok` sums them up.

Translations are done on [Weblate](https://weblate.x-core.org/).

## License

| | License |
|---|---|
| XCore, Discord bot, metrics gateway, website | [AGPL-3.0](plugins/xcore/LICENSE), with an [exception](plugins/xcore/LICENSE-EXCEPTION) for separate plugins that use XCore's API |
| TileLogger | [GPL-3.0](plugins/tilelogger/LICENSE) |
| Libraries, protocol, everything else | [MPL-2.0](LICENSE) |

Each component has its own `LICENSE`. Code contributed while a component was under the MIT License keeps that notice in `LICENSE.MIT`.

## History

Until November 2026 these components lived in separate repositories, which are archived as their migration completes. Their history was imported with each project moved into its directory: old tags are renamed to `<component>/vX.Y.Z`, and `#123` in imported commit messages points to `XCore-mindustry/<old-repo>#123`. XCore's own history and tags are unchanged. Details are in [docs/monorepo-migration.md](docs/monorepo-migration.md).
