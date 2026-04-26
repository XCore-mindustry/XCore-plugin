from __future__ import annotations

from pathlib import Path

from generators.families import default_family_name, supported_family_names
from generators.cli import build_parser, main
from generators.discovery import load_generation_plan
from generators.java_writer import render_java_outputs
from generators.python_writer import render_python_outputs


def test_generator_cli_exposes_expected_subcommands() -> None:
    parser = build_parser()

    command_actions = [
        action for action in parser._actions if action.dest == "command"  # pyright: ignore[reportPrivateUsage]
    ]
    assert len(command_actions) == 1

    subparser_action = command_actions[0]
    assert set(subparser_action.choices.keys()) == {"generate", "check", "inspect"}


def test_generator_cli_accepts_registered_generate_commands() -> None:
    for family_name in supported_family_names():
        exit_code = main(["generate", "--language", "python", "--family", family_name])
        assert exit_code == 0


def test_generator_cli_inspect_supports_registered_families() -> None:
    for family_name in supported_family_names():
        exit_code = main(["inspect", "--family", family_name])
        assert exit_code == 0


def test_generator_cli_inspect_uses_registry_default_family(capsys) -> None:
    exit_code = main(["inspect"])
    assert exit_code == 0

    captured = capsys.readouterr()
    default_plan = load_generation_plan(family=default_family_name())

    assert "shared=" in captured.out
    assert "messages=" in captured.out
    for schema in default_plan.message_schemas_for(default_family_name()):
        assert schema.title in captured.out


def test_generator_cli_rejects_unsupported_family() -> None:
    unsupported_family = "unsupported-family"
    assert unsupported_family not in supported_family_names()

    try:
        main(["inspect", "--family", unsupported_family])
    except ValueError as error:
        assert "Unsupported family" in str(error)
    else:
        raise AssertionError("Expected unsupported family to raise ValueError")


def test_family_scoped_python_generation_keeps_aggregate_outputs_complete() -> None:
    outputs = render_python_outputs(load_generation_plan(family="discord"))
    output_by_name = {Path(output.path).name: output.content for output in outputs}

    assert "discord.py" in output_by_name
    assert "chat.py" not in output_by_name
    assert "maps.py" not in output_by_name

    package_init = output_by_name["__init__.py"]
    routes_module = output_by_name["routes.py"]
    shared_module = output_by_name["shared.py"]

    assert "ChatMessageV1" in package_init
    assert "MapsListRequestV1" in package_init
    assert "DiscordLinkConfirmCommandV1" in package_init

    assert "CHAT_MESSAGE_V1" in routes_module
    assert "MAPS_LIST_REQUEST_V1" in routes_module
    assert "DISCORD_LINK_CONFIRM_COMMAND_V1" in routes_module

    assert "MapEntryV1" in shared_module
    assert "MapFileSourceV1" in shared_module
    assert "PlayerRefV1" in shared_module
    assert "DiscordIdentityRefV1" in shared_module


def test_family_scoped_python_generation_keeps_aggregate_envelope_outputs_complete() -> None:
    outputs = render_python_outputs(load_generation_plan(family="moderation"))
    output_by_name = {Path(output.path).name: output.content for output in outputs}

    assert "envelopes.py" in output_by_name

    envelopes_module = output_by_name["envelopes.py"]
    package_init = output_by_name["__init__.py"]

    assert "class EnvelopeBaseV1:" in envelopes_module
    assert "class EventEnvelopeV1:" in envelopes_module
    assert "class CommandEnvelopeV1:" in envelopes_module
    assert "class RpcRequestEnvelopeV1:" in envelopes_module
    assert "class RpcResponseEnvelopeV1:" in envelopes_module
    assert "class DlqEnvelopeV1:" in envelopes_module
    assert "EnvelopeBaseV1" in package_init
    assert "EventEnvelopeV1" in package_init
    assert "CommandEnvelopeV1" in package_init
    assert "RpcRequestEnvelopeV1" in package_init
    assert "RpcResponseEnvelopeV1" in package_init
    assert "DlqEnvelopeV1" in package_init


def test_family_scoped_java_generation_preserves_nullable_map_values() -> None:
    outputs = render_java_outputs(load_generation_plan(family="moderation"))
    output_by_name = {Path(output.path).name: output.content for output in outputs}

    moderation_messages = output_by_name["ModerationMessages.java"]

    assert "details = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(details));" in moderation_messages
    assert "details = Map.copyOf(details);" not in moderation_messages
