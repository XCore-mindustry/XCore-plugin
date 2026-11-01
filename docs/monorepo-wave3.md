# Monorepo migration: wave 3 (Python services)

Imports the main histories of XCore-discord-bot at
`cbdaf95d226ad4c69de0c53a12a1cdedc1790fde` and xcore-metrics-gateway at
`95399df53033e8f8b5a2d9c8929a24330d7f305a`. Issue references are qualified with
the original repository names. The source repositories remain available.

Protocol, bot and gateway share a root uv workspace and lock. Services consume
`xcore-protocol` through `workspace = true`. Existing third-party locked versions
are retained; only protocol changes from 0.17.0 to the local 0.17.1 package.
Service entrypoint names and runtime behavior stay unchanged. Each component
retains its own pytest/lint configuration and the bot's existing mypy baseline.
The bot/plugin document fixture equality check now runs unconditionally.

CI checks generation and compatibility on Python 3.11, service tests on 3.14,
real disposable MongoDB/Redis backends, and both Docker builds/runtime imports.
The existing gateway formatter warning remains a warning. Codegen subprocesses
use the calling Python interpreter, avoiding nested workspace sync operations.

Docker builds use the monorepo root as context. Packages install non-editably;
the runtime image contains the installed environment, not source-tree links.
The gateway's standalone compose and optional systemd installer use the new
paths. The installer creates a service-specific environment so it cannot remove
another service from a shared development environment.

The services use AGPL-3.0 with the plugin exception under the agreed license
policy. The bot's original MIT notice is preserved as LICENSE.MIT.

## Existing tmux deployments

The existing services were inspected read-only: both run via uv in separate tmux
sessions. The bot uses Python 3.14, gateway Python 3.13. A local bot wrapper and
configuration files, plus modified gateway observability assets, must be kept.
No production process, environment file or operator edit is changed by this PR.

Before switching:

1. Keep the old checkouts and launch wrappers for rollback. Back up local
   configuration and preserve any uncommitted dashboard/Prometheus edits outside
   the new checkout. Those private/operator files are not imported here.
2. Check out the reviewed monorepo commit separately. Build/test it before
   stopping a running service. Keep the old working directory so relative paths
   in existing configuration retain their meaning.
3. Prepare a separate runtime environment for each service, retaining its Python
   version. Do not switch the old environment or its symlinks while it is live.
4. In the existing tmux session, stop one old process, then invoke the new package
   with the old environment file and existing wrapper exports. Do not start a
   second Discord bot alongside the first.

Example for a gateway, run from its existing working directory (Bash syntax):

```sh
UV_PROJECT_ENVIRONMENT=/path/to/new-gateway-venv \
uv run --locked --python 3.13 \
  --project /path/to/monorepo/services/metrics-gateway \
  --package xcore-metrics-gateway --env-file /path/to/existing/gateway/.env \
  xcore-metrics-gateway
```

For the bot, use its own environment directory, Python 3.14, project
`services/discord-bot`, package/entrypoint `xcore-discord-bot`, and existing bot
`.env`. Preserve all other setup in the current launch wrapper.

Check gateway `/ready` and `/metrics`, and bot connection/command registration.
On failure, stop the new process and restart the unchanged old wrapper from the
old checkout. No protocol/data migration is needed for this repository move.
Do not archive old repositories until their deployments have been verified.

## Local verification

- Bot: 1863 existing tests, plus live Mongo document and Redis reconnection checks.
- Gateway: 35 tests on Python 3.14 and 3.13.
- Protocol: 323 tests and generation/model checks on Python 3.11.
- Bot mypy baseline: no new or fixed-baseline errors.
- Both Docker images build. Installed bot locale resources and local protocol
  import; gateway reaches `/ready` and serves metrics against real Redis.
- Discord was not contacted by smoke tests; production was not restarted.
- Secret scanning ignores only reviewed historical false positives: public test
  Discord IDs, an action-token parameter call and fictional map-name privacy text.
