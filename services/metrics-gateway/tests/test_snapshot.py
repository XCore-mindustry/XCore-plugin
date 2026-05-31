from __future__ import annotations

import gzip
import json

import pytest

from xcore_metrics_gateway.snapshot import (
    SnapshotDecodeError,
    SnapshotValidationError,
    decode_snapshot,
)


def _encode(payload: dict[str, object]) -> bytes:
    return gzip.compress(json.dumps(payload).encode("utf-8"))


def test_decode_snapshot_parses_valid_telemetry_payload() -> None:
    compressed = _encode(
        {
            "schemaVersion": "metrics.snapshot.v1",
            "server": "mini-hexed",
            "nodeId": "mini-hexed-01",
            "producer": "xcore-plugin/4.1.0-SNAPSHOT",
            "createdAtUnixMs": 1_790_789_112_000,
            "startTimeUnixMs": 1_790_789_000_000,
            "sequence": 7,
            "intervalMs": 15000,
            "samples": [
                {
                    "name": "mindustry_players_online",
                    "type": "gauge",
                    "labels": {},
                    "value": 12,
                },
                {
                    "name": "xcore_command_duration_seconds",
                    "type": "histogram",
                    "labels": {"command": "maps"},
                    "buckets": [0.1, 0.5, 1.0],
                    "counts": [1, 2, 3],
                    "count": 4,
                    "sum": 5.45,
                },
            ],
        }
    )

    decoded = decode_snapshot(
        compressed,
        max_compressed_snapshot_bytes=131072,
        max_uncompressed_snapshot_bytes=524288,
    )

    assert decoded.snapshot.server == "mini-hexed"
    assert decoded.snapshot.sequence == 7
    assert len(decoded.snapshot.samples) == 2


def test_decode_snapshot_rejects_reserved_server_label() -> None:
    compressed = _encode(
        {
            "schemaVersion": "metrics.snapshot.v1",
            "server": "mini-hexed",
            "nodeId": "mini-hexed-01",
            "producer": "xcore-plugin",
            "createdAtUnixMs": 1,
            "startTimeUnixMs": 0,
            "sequence": 0,
            "intervalMs": 15000,
            "samples": [
                {
                    "name": "mindustry_players_online",
                    "type": "gauge",
                    "labels": {"server": "bad"},
                    "value": 12,
                }
            ],
        }
    )

    with pytest.raises(SnapshotValidationError, match="reserved label"):
        decode_snapshot(
            compressed,
            max_compressed_snapshot_bytes=131072,
            max_uncompressed_snapshot_bytes=524288,
        )


def test_decode_snapshot_rejects_non_cumulative_histogram_counts() -> None:
    compressed = _encode(
        {
            "schemaVersion": "metrics.snapshot.v1",
            "server": "mini-hexed",
            "nodeId": "mini-hexed-01",
            "producer": "xcore-plugin",
            "createdAtUnixMs": 1,
            "startTimeUnixMs": 0,
            "sequence": 0,
            "intervalMs": 15000,
            "samples": [
                {
                    "name": "xcore_command_duration_seconds",
                    "type": "histogram",
                    "labels": {},
                    "buckets": [0.1, 0.5, 1.0],
                    "counts": [1, 3, 2],
                    "count": 3,
                    "sum": 1.2,
                }
            ],
        }
    )

    with pytest.raises(SnapshotValidationError, match="counts must be cumulative"):
        decode_snapshot(
            compressed,
            max_compressed_snapshot_bytes=131072,
            max_uncompressed_snapshot_bytes=524288,
        )


def test_decode_snapshot_rejects_invalid_json_as_decode_failure() -> None:
    compressed = gzip.compress(b"not-json")

    with pytest.raises(SnapshotDecodeError, match="valid utf-8 json"):
        decode_snapshot(
            compressed,
            max_compressed_snapshot_bytes=131072,
            max_uncompressed_snapshot_bytes=524288,
        )
