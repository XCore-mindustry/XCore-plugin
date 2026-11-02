# xcore-metrics-gateway

Standalone Prometheus-facing telemetry gateway for XCore Mindustry servers.

## Stack

- Python (managed with `uv`)
- `redis` (asyncio)
- `aiohttp`
- `prometheus-client`
- generated `xcore-protocol` telemetry models

## Responsibilities

- discover Redis TTL snapshot keys via `SCAN`
- read compressed snapshots with `MGET`
- GZIP-decompress and decode `MetricsSnapshotV1`
- maintain a bounded in-memory render view
- expose `GET /metrics`
- expose `GET /health`
- expose `GET /ready`

## Current scaffold

- uv workspace and shared pre-commit hooks
- environment-driven settings
- telemetry snapshot decoder using `xcore-protocol`
- read-only Redis client for binary snapshot values
- snapshot discovery via `SCAN`
- snapshot polling via batched `MGET`
- cardinality and label guards before snapshots enter the render store
- in-memory series store and Prometheus renderer skeleton
- `aiohttp` app with `/metrics`, `/health`, and `/ready`
- gateway self-metrics under `xcore_metrics_gateway_*`
- `/health` status with `starting`, `ready`, and `degraded` runtime states
- explicit Prometheus stale-node visibility via `xcore_node_stale{server=...}`
- bounded discovery/poll warning summaries for operational debugging without log spam
- containerized deployment surface via `Dockerfile` and `compose.yaml`
- provisioned Prometheus scrape config, recording/alerting rules, and Grafana dashboard bundle

## Environment variables

Optional:

- `GATEWAY_HTTP_HOST` (default: `0.0.0.0`)
- `GATEWAY_HTTP_PORT` (default: `9100`)
- `REDIS_URL` (default: `redis://127.0.0.1:6379`)
- `REDIS_DISCOVERY_INTERVAL_MS` (default: `30000`)
- `REDIS_POLL_INTERVAL_MS` (default: `3000`)
- `REDIS_SCAN_COUNT` (default: `100`)
- `REDIS_MGET_BATCH_SIZE` (default: `100`)
- `REDIS_COMMAND_TIMEOUT_MS` (default: `500`)
- `MAX_SERVERS` (default: `200`)
- `MAX_SERIES_PER_SERVER` (default: `5000`)
- `MAX_TOTAL_SERIES` (default: `250000`)
- `MAX_LABELS_PER_METRIC` (default: `8`)
- `MAX_LABEL_VALUE_LENGTH` (default: `80`)
- `STALE_SNAPSHOT_AGE_SECONDS` (default: `45`)
- `MAX_COMPRESSED_SNAPSHOT_BYTES` (default: `131072`)
- `MAX_UNCOMPRESSED_SNAPSHOT_BYTES` (default: `524288`)

Example environment file:

- `.env.example`

## Local setup

Run setup from the monorepo root.

```bash
uv sync --locked --all-packages --all-groups --all-extras
uv run --no-sync pre-commit install --install-hooks
```

## Local run

Run from this component directory with its `.env` file. Protocol is the local
workspace member, locked with both services in the root `uv.lock`.

```bash
uv run --locked --package xcore-metrics-gateway --env-file .env xcore-metrics-gateway
```

The gateway starts background discovery and polling loops automatically. `/metrics`
always renders from the local in-memory store and does not read Redis directly.
Snapshots older than `STALE_SNAPSHOT_AGE_SECONDS` are treated as stale: they are removed
from the rendered metrics view, but their node state remains visible through health and
gateway self-metrics. Prometheus can track stale nodes through
`xcore_node_stale{server=...}` and loop timing through gateway self-metrics. `/health`
is informational: it starts as `starting`, becomes `ready` after the first successful
discovery and poll, and moves to `degraded` when runtime failures or stale nodes are
observed. `/ready` returns HTTP 200 only when the runtime status is `ready`; Docker
healthchecks should use `/ready`.

## Container build

Use the monorepo root as the build context.

```bash
docker build -f services/metrics-gateway/Dockerfile -t xcore-metrics-gateway:local .
```

