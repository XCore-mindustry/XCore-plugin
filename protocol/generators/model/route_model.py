"""Strict normalized route manifest model for generation."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass(frozen=True, slots=True)
class RouteResponse:
    message_type: str
    message_version: int
    stream: str


@dataclass(frozen=True, slots=True)
class NormalizedRoute:
    family: str
    message_type: str
    message_version: int
    kind: str
    stream: str
    target_scope: str
    ttl_ms: int
    replayable: bool
    idempotent_consumer_recommended: bool
    owner: str
    response: RouteResponse | None = None


def _expect_str(value: Any, *, field_name: str, path: Path) -> str:
    if not isinstance(value, str) or not value:
        raise ValueError(f"{field_name} must be a non-empty string in {path}")
    return value


def _expect_int(value: Any, *, field_name: str, path: Path) -> int:
    if not isinstance(value, int):
        raise ValueError(f"{field_name} must be an int in {path}")
    return value


def _expect_bool(value: Any, *, field_name: str, path: Path) -> bool:
    if not isinstance(value, bool):
        raise ValueError(f"{field_name} must be a bool in {path}")
    return value


def _normalize_response(raw: Any, path: Path) -> RouteResponse | None:
    if raw is None:
        return None
    if not isinstance(raw, dict):
        raise ValueError(f"response must be an object in {path}")
    return RouteResponse(
        message_type=_expect_str(raw.get("messageType"), field_name="response.messageType", path=path),
        message_version=_expect_int(raw.get("messageVersion"), field_name="response.messageVersion", path=path),
        stream=_expect_str(raw.get("stream"), field_name="response.stream", path=path),
    )


def load_routes(path: Path) -> tuple[NormalizedRoute, ...]:
    with path.open("r", encoding="utf-8") as handle:
        raw = yaml.safe_load(handle)

    if not isinstance(raw, dict):
        raise ValueError(f"Route manifest must be an object: {path}")

    family = _expect_str(raw.get("family"), field_name="family", path=path)
    version = _expect_int(raw.get("version"), field_name="version", path=path)
    if version != 1:
        raise ValueError(f"Unsupported route manifest version in {path}: {version}")

    messages = raw.get("messages")
    if not isinstance(messages, list):
        raise ValueError(f"messages must be a list in {path}")

    routes: list[NormalizedRoute] = []
    for entry in messages:
        if not isinstance(entry, dict):
            raise ValueError(f"Each route entry must be an object in {path}")
        routes.append(
            NormalizedRoute(
                family=family,
                message_type=_expect_str(entry.get("messageType"), field_name="messageType", path=path),
                message_version=_expect_int(entry.get("messageVersion"), field_name="messageVersion", path=path),
                kind=_expect_str(entry.get("kind"), field_name="kind", path=path),
                stream=_expect_str(entry.get("stream"), field_name="stream", path=path),
                target_scope=_expect_str(entry.get("targetScope"), field_name="targetScope", path=path),
                ttl_ms=_expect_int(entry.get("ttlMs"), field_name="ttlMs", path=path),
                replayable=_expect_bool(entry.get("replayable"), field_name="replayable", path=path),
                idempotent_consumer_recommended=_expect_bool(
                    entry.get("idempotentConsumerRecommended"),
                    field_name="idempotentConsumerRecommended",
                    path=path,
                ),
                owner=_expect_str(entry.get("owner"), field_name="owner", path=path),
                response=_normalize_response(entry.get("response"), path),
            )
        )

    return tuple(routes)
