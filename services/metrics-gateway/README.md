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

## Current scaffold

- uv project and pre-commit hooks
- environment-driven settings
- telemetry snapshot decoder using `xcore-protocol`
- read-only Redis client for binary snapshot values
- snapshot discovery via `SCAN`
- snapshot polling via batched `MGET`
- cardinality and label guards before snapshots enter the render store
- in-memory series store and Prometheus renderer skeleton
- `aiohttp` app with `/metrics` and `/health`
- gateway self-metrics such as `xcore_metrics_gateway_*`
- degraded `/health` status with runtime failure reasons

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
gateway self-metrics.

## Tests

```bash
uv run pytest -q
```

## Lint

```bash
uvx ruff check
uvx ruff format --check
```
