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
            "owner": route.owner,
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
            "owner": match.group("owner"),
        }

    return parsed
