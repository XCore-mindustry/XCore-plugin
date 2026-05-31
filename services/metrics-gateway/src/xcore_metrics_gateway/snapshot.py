from __future__ import annotations

import gzip
import json
import math
from dataclasses import dataclass
from typing import Any

from xcore_protocol.generated import MetricSampleV1Type, MetricsSnapshotV1


class SnapshotDecodeError(ValueError):
    pass


class SnapshotValidationError(SnapshotDecodeError):
    pass


@dataclass(frozen=True, slots=True)
class DecodedSnapshot:
    snapshot: MetricsSnapshotV1
    payload: dict[str, Any]


def decode_snapshot(
    compressed: bytes,
    *,
    max_compressed_snapshot_bytes: int,
    max_uncompressed_snapshot_bytes: int,
) -> DecodedSnapshot:
    if len(compressed) > max_compressed_snapshot_bytes:
        raise SnapshotDecodeError("compressed snapshot exceeds configured size limit")

    try:
        payload_bytes = gzip.decompress(compressed)
    except OSError as error:
        raise SnapshotDecodeError("snapshot is not valid gzip data") from error

    if len(payload_bytes) > max_uncompressed_snapshot_bytes:
        raise SnapshotDecodeError("uncompressed snapshot exceeds configured size limit")

    try:
        payload = json.loads(payload_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise SnapshotDecodeError("snapshot is not valid utf-8 json") from error

    try:
        snapshot = MetricsSnapshotV1.from_payload(payload)
    except (TypeError, ValueError) as error:
        raise SnapshotDecodeError(str(error)) from error

    _validate_snapshot(snapshot)
    return DecodedSnapshot(snapshot=snapshot, payload=payload)


def _validate_snapshot(snapshot: MetricsSnapshotV1) -> None:
    for sample in snapshot.samples:
        if "server" in sample.labels:
            raise SnapshotValidationError(
                "metric labels must not include reserved label 'server'"
            )

        if sample.type in (
            MetricSampleV1Type.COUNTER,
            MetricSampleV1Type.GAUGE,
            MetricSampleV1Type.INFO,
        ):
            if sample.value is None or not math.isfinite(sample.value):
                raise SnapshotValidationError(
                    f"sample {sample.name} requires finite value"
                )

        if sample.type is MetricSampleV1Type.HISTOGRAM:
            if (
                sample.buckets is None
                or sample.counts is None
                or sample.count is None
                or sample.sum is None
            ):
                raise SnapshotValidationError(
                    f"histogram sample {sample.name} is missing required fields"
                )
            if len(sample.buckets) != len(sample.counts):
                raise SnapshotValidationError(
                    f"histogram sample {sample.name} buckets/counts length mismatch"
                )
            if sample.count < 0:
                raise SnapshotValidationError(
                    f"histogram sample {sample.name} count must be >= 0"
                )
            if not math.isfinite(sample.sum):
                raise SnapshotValidationError(
                    f"histogram sample {sample.name} sum must be finite"
                )

            previous_bucket = None
            previous_count = -1
            for bucket, count in zip(sample.buckets, sample.counts, strict=True):
                if not math.isfinite(bucket):
                    raise SnapshotValidationError(
                        f"histogram sample {sample.name} bucket must be finite"
                    )
                if previous_bucket is not None and bucket <= previous_bucket:
                    raise SnapshotValidationError(
                        f"histogram sample {sample.name} buckets must be strictly increasing"
                    )
                if count < previous_count:
                    raise SnapshotValidationError(
                        f"histogram sample {sample.name} counts must be cumulative"
                    )
                previous_bucket = bucket
                previous_count = count

            if sample.counts and sample.counts[-1] > sample.count:
                raise SnapshotValidationError(
                    f"histogram sample {sample.name} final bucket count exceeds total count"
                )
