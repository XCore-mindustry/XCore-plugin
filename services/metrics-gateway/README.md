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
- keep a bounded in-memory render view
- expose `GET /metrics`
- expose `GET /health`
- expose `GET /ready`

## Current scaffold

- uv project and pre-commit hooks
- environment-driven settings
- telemetry snapshot decoder using `xcore-protocol`
- read-only Redis client for binary snapshot values
- snapshot discovery via `SCAN`
- snapshot polling via batched `MGET`
- cardinality and label guards before snapshots enter the render store
- in-memory series store and Prometheus renderer skeleton
- `aiohttp` app with `/metrics`, `/health`, and `/ready`
- gateway self-metrics such as `xcore_metrics_gateway_*`
- `/health` status with `starting`, `ready`, and `degraded` runtime states
- explicit Prometheus stale-node visibility via `xcore_node_stale{server=...}`
- bounded discovery/poll warning summaries for operational debugging without log spam
- containerized deployment surface via `Dockerfile` and `compose.yaml`
- provisioned Prometheus scrape config and Grafana datasource/dashboard bundle

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

```bash
uv sync --all-groups
uv run pre-commit install --install-hooks
```

## Local run

```bash
uv sync
uv run xcore-metrics-gateway
```

The current MVP starts background discovery and polling loops automatically. `/metrics`
always renders from the local in-memory store and does not read Redis directly.
Snapshots older than `STALE_SNAPSHOT_AGE_SECONDS` are treated as stale: they are removed
from the rendered metrics view, but their node state remains visible through health and
gateway self-metrics. Prometheus also gets explicit stale visibility through
`xcore_node_stale{server=...}` and loop timing through gateway self-metrics. `/health`
is informational: it starts as `starting`, becomes `ready` after the first successful
discovery and poll, and moves to `degraded` when runtime failures or stale nodes are
observed. `/ready` returns HTTP 200 only when the runtime status is `ready`; Docker
healthchecks should use `/ready`.

## Container build

```bash
docker build -t xcore-metrics-gateway:local .
```

## Docker Compose stack

The repository now ships a ready-to-run observability stack:

- `redis`
- `xcore-metrics-gateway`
- `prometheus`
- `grafana`

Start it with:

```bash
docker compose up --build
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

The compose stack is suitable for local and staging validation. For production, replace the
default Grafana admin credentials, set persistent volume policies explicitly, and front the
gateway/Grafana endpoints with your normal network and secret-management controls.

## Non-Docker production deploy

You can run the gateway directly on a VM or bare-metal host without Docker.

### 1. Install the service files

Suggested layout:

```text
/opt/xcore/xcore-metrics-gateway/
  pyproject.toml
  uv.lock
  src/
```

Install Python + `uv`, then sync the locked environment:

```bash
cd /opt/xcore/xcore-metrics-gateway
uv sync --locked
```

### 2. Create the environment file

Use `.env.example` as the base and install it as:

```text
/etc/xcore/xcore-metrics-gateway.env
```

Minimum required values in most deployments:

```bash
GATEWAY_HTTP_HOST=0.0.0.0
GATEWAY_HTTP_PORT=9100
REDIS_URL=redis://<redis-host>:6379
```

### 3. Install the systemd unit

Template unit file:

- `ops/systemd/xcore-metrics-gateway.service`

Typical install flow:

```bash
sudo cp ops/systemd/xcore-metrics-gateway.service /etc/systemd/system/
sudo mkdir -p /etc/xcore
sudo cp .env.example /etc/xcore/xcore-metrics-gateway.env
sudo systemctl daemon-reload
sudo systemctl enable --now xcore-metrics-gateway
```

### 4. Validate the running service

```bash
systemctl status xcore-metrics-gateway
curl http://127.0.0.1:9100/health
curl http://127.0.0.1:9100/ready
curl http://127.0.0.1:9100/metrics
```

### 5. Point Prometheus at the gateway

If Prometheus already exists outside this repository, add a scrape job like this:

```yaml
scrape_configs:
  - job_name: xcore-metrics-gateway
    metrics_path: /metrics
    static_configs:
      - targets:
          - <gateway-host>:9100
```

### Operational notes

- Prefer a dedicated service user such as `xcore`.
- Keep the gateway bound behind your normal firewall/reverse-proxy policy.
- Treat `/health`, `/ready`, and `/metrics` as internal observability endpoints.
- Use the default Grafana dashboard JSON from `ops/grafana/dashboards/xcore-overview.json`
  if you already run Grafana elsewhere.

## Tests

```bash
uv run pytest -q
```

## Lint

```bash
uvx ruff check
uvx ruff format --check
```