## Docker Compose stack

The repository includes a local observability stack:

- `redis`
- `xcore-metrics-gateway`
- `prometheus`
- `grafana`

Start it with:

```bash
docker compose -f services/metrics-gateway/compose.yaml up --build
```

Endpoints:

- gateway metrics: `http://127.0.0.1:9100/metrics`
- gateway health: `http://127.0.0.1:9100/health`
- gateway readiness: `http://127.0.0.1:9100/ready`
- Prometheus: `http://127.0.0.1:9090`
- Grafana: `http://127.0.0.1:3000` (`admin` / `admin`)

Provisioned assets:

- Prometheus scrape config: `ops/prometheus/prometheus.yml`
- Grafana datasource provisioning: `ops/grafana/provisioning/datasources/prometheus.yml`
- Grafana dashboard provisioning: `ops/grafana/provisioning/dashboards/default.yml`
- Default dashboard: `ops/grafana/dashboards/xcore-overview.json`
- Prometheus recording/alerting rules: `ops/prometheus/xcore.rules.yml`

### Metrics contract

The gateway does not maintain a second allowlist of server metrics: it forwards the
`MetricsSnapshotV1` samples produced by XCore-plugin and adds only the `server` label.
Sentinel metrics therefore appear when XCore-plugin telemetry is enabled and the relevant
event has occurred:

- `sentinel_nickname_denials_total{server,rule}` — nickname-rule denials
- `sentinel_admission_attempts_total{server,outcome}` — `confirmed`/`expired` attempts
- `sentinel_admission_waves_total{server,action}` — `observe`/`throttle` waves
- `sentinel_admission_closes_total{server}` — connections closed during throttling
- `sentinel_subnet_denials_total{server,origin}` — `local`/`global` subnet denials

The base XCore-plugin contract is also displayed: `mindustry_players_online`, `mindustry_wave`,
`mindustry_tps`, `mindustry_player_joins_total`, `mindustry_player_leaves_total`,
`xcore_plugin_uptime_seconds`, `xcore_commands_total`, `xcore_command_duration_seconds`,
`xcore_ingress_denials_total`, and `xcore_ingress_check_errors_total`. Gateway health metrics
are shown in the platform and gateway sections, including discovery/poll durations and all
poll/decode/validation/drop counters.

`xcore_ingress_denials_total` is the authoritative total ingress result from the XCore
pipeline; it is not added to the Sentinel-specific counters, because Sentinel denials are
already included there. The Sentinel rate panel shows the layer-specific counters separately.

Counters are intentionally visualized with `rate()`; the nickname rule panel is limited
to the top 10 series. Raw rule values can be high-cardinality, so they must not be used
as a dashboard variable. Sparse metrics are left as no data rather than being turned into
zero.

To validate the local assets:

```bash
uv run pytest -q
uv run ruff check
```

The asset test fails if a Sentinel metric is added without updating this dashboard/rules
contract.

The compose stack is intended for local and staging validation. For production, replace the
default Grafana admin credentials, define persistent volume policies explicitly, and put the
gateway and Grafana behind your normal network and secret-management controls.

## Non-Docker deploy

In production the gateway runs as a systemd user unit, deployed with
`ops/deploy <ssh-host> gateway`; see [ops/README.md](../../ops/README.md).

Validate the service:

```bash
systemctl --user status xcore-metrics-gateway
curl http://127.0.0.1:9100/ready
curl http://127.0.0.1:9100/metrics
```

To add a scrape job to an existing Prometheus:

```yaml
scrape_configs:
  - job_name: xcore-metrics-gateway
    static_configs:
      - targets: [<gateway-host>:9100]
```

## Tests

```bash
uv run pytest -q
```

## Lint

```bash
uv run --no-sync ruff check
uv run --no-sync ruff format --check
```

Existing tmux deployments and preservation of local observability edits are
covered by [the cutover guide](../../docs/monorepo-wave3.md).

## License

[AGPL-3.0](LICENSE) with the [plugin exception](LICENSE-EXCEPTION).
