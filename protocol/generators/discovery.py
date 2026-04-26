"""Discover supported canonical inputs for generation."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from .families import FamilyConfig, entrypoint_family_configs, resolve_family_configs
from .model import (
    NormalizedRoute,
    NormalizedSchema,
    load_envelope_schema,
    load_message_schema,
    load_routes,
    load_shared_schema,
)
from .route_descriptors import RouteDescriptor, build_route_descriptors


@dataclass(frozen=True, slots=True)
class FamilyGenerationInput:
    family: str
    config: FamilyConfig
    message_schemas: tuple[NormalizedSchema, ...]
    routes: tuple[NormalizedRoute, ...]


@dataclass(frozen=True, slots=True)
class GenerationPlan:
    shared_schemas: tuple[NormalizedSchema, ...]
    family_inputs: tuple[FamilyGenerationInput, ...]
    envelope_schemas: tuple[NormalizedSchema, ...]
    route_descriptors: tuple[RouteDescriptor, ...]

    @property
    def families(self) -> dict[str, FamilyGenerationInput]:
        return {family_input.family: family_input for family_input in self.family_inputs}

    @property
    def map_schemas(self) -> tuple[NormalizedSchema, ...]:
        return self.message_schemas_for("maps")

    @property
    def chat_schemas(self) -> tuple[NormalizedSchema, ...]:
        return self.message_schemas_for("chat")

    @property
    def discord_schemas(self) -> tuple[NormalizedSchema, ...]:
        return self.message_schemas_for("discord")

    @property
    def moderation_schemas(self) -> tuple[NormalizedSchema, ...]:
        return self.message_schemas_for("moderation")

    @property
    def map_routes(self) -> tuple[RouteDescriptor, ...]:
        return self.route_descriptors_for("maps")

    @property
    def chat_routes(self) -> tuple[RouteDescriptor, ...]:
        return self.route_descriptors_for("chat")

    @property
    def discord_routes(self) -> tuple[RouteDescriptor, ...]:
        return self.route_descriptors_for("discord")

    @property
    def moderation_routes(self) -> tuple[RouteDescriptor, ...]:
        return self.route_descriptors_for("moderation")

    def message_schemas_for(self, family: str) -> tuple[NormalizedSchema, ...]:
        family_input = self._family_input(family)
        return family_input.message_schemas if family_input is not None else ()

    def routes_for(self, family: str) -> tuple[NormalizedRoute, ...]:
        family_input = self._family_input(family)
        return family_input.routes if family_input is not None else ()

    def route_descriptors_for(self, family: str) -> tuple[RouteDescriptor, ...]:
        return tuple(route for route in self.route_descriptors if route.family == family)

    def _family_input(self, family: str) -> FamilyGenerationInput | None:
        for family_input in self.family_inputs:
            if family_input.family == family:
                return family_input
        return None


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


def spec_root() -> Path:
    return repo_root() / "spec"


def generated_python_root() -> Path:
    return repo_root() / "python" / "xcore_protocol" / "generated"


def generated_java_root() -> Path:
    return repo_root() / "java" / "core" / "src" / "main" / "java"


def envelope_spec_root() -> Path:
    return spec_root() / "envelopes"


def _load_shared_schemas() -> tuple[NormalizedSchema, ...]:
    shared_root = spec_root() / "shared" / "types"
    return tuple(load_shared_schema(path) for path in sorted(shared_root.glob("*.json")))


def _shared_schema_index(
    shared_schemas: tuple[NormalizedSchema, ...],
) -> dict[str, NormalizedSchema]:
    return {schema.schema_id: schema for schema in shared_schemas}


def _collect_reachable_shared_schemas(
    message_schemas: tuple[NormalizedSchema, ...],
    shared_schemas: tuple[NormalizedSchema, ...],
) -> tuple[NormalizedSchema, ...]:
    shared_index = _shared_schema_index(shared_schemas)
    queue = [
        field.ref_target.ref
        for schema in message_schemas
        for field in schema.fields
        if field.ref_target is not None
    ]
    visited: set[str] = set()
    resolved: list[NormalizedSchema] = []

    while queue:
        ref = queue.pop(0)
        if ref in visited:
            continue
        visited.add(ref)

        schema = shared_index.get(ref)
        if schema is None:
            raise ValueError(f"Unknown shared schema reference in generation plan: {ref}")

        resolved.append(schema)
        queue.extend(
            field.ref_target.ref
            for field in schema.fields
            if field.ref_target is not None and field.ref_target.ref not in visited
        )

    return tuple(sorted(resolved, key=lambda schema: schema.title))


def _load_message_schemas(config: FamilyConfig) -> tuple[NormalizedSchema, ...]:
    message_root = spec_root() / config.message_dir
    return tuple(load_message_schema(path) for path in sorted(message_root.glob("*.json")))


def _load_family_routes(config: FamilyConfig) -> tuple[NormalizedRoute, ...]:
    route_manifest = spec_root() / config.route_manifest
    return load_routes(route_manifest)


def _load_route_descriptors(
    family_inputs: tuple[FamilyGenerationInput, ...],
) -> tuple[RouteDescriptor, ...]:
    descriptors: list[RouteDescriptor] = []
    for family_input in family_inputs:
        if not family_input.message_schemas:
            continue
        descriptors.extend(build_route_descriptors(family_input.routes, family_input.message_schemas))
    return tuple(descriptors)


def _load_envelope_schemas() -> tuple[NormalizedSchema, ...]:
    envelope_root = envelope_spec_root()
    envelope_paths = (
        envelope_root / "envelope-base.v1.json",
        envelope_root / "event-envelope.v1.json",
        envelope_root / "command-envelope.v1.json",
        envelope_root / "rpc-request-envelope.v1.json",
        envelope_root / "rpc-response-envelope.v1.json",
        envelope_root / "dlq-envelope.v1.json",
    )
    return tuple(load_envelope_schema(path) for path in envelope_paths)


def load_generation_plan(*, family: str | None) -> GenerationPlan:
    requested_configs = resolve_family_configs(family)
    return _build_generation_plan(requested_configs)


def load_aggregate_generation_plan() -> GenerationPlan:
    aggregate_configs: list[FamilyConfig] = []
    seen: set[str] = set()
    for entrypoint_config in entrypoint_family_configs():
        for included_config in resolve_family_configs(entrypoint_config.name):
            if included_config.name in seen:
                continue
            seen.add(included_config.name)
            aggregate_configs.append(included_config)
    return _build_generation_plan(tuple(aggregate_configs))


def _build_generation_plan(requested_configs: tuple[FamilyConfig, ...]) -> GenerationPlan:
    family_inputs = tuple(
        FamilyGenerationInput(
            family=config.name,
            config=config,
            message_schemas=_load_message_schemas(config),
            routes=_load_family_routes(config),
        )
        for config in requested_configs
    )

    all_message_schemas = tuple(
        schema for family_input in family_inputs for schema in family_input.message_schemas
    )
    shared_schemas = _collect_reachable_shared_schemas(all_message_schemas, _load_shared_schemas())
    return GenerationPlan(
        shared_schemas=shared_schemas,
        family_inputs=family_inputs,
        envelope_schemas=_load_envelope_schemas(),
        route_descriptors=_load_route_descriptors(family_inputs),
    )


def load_python_generation_plan(*, family: str | None) -> GenerationPlan:
    return load_generation_plan(family=family)


def load_java_generation_plan(*, family: str | None) -> GenerationPlan:
    return load_generation_plan(family=family)
