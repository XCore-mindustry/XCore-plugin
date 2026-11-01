# Telemetry Messages

Telemetry contracts define canonical snapshot payloads published into Redis TTL keys rather than routed streams.

## Canonical V1 Messages
- `metrics.snapshot.v1` - canonical telemetry snapshot payload stored as compressed JSON before Redis publication.

## Field Policy
- Payload fields use camelCase.
- Telemetry snapshots use `schemaVersion` instead of `messageType` and `messageVersion` because they are snapshot documents, not routed messages.
- Producers must not include `server` inside per-sample `labels`; the enclosing snapshot `server` field is the canonical source.
- Histogram samples use cumulative `counts` aligned to Prometheus bucket semantics.
