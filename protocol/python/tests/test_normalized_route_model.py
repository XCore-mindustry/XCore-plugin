from __future__ import annotations

from pathlib import Path

from generators.discovery import load_generation_plan
from generators.naming import message_type_constant_name
from generators.model import load_message_schema, load_routes, load_shared_schema


def test_load_shared_schema_normalizes_map_entry_fields() -> None:
    schema = load_shared_schema(Path("spec/shared/types/map-entry.v1.json"))

    assert schema.title == "MapEntryV1"
    assert schema.kind == "shared"
    assert schema.message_type is None
    assert schema.message_version is None

    required_names = {field.name for field in schema.fields if field.required}
    assert required_names == {"name", "fileName", "author"}


def test_load_message_schema_normalizes_identity_constants() -> None:
    schema = load_message_schema(Path("spec/messages/maps/maps.list.request.v1.json"))

    assert schema.title == "MapsListRequestV1"
    assert schema.kind == "message"
    assert schema.message_type == "maps.list.request"
    assert schema.message_version == 1


def test_load_routes_normalizes_maps_manifest() -> None:
    routes = load_routes(Path("spec/routes/maps.routes.v1.yaml"))

    assert len(routes) == 3
    first = routes[0]
    assert first.family == "maps"
    assert first.message_type == "maps.list.request"
    assert first.kind == "rpc-request"
    assert first.response is not None
    assert first.response.message_type == "maps.list.response"


def test_generation_plan_only_uses_reachable_maps_shared_types() -> None:
    plan = load_generation_plan(family="maps")

    assert [schema.title for schema in plan.shared_schemas] == [
        "MapEntryV1",
        "MapFileSourceV1",
    ]
    assert [schema.title for schema in plan.map_schemas] == [
        "MapsListRequestV1",
        "MapsListResponseV1",
        "MapsLoadCommandV1",
        "MapsRemoveRequestV1",
        "MapsRemoveResponseV1",
    ]
    assert [route.constant_name for route in plan.map_routes] == [
        "MAPS_LIST_REQUEST_V1",
        "MAPS_REMOVE_REQUEST_V1",
        "MAPS_LOAD_COMMAND_V1",
    ]


def test_generation_plan_supports_chat_family_without_shared_refs() -> None:
    plan = load_generation_plan(family="chat")

    assert [schema.title for schema in plan.shared_schemas] == [
        "MapEntryV1",
        "MapFileSourceV1",
    ]
    assert [schema.title for schema in plan.message_schemas_for("chat")] == [
        "ChatDiscordIngressCommandV1",
        "ChatGlobalV1",
        "ChatMessageV1",
        "PlayerJoinLeaveV1",
        "ServerActionV1",
        "ServerHeartbeatV1",
    ]
    assert [route.message_type for route in plan.routes_for("chat")] == [
        "chat.message",
        "chat.global",
        "chat.discord-ingress.command",
        "server.action",
        "player.join-leave",
        "server.heartbeat",
    ]
    assert [schema.title for schema in plan.map_schemas] == [
        "MapsListRequestV1",
        "MapsListResponseV1",
        "MapsLoadCommandV1",
        "MapsRemoveRequestV1",
        "MapsRemoveResponseV1",
    ]
    assert [route.constant_name for route in plan.chat_routes] == [
        "CHAT_MESSAGE_V1",
        "CHAT_GLOBAL_V1",
        "CHAT_DISCORD_INGRESS_COMMAND_V1",
        "SERVER_ACTION_V1",
        "PLAYER_JOIN_LEAVE_V1",
        "SERVER_HEARTBEAT_V1",
    ]
    assert [route.constant_name for route in plan.map_routes] == [
        "MAPS_LIST_REQUEST_V1",
        "MAPS_REMOVE_REQUEST_V1",
        "MAPS_LOAD_COMMAND_V1",
    ]


def test_generation_plan_keeps_family_inputs_separate() -> None:
    plan = load_generation_plan(family="chat")

    assert [family_input.family for family_input in plan.family_inputs] == ["maps", "chat"]
    assert plan.family_inputs[0].message_schemas == plan.message_schemas_for("maps")
    assert plan.family_inputs[0].routes == plan.routes_for("maps")
    assert plan.family_inputs[1].message_schemas == plan.message_schemas_for("chat")
    assert plan.family_inputs[1].routes == plan.routes_for("chat")


def test_generation_plan_supports_discord_family_with_nested_shared_refs() -> None:
    plan = load_generation_plan(family="discord")

    assert [schema.title for schema in plan.shared_schemas] == [
        "DiscordIdentityRefV1",
        "PlayerRefV1",
    ]
    assert [schema.title for schema in plan.discord_schemas] == [
        "DiscordAdminAccessChangedCommandV1",
        "DiscordLinkConfirmCommandV1",
        "DiscordLinkStatusChangedV1",
        "DiscordUnlinkCommandV1",
    ]
    assert [route.message_type for route in plan.routes_for("discord")] == [
        "discord.link.confirm.command",
        "discord.unlink.command",
        "discord.link.status-changed",
        "discord.admin-access.changed.command",
    ]
    assert [route.constant_name for route in plan.discord_routes] == [
        "DISCORD_LINK_CONFIRM_COMMAND_V1",
        "DISCORD_UNLINK_COMMAND_V1",
        "DISCORD_LINK_STATUS_CHANGED_V1",
        "DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1",
    ]


def test_generation_plan_supports_moderation_family_with_shared_refs() -> None:
    plan = load_generation_plan(family="moderation")

    assert [schema.title for schema in plan.shared_schemas] == [
        "ActorRefV1",
        "ExpirationInfoV1",
        "PlayerRefV1",
        "VoteKickParticipantV1",
    ]
    assert [schema.title for schema in plan.moderation_schemas] == [
        "ModerationAuditAppendedV1",
        "ModerationBanCreatedV1",
        "ModerationKickBannedCommandV1",
        "ModerationMuteCreatedV1",
        "ModerationPardonCommandV1",
        "ModerationVoteKickCreatedV1",
    ]
    assert [route.message_type for route in plan.routes_for("moderation")] == [
        "moderation.ban.created",
        "moderation.mute.created",
        "moderation.vote-kick.created",
        "moderation.kick-banned.command",
        "moderation.pardon.command",
        "moderation.audit.appended",
    ]
    assert [route.constant_name for route in plan.moderation_routes] == [
        "MODERATION_BAN_CREATED_V1",
        "MODERATION_MUTE_CREATED_V1",
        "MODERATION_VOTE_KICK_CREATED_V1",
        "MODERATION_KICK_BANNED_COMMAND_V1",
        "MODERATION_PARDON_COMMAND_V1",
        "MODERATION_AUDIT_APPENDED_V1",
    ]


def test_message_type_constant_name_normalizes_hyphenated_identifiers() -> None:
    assert message_type_constant_name("chat.discord-ingress.command", 1) == "CHAT_DISCORD_INGRESS_COMMAND_V1"
    assert message_type_constant_name("player.join-leave", 1) == "PLAYER_JOIN_LEAVE_V1"
    assert message_type_constant_name("server.heartbeat", 1) == "SERVER_HEARTBEAT_V1"


def test_message_type_constant_name_preserves_existing_maps_behavior() -> None:
    assert message_type_constant_name("maps.list.request", 1) == "MAPS_LIST_REQUEST_V1"
