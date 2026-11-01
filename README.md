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

## Releasing

Each component has its own version in its build files (the protocol keeps it in `protocol/pyproject.toml`).
To release one, bump that version and push a tag `<component>/vX.Y.Z`, where component is
`xcore`, `flubundle`, `cloud-mindustry`, `xcore-ui`, `testkit`, `protocol` or `tilelogger`. The release workflow
checks the tag against the build version, publishes Maven components to `maven.x-core.org` and creates the GitHub release. TileLogger versions live in `plugins/tilelogger/plugin.json`; its release runs the native matrix and uploads Linux x64/ARM and Windows x64 jars.

## History

These components used to live in separate repositories, which remain available during the migration. Their history was
imported with paths moved into their directories. Old tags are renamed to `<component>/vX.Y.Z`, and
`#123` in imported commit messages is rewritten to `XCore-mindustry/<old-repo>#123`.
