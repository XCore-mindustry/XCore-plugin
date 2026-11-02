from __future__ import annotations

from dataclasses import dataclass

from xcore_protocol.generated import MetricSampleV1, MetricsSnapshotV1

from .settings import Settings


@dataclass(frozen=True, slots=True)
class GuardResult:
    snapshot: MetricsSnapshotV1 | None
    dropped_reasons: tuple[tuple[str, int], ...]


class CardinalityGuard:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def apply(
        self,
        snapshot: MetricsSnapshotV1,
        *,
        server_already_tracked: bool,
        current_server_series: int,
        tracked_servers: int,
        tracked_total_series: int,
    ) -> GuardResult:
        dropped: dict[str, int] = {}

        if not server_already_tracked and tracked_servers >= self._settings.max_servers:
            dropped["server_limit"] = 1
            return GuardResult(
                snapshot=None, dropped_reasons=tuple(sorted(dropped.items()))
            )

        allowed_remaining = max(
            self._settings.max_total_series
            - tracked_total_series
            + current_server_series,
            0,
        )
        if allowed_remaining == 0:
            dropped["total_series_limit"] = len(snapshot.samples)
            return GuardResult(
                snapshot=None, dropped_reasons=tuple(sorted(dropped.items()))
            )

        kept_samples: list[MetricSampleV1] = []
        for sample in snapshot.samples:
            if len(kept_samples) >= self._settings.max_series_per_server:
                dropped["series_per_server_limit"] = (
                    dropped.get("series_per_server_limit", 0) + 1
                )
                continue
            if len(kept_samples) >= allowed_remaining:
                dropped["total_series_limit"] = dropped.get("total_series_limit", 0) + 1
                continue
            if len(sample.labels) > self._settings.max_labels_per_metric:
                dropped["labels_per_metric_limit"] = (
                    dropped.get("labels_per_metric_limit", 0) + 1
                )
                continue

            too_long = False
            for value in sample.labels.values():
                if len(str(value)) > self._settings.max_label_value_length:
                    too_long = True
                    break
            if too_long:
                dropped["label_value_length_limit"] = (
                    dropped.get("label_value_length_limit", 0) + 1
                )
                continue

            kept_samples.append(sample)

        if not kept_samples:
            return GuardResult(
                snapshot=None, dropped_reasons=tuple(sorted(dropped.items()))
            )

        if len(kept_samples) == len(snapshot.samples):
            return GuardResult(
                snapshot=snapshot, dropped_reasons=tuple(sorted(dropped.items()))
            )

        guarded_snapshot = MetricsSnapshotV1(
            server=snapshot.server,
            nodeId=snapshot.nodeId,
            producer=snapshot.producer,
            createdAtUnixMs=snapshot.createdAtUnixMs,
            startTimeUnixMs=snapshot.startTimeUnixMs,
            sequence=snapshot.sequence,
            intervalMs=snapshot.intervalMs,
            samples=tuple(kept_samples),
        )
        return GuardResult(
            snapshot=guarded_snapshot,
            dropped_reasons=tuple(sorted(dropped.items())),
        )
