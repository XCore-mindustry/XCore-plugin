# XCore

Plugins, libraries and the protocol behind the XCore Mindustry servers.

| Path | What | Published as | License |
|---|---|---|---|
| `plugins/xcore` | The main server plugin | `org.xcore.plugin:XCore-plugin`, GitHub releases | [AGPL-3.0 + plugin exception](plugins/xcore/LICENSE-EXCEPTION) |
| `plugins/tilelogger` | Tile history and rollback with native storage | Platform jars on GitHub releases | [GPL-3.0](plugins/tilelogger/LICENSE) |
| `libs/flubundle` | Fluent localization for Mindustry | `com.ospx:flubundle` | [MPL-2.0](libs/flubundle/LICENSE) |
| `libs/cloud-mindustry` | Cloud command framework integration | `org.xcore:cloud-mindustry` | [MPL-2.0](libs/cloud-mindustry/LICENSE) |
| `libs/xcore-ui` | Server-driven menu UI | `org.xcore:xcore-ui` | [MPL-2.0](libs/xcore-ui/LICENSE) |
| `libs/mindustry-testkit` | Test toolkit and real-client menu renderer | `org.xcore.testkit:{core,ui,fixtures}` | [MPL-2.0](libs/mindustry-testkit/LICENSE) |
| `services/discord-bot` | Discord transport and commands | Docker image / uv entrypoint | [AGPL-3.0 + plugin exception](services/discord-bot/LICENSE-EXCEPTION) |
| `services/metrics-gateway` | Prometheus telemetry gateway | Docker image / uv entrypoint | [AGPL-3.0 + plugin exception](services/metrics-gateway/LICENSE-EXCEPTION) |
| `web` | Website and player accounts | Standalone Next.js image | [AGPL-3.0 + plugin exception](web/LICENSE-EXCEPTION) |
| `protocol` | Message spec and generated Java/Python/TypeScript bindings | `org.xcore:xcore-protocol-java`, `xcore-protocol` on PyPI | [MPL-2.0](protocol/LICENSE) |

Everything else in the repository is under the [MPL-2.0](LICENSE).

## Building

Java 25 and a C++20 toolchain are required for the full build. Everything JVM-based is one Gradle build:

```
./gradlew build                          # all components and their tests
./gradlew :plugins:xcore:shadowJar       # the XCore jar, in plugins/xcore/build/libs
./gradlew :plugins:tilelogger:jarRelease # TileLogger jar with the host native library
```

Inside this repository the plugin always builds against the libraries from the same commit.

## Python workspace

Protocol, the Discord bot and metrics-gateway share one `uv.lock` and use the
protocol from the same checkout. Python 3.14 is the development default; protocol
is also checked on 3.11, and gateway on 3.13 for existing deployments.

```sh
uv sync --locked --all-packages --all-groups --all-extras
uv run --package xcore-discord-bot --env-file /path/to/bot.env xcore-discord-bot
uv run --package xcore-metrics-gateway --env-file /path/to/gateway.env xcore-metrics-gateway
```

Service Dockerfiles use the repository root as build context. Existing service
cutover and separate runtime environments are described in
[the wave 3 guide](docs/monorepo-wave3.md).

## Development stack

```sh
./dev up xcore             # build and run the server plus Mongo/Redis
./dev up tilelogger        # resolve and build XCore automatically
./dev up web               # website and demo data, without the game server
./dev reload               # rebuild the active set; restart game only on changes
./dev check --json
./dev down                 # keep volumes
```

Configuration is optional: copy `devtools/local.example.toml` to
`devtools/local.toml` for OAuth, bot credentials or explicit satellite paths.
Targets replace the previous selection; databases, saves and live configs persist.
`./dev reset` requires confirmation before deleting volumes. See
[the development guide](devtools/README.md) for config backups and satellite builds.

## Releasing

Each component has its own version in its build files (the protocol keeps it in `protocol/pyproject.toml`).
To release one, bump that version and push a tag `<component>/vX.Y.Z`, where component is
`xcore`, `flubundle`, `cloud-mindustry`, `xcore-ui`, `testkit`, `protocol` or `tilelogger`. The release workflow
checks the tag against the build version, publishes Maven components to `maven.x-core.org` and creates the GitHub release. TileLogger versions live in `plugins/tilelogger/plugin.json`; its release runs the native matrix and uploads Linux x64/ARM and Windows x64 jars.

## History

These components used to live in separate repositories, which remain available during the migration. Their history was
imported with paths moved into their directories. Old tags are renamed to `<component>/vX.Y.Z`, and
`#123` in imported commit messages is rewritten to `XCore-mindustry/<old-repo>#123`.
