# Monorepo migration: wave 4 (web and development stack)

Imports xcore-web at cb6f6847e88b4fb409d0425298fad8c1dcd981a8 into web/ with
its history. The website remains a separate bun project. Its generated contract
comes from protocol/typescript, and Next standalone tracing includes the root.
The Docker runtime starts web/server.js and receives secrets only at runtime.
AGPL-3.0 plus the agreed exception replaces the component's default policy; the
original MIT notice and Mindustry asset credits remain.

The executable ./dev uses devtools/ for code, compose, local.toml and ignored
runtime state. This resolves the original plan's conflicting file/directory
name while retaining its command syntax. The full agreed migration design is
preserved in monorepo-migration.md without private local-file details.

There is one target registry. Plugin metadata determines dependency closure and
load order, with selected satellite paths supplied only by local.toml. Builds
and metadata validation precede staging/runtime changes. Target replacement,
content-based reload, persistent data, config diff/apply/restore, diagnostic
probes and reset confirmation are covered by standard-library tests.

The game image contains no plugin aliases. It copies only staged jars and keeps
live configs when seeds differ. Startup server name/gamemode/auto-start use
runtime environment overrides rather than rewriting TOML. The XCore dev jar has
a stable output task without changing distributed artifact names.

A single Compose definition shares Mongo replica set and Redis between selected
applications. Demo data works without game-provided maps and uses insert-only
upserts in the guarded dev database. Audit seeds contain synthetic identities
and never delete existing collections. No private satellite or tunnel is enabled
by default, and nothing from the old runtime configuration is copied implicitly.

Before merge: web lint/tests/build, real Mongo/Redis web integration, dev unit
checks, and a clean-checkout ./dev up xcore -> ./dev up web smoke must pass.
No production restart, data migration, old-checkout deletion or source-repository
archiving is included in this wave.
