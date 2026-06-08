from __future__ import annotations

import re
from pathlib import Path

from xcore_protocol.generated import ROUTES_BY_MESSAGE
from xcore_protocol.paths import repo_root


def test_generated_java_route_registries_match_python_route_surface() -> None:
    java_route_files = {
        "maps": repo_root()
        / "java/core/src/main/java/org/xcore/protocol/generated/routes/MapsRoutes.java",
        "chat": repo_root()
        / "java/core/src/main/java/org/xcore/protocol/generated/routes/ChatRoutes.java",
        "discord": repo_root()
        / "java/core/src/main/java/org/xcore/protocol/generated/routes/DiscordRoutes.java",
        "moderation": repo_root()
        / "java/core/src/main/java/org/xcore/protocol/generated/routes/ModerationRoutes.java",
    }

    expected_by_family = _expected_route_surface_by_family()

    for family, java_file in java_route_files.items():
        actual_routes = _parse_java_route_surface(java_file)
        expected_routes = expected_by_family[family]
        assert actual_routes == expected_routes


def _expected_route_surface_by_family() -> dict[str, dict[tuple[str, int], dict[str, object]]]:
    expected: dict[str, dict[tuple[str, int], dict[str, object]]] = {}

    for key, route in ROUTES_BY_MESSAGE.items():
        family_routes = expected.setdefault(route.family, {})
        family_routes[key] = {
            "messageType": route.messageType,
            "messageVersion": route.messageVersion,
            "kind": route.kind,
            "stream": route.stream,
            "bindings": route.bindings,
            "targetScope": route.targetScope,
            "owner": route.owner,
            "response": None
            if route.response is None
            else {
                "messageType": route.response.messageType,
                "messageVersion": route.response.messageVersion,
                "stream": route.response.stream,
                "bindings": route.response.bindings,
            },
        }

    return expected


def _parse_java_route_surface(java_file: Path) -> dict[tuple[str, int], dict[str, object]]:
    content = java_file.read_text(encoding="utf-8")

    route_pattern = re.compile(
        r'public static final RouteDescriptor\s+(?P<constant>[A-Z0-9_]+)\s*=\s*new RouteDescriptor\(\n'
        r'\s*"(?P<family>[^"]+)",\n'
        r'\s*"(?P<method>[^"]+)",\n'
        r'\s*"(?P<message_type>[^"]+)",\n'
        r'\s*(?P<message_version>\d+),\n'
        r'\s*[^\n]+,\n'
        r'\s*"(?P<kind>[^"]+)",\n'
        r'\s*"(?P<stream>[^"]+)",\n'
        r'\s*(?P<bindings>Map\.of\([\s\S]*?\)|Map\.of\(\)),\n'
        r'\s*"(?P<target_scope>[^"]+)",\n'
        r'\s*(?P<ttl_ms>\d+),\n'
        r'\s*(?P<replayable>true|false),\n'
        r'\s*(?P<idempotent>true|false),\n'
        r'\s*"(?P<owner>[^"]+)",\n'
        r'\s*(?P<response>null|new RouteResponseDescriptor\([\s\S]*?\))\n'
        r'\s*\);',
        re.MULTILINE,
    )

    parsed: dict[tuple[str, int], dict[str, object]] = {}
    for match in route_pattern.finditer(content):
        message_type = match.group("message_type")
        message_version = int(match.group("message_version"))
        parsed[(message_type, message_version)] = {
            "messageType": message_type,
            "messageVersion": message_version,
            "kind": match.group("kind"),
            "stream": match.group("stream"),
            "bindings": _parse_java_map(match.group("bindings")),
            "targetScope": match.group("target_scope"),
            "owner": match.group("owner"),
            "response": _parse_java_response(match.group("response")),
        }

    return parsed


def _parse_java_map(value: str) -> dict[str, str]:
    value = value.strip()
    if value == "Map.of()":
        return {}
    match = re.fullmatch(r"Map\.of\((?P<body>.*)\)", value, re.DOTALL)
    if match is None:
        raise AssertionError(f"Unsupported Java map literal: {value}")
    parts = re.findall(r'"([^"]+)"', match.group("body"))
    if len(parts) % 2 != 0:
        raise AssertionError(f"Uneven Java map literal entries: {value}")
    return {parts[index]: parts[index + 1] for index in range(0, len(parts), 2)}


def _parse_java_response(value: str) -> dict[str, object] | None:
    if value == "null":
        return None
    response_pattern = re.compile(
        r'new RouteResponseDescriptor\(\s*"(?P<message_type>[^"]+)",\s*(?P<message_version>\d+),\s*[^,]+,\s*"(?P<stream>[^"]+)",\s*(?P<bindings>Map\.of\([\s\S]*?\)|Map\.of\(\))\s*\)',
        re.DOTALL,
    )
    match = response_pattern.fullmatch(value.strip())
    if match is None:
        raise AssertionError(f"Unsupported Java response literal: {value}")
    return {
        "messageType": match.group("message_type"),
        "messageVersion": int(match.group("message_version")),
        "stream": match.group("stream"),
        "bindings": _parse_java_map(match.group("bindings")),
    }
