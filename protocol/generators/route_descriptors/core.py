"""Build strict route descriptors from canonical route manifests."""

from __future__ import annotations

from dataclasses import dataclass

from ..model import NormalizedRoute, NormalizedSchema
from ..naming import message_type_constant_name


@dataclass(frozen=True, slots=True)
class MessageDescriptor:
    schema_title: str
    message_type: str
    message_version: int


@dataclass(frozen=True, slots=True)
class RouteResponseDescriptor:
    message: MessageDescriptor
    stream: str


@dataclass(frozen=True, slots=True)
class RouteDescriptor:
    constant_name: str
    family: str
    method_name: str
    message: MessageDescriptor
    kind: str
    stream: str
    target_scope: str
    ttl_ms: int
    replayable: bool
    idempotent_consumer_recommended: bool
    owner: str
    response: RouteResponseDescriptor | None = None


def build_route_descriptors(
    routes: tuple[NormalizedRoute, ...],
    message_schemas: tuple[NormalizedSchema, ...],
) -> tuple[RouteDescriptor, ...]:
    schema_index = _build_schema_index(message_schemas)
    descriptors: list[RouteDescriptor] = []
    for route in routes:
        message = _message_descriptor(
            schema_index=schema_index,
            message_type=route.message_type,
            message_version=route.message_version,
        )
        response = None
        if route.response is not None:
            response = RouteResponseDescriptor(
                message=_message_descriptor(
                    schema_index=schema_index,
                    message_type=route.response.message_type,
                    message_version=route.response.message_version,
                ),
                stream=route.response.stream,
            )
        descriptors.append(
            RouteDescriptor(
                constant_name=_constant_name(message),
                family=route.family,
                method_name=_method_name(message),
                message=message,
                kind=route.kind,
                stream=route.stream,
                target_scope=route.target_scope,
                ttl_ms=route.ttl_ms,
                replayable=route.replayable,
                idempotent_consumer_recommended=route.idempotent_consumer_recommended,
                owner=route.owner,
                response=response,
            )
        )
    return tuple(descriptors)


def _build_schema_index(
    message_schemas: tuple[NormalizedSchema, ...],
) -> dict[tuple[str, int], NormalizedSchema]:
    index: dict[tuple[str, int], NormalizedSchema] = {}
    for schema in message_schemas:
        if schema.message_type is None or schema.message_version is None:
            raise ValueError(f"Message schema is missing canonical identity: {schema.title}")
        key = (schema.message_type, schema.message_version)
        index[key] = schema
    return index


def _message_descriptor(
    *,
    schema_index: dict[tuple[str, int], NormalizedSchema],
    message_type: str,
    message_version: int,
) -> MessageDescriptor:
    schema = schema_index.get((message_type, message_version))
    if schema is None:
        raise ValueError(
            "Route manifest references an unknown canonical message: "
            f"{message_type}@v{message_version}"
        )
    return MessageDescriptor(
        schema_title=schema.title,
        message_type=message_type,
        message_version=message_version,
    )


def _constant_name(message: MessageDescriptor) -> str:
    return message_type_constant_name(message.message_type, message.message_version)


def _method_name(message: MessageDescriptor) -> str:
    return f"{message.schema_title[0].lower()}{message.schema_title[1:]}Route"
