# Local development

Python 3.11+, Docker Compose v2, and Java 25 are required. TileLogger also needs a
C++20 toolchain. `./dev` is the executable; `devtools/` contains its code and files
(the executable and directory cannot both be named `dev`).

`targets.py` is the only plugin/service target registry. Jars are built at exact
paths and their plugin.json files determine hard dependencies. Soft dependencies
are loaded first only when already selected. Satellites are never discovered,
cloned or built unless selected or needed by a hard dependency.

```sh
./dev up xcore
./dev up tilelogger
./dev up web
./dev up xcore bot
./dev reload
./dev logs [service]
./dev console                  # Docker attach; detach with Ctrl-P, Ctrl-Q
./dev status
./dev check [--json] [--timeout 5]
./dev down
./dev reset                    # type RESET to delete this stack's volumes
```

Each checkout gets a project name based on its absolute path, preventing volume
mixups between clones. Ports bind to loopback. Moving the checkout changes the
project name; keep the original directory to continue using its data. Multiple
checkouts cannot bind the same host ports simultaneously.

`up` replaces the active set and stops unneeded services without removing volumes.
`reload` always builds the full dependency closure; unchanged game jars/settings
avoid a game restart. Web/bot/metrics targets rebuild their application services.
Build/metadata failures leave the running stack and staged jars alone. Validated
jars are prepared separately and installed as a complete set; entrypoint copies
only that set into the game's mods directory. One mutation command runs at a time.
After an interrupted process, remove `devtools/.run/lock` only once no command runs.

## Local configuration

Copy `local.example.toml` to `local.toml`. The tool only reads this file. Unknown
keys/types fail before startup; values are not included in errors. Every Compose
invocation receives the same configured OAuth/bot environment, including status,
config commands and invocations that select only game targets.

A ready satellite needs `jar`; a source satellite needs `repo`, `task`, and `jar`.
Paths are relative to local.toml, except a source satellite's jar is relative to
repo. `~` expands normally. Use `mod` when the dependency's mod name differs from
the configured key. Matching follows Mindustry: lowercase, spaces become hyphens.
Conflicting game overrides in selected satellites fail validation.

OAuth uses the callback `<web.origin>/api/auth/discord/callback`. Without OAuth
credentials, public pages still work. With no bot token, the bot image idles.
A configured bot token requires a guild_id of 5 to 25 digits. Default dev
permissions have no staff grants/bindings. The generated guild seed is shared
with the bot. Live game permission
files are preserved, so changing guild after initialization requires updating the
live permission file as well; it is not silently overwritten.

## Persistent config

```sh
./dev config diff xcore
./dev config apply xcore [--source /path/to/xcore.toml]
./dev config restore xcore <backup-id>
```

The existing xcore/sentinel/secrets validation and atomic backup/restore contract
is preserved. Config diffs show keys, not values. Applying/restoring a change
restarts only the game; database volumes and saves remain. Unchanged config does
not restart. Runtime game overrides come from local.toml/environment and do not
rewrite the live xcore.toml file. A seed mismatch warning keeps operator edits.

## Seeds and tests

The demo seed accepts only the xcore_dev database, works without registered game
maps, and uses stable insert-only upserts. Audit seed uses synthetic identities,
never deletes a collection, and is idempotent. Nothing uses private default
satellites, external tunnels or the former accounts stack.

```sh
python3 -m unittest discover -s devtools/tests -v
```

`check` verifies running/selected services, Mongo replica-set primary and an
aborted transaction, Redis ping, the game socket and HTTP readiness endpoints.
Raw Docker/config parser errors are hidden because they can contain secrets.
Use service logs deliberately when diagnosing an application startup failure.
