# XCore

Plugins, libraries and the protocol behind the XCore Mindustry servers.

| Path | What | Published as | License |
|---|---|---|---|
| `plugins/xcore` | The main server plugin | `org.xcore.plugin:XCore-plugin`, GitHub releases | [AGPL-3.0 + plugin exception](plugins/xcore/LICENSE-EXCEPTION) |
| `libs/flubundle` | Fluent localization for Mindustry | `com.ospx:flubundle` | [MPL-2.0](libs/flubundle/LICENSE) |
| `libs/cloud-mindustry` | Cloud command framework integration | `org.xcore:cloud-mindustry` | [MPL-2.0](libs/cloud-mindustry/LICENSE) |
| `libs/xcore-ui` | Server-driven menu UI | `org.xcore:xcore-ui` | [MPL-2.0](libs/xcore-ui/LICENSE) |
| `libs/mindustry-testkit` | Test toolkit and real-client menu renderer | `org.xcore.testkit:{core,ui,fixtures}` | [MPL-2.0](libs/mindustry-testkit/LICENSE) |
| `protocol` | Message spec and generated Java/Python/TypeScript bindings | `org.xcore:xcore-protocol-java`, `xcore-protocol` on PyPI | [MPL-2.0](protocol/LICENSE) |

Everything else in the repository is under the [MPL-2.0](LICENSE).

## Building

Java 25 is required. Everything JVM-based is one Gradle build:

```
./gradlew build                          # all components and their tests
./gradlew :plugins:xcore:shadowJar       # the plugin jar, in plugins/xcore/build/libs
```

Inside this repository the plugin always builds against the libraries from the same commit.

## Releasing

Each component has its own version in its build files (the protocol keeps it in `protocol/pyproject.toml`).
To release one, bump that version and push a tag `<component>/vX.Y.Z`, where component is
`xcore`, `flubundle`, `cloud-mindustry`, `xcore-ui`, `testkit` or `protocol`. The release workflow
checks the tag against the build version, publishes to `maven.x-core.org` and creates the GitHub release.

## History

These components used to live in separate repositories, which are now archived. Their history was
imported with paths moved into their directories. Old tags are renamed to `<component>/vX.Y.Z`, and
`#123` in imported commit messages is rewritten to `XCore-mindustry/<old-repo>#123`.
