# Monorepo migration: wave 2 (TileLogger)

Imports TileLogger master at `cc297bcff6e15bdd3b2f3fd312e347939b075d09`
into `plugins/tilelogger` with its full branch history. Bare issue references are
qualified as `XCore-mindustry/TileLogger#N`; historical tags become
`tilelogger/v2.1.0` and `tilelogger/v2.1.1` locally. They must not be pushed as
new release events. The original repository stays available until the first
successful release from the monorepo. Its GPL-3.0 license is preserved.

TileLogger and its native subproject join the root Gradle build and catalog.
Production dependencies on XCore, FluBundle and cloud-mindustry, and test
fixtures use project dependencies. The plugin's version comes from plugin.json.
Native sources, Java sources, protocol and archive formats are unchanged.

The ordinary XCore jar now has the `plain` classifier so project consumers do
not collide with the shadow task's output. The distributed shadow jars keep
their existing names and contents.

The root CI calls a reusable native workflow for Linux x64, Linux ARM and
Windows x64. It retains native sanitizer checks and the native/JMH smoke tests.
The same workflow is called by `tilelogger/vX.Y.Z` releases, which upload named
platform jars instead of publishing TileLogger to Maven. `ci-ok` includes the
native workflow result. Path filters avoid the matrix for unrelated changes.

Benchmark scripts run from the monorepo root and find their component directory
from the script location, including when comparing against rewritten history.
Historical benchmark result documents are retained as historical instructions.

Before merge: require all native matrix entries, sanitizer checks and `ci-ok`.
No runtime deployment or repository archiving is part of this wave.
