"""Declarative family registry for generator inputs and outputs."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class FamilyConfig:
    name: str
    python_module: str
    java_package: str
    java_messages_class: str
    java_routes_class: str
    includes: tuple[str, ...]
    generated_model_test: str | None = None
    alias_policy: str | None = None
    default_selected: bool = False

    @property
    def message_dir(self) -> str:
        return f"messages/{self.name}"

    @property
    def route_manifest(self) -> str:
        return f"routes/{self.name}.routes.v1.yaml"

    @property
    def route_aliases(self) -> tuple[str, ...]:
        if self.alias_policy == "maps_compat":
            return ("MAPS_ROUTES_BY_MESSAGE",)
        return ()


FAMILY_CONFIGS: tuple[FamilyConfig, ...] = (
    FamilyConfig(
        name="maps",
        python_module="maps",
        java_package="org.xcore.protocol.generated.messages.maps",
        java_messages_class="MapsMessages",
        java_routes_class="MapsRoutes",
        includes=("maps",),
        generated_model_test="python/tests/test_generated_maps_models.py",
        alias_policy="maps_compat",
        default_selected=True,
    ),
    FamilyConfig(
        name="chat",
        python_module="chat",
        java_package="org.xcore.protocol.generated.messages.chat",
        java_messages_class="ChatMessages",
        java_routes_class="ChatRoutes",
        includes=("maps", "chat"),
        generated_model_test="python/tests/test_generated_chat_models.py",
    ),
    FamilyConfig(
        name="discord",
        python_module="discord",
        java_package="org.xcore.protocol.generated.messages.discord",
        java_messages_class="DiscordMessages",
        java_routes_class="DiscordRoutes",
        includes=("discord",),
        generated_model_test="python/tests/test_generated_discord_models.py",
    ),
    FamilyConfig(
        name="moderation",
        python_module="moderation",
        java_package="org.xcore.protocol.generated.messages.moderation",
        java_messages_class="ModerationMessages",
        java_routes_class="ModerationRoutes",
        includes=("moderation",),
        generated_model_test="python/tests/test_generated_moderation_models.py",
    ),
)

FAMILY_CONFIG_BY_NAME: dict[str, FamilyConfig] = {config.name: config for config in FAMILY_CONFIGS}


def default_family_name() -> str:
    for config in FAMILY_CONFIGS:
        if config.default_selected:
            return config.name
    raise ValueError("No default family configured for generator discovery")


def get_family_config(name: str) -> FamilyConfig | None:
    return FAMILY_CONFIG_BY_NAME.get(name)


def require_family_config(name: str) -> FamilyConfig:
    config = get_family_config(name)
    if config is None:
        supported = ", ".join(repr(item) for item in supported_family_names())
        raise ValueError(f"Unknown family config: {name!r}. Supported: {supported}")
    return config


def supported_family_names() -> tuple[str, ...]:
    return tuple(config.name for config in FAMILY_CONFIGS)


def supported_family_display() -> str:
    return ", ".join(supported_family_names())


def resolve_family_configs(requested_family: str | None) -> tuple[FamilyConfig, ...]:
    if requested_family is None:
        return (FAMILY_CONFIG_BY_NAME[default_family_name()],)

    requested_config = get_family_config(requested_family)
    if requested_config is None:
        supported = ", ".join(repr(name) for name in supported_family_names())
        raise ValueError(
            f"Unsupported family for generation: {requested_family!r}. Supported: {supported}"
        )

    return tuple(FAMILY_CONFIG_BY_NAME[name] for name in requested_config.includes)


def entrypoint_family_configs() -> tuple[FamilyConfig, ...]:
    included_by_other_families = {
        included_family
        for config in FAMILY_CONFIGS
        for included_family in config.includes
        if included_family != config.name
    }
    return tuple(
        config for config in FAMILY_CONFIGS if config.name not in included_by_other_families
    )


def entrypoint_family_names() -> tuple[str, ...]:
    return tuple(config.name for config in entrypoint_family_configs())


def generated_model_test_paths() -> tuple[str, ...]:
    return tuple(
        config.generated_model_test
        for config in FAMILY_CONFIGS
        if config.generated_model_test is not None
    )


def family_argument_help() -> str:
    return (
        "Restrict the operation to one registered protocol family. "
        f"Supported: {supported_family_display()}. "
        f"Default selection: {default_family_name()}."
    )
