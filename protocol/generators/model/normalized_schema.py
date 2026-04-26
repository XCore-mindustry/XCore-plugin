"""Strict normalized schema model for generation."""

from __future__ import annotations

import json
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any


class FieldShape(str, Enum):
    SCALAR = "scalar"
    ARRAY = "array"
    MAP = "map"


class FieldType(str, Enum):
    STRING = "string"
    INTEGER = "integer"
    NUMBER = "number"
    BOOLEAN = "boolean"
    OBJECT_REF = "object_ref"
    JSON_VALUE = "json_value"


@dataclass(frozen=True, slots=True)
class RefTarget:
    ref: str
    title: str


@dataclass(frozen=True, slots=True)
class NormalizedField:
    name: str
    required: bool
    shape: FieldShape
    field_type: FieldType
    const: str | int | float | bool | None = None
    ref_target: RefTarget | None = None
    format: str | None = None
    pattern: str | None = None
    minimum: int | float | None = None
    min_length: int | None = None
    min_items: int | None = None
    map_value_types: tuple[FieldType, ...] = ()
    map_allows_null: bool = False


@dataclass(frozen=True, slots=True)
class NormalizedSchema:
    schema_id: str
    title: str
    kind: str
    family: str
    message_type: str | None
    message_version: int | None
    fields: tuple[NormalizedField, ...]


def _load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError(f"Schema must be an object: {path}")
    return data


def _expect_object_schema(raw: dict[str, Any], path: Path) -> None:
    if raw.get("type") != "object":
        raise ValueError(f"Only object schemas are supported: {path}")
    if raw.get("additionalProperties") is not False:
        raise ValueError(f"Schema must disable additionalProperties: {path}")


def _expect_envelope_schema(raw: dict[str, Any], path: Path) -> None:
    if raw.get("type") != "object":
        raise ValueError(f"Only object envelope schemas are supported: {path}")


def _schema_title_from_ref(ref: str) -> str:
    tail = ref.rsplit(":", maxsplit=2)[-2:]
    return "".join(part.replace("-", " ").title().replace(" ", "") for part in tail)


def _normalize_scalar_field(
    *,
    name: str,
    required: bool,
    definition: dict[str, Any],
) -> NormalizedField:
    if "$ref" in definition:
        ref = definition["$ref"]
        if not isinstance(ref, str):
            raise ValueError(f"Invalid $ref for field {name}")
        return NormalizedField(
            name=name,
            required=required,
            shape=FieldShape.SCALAR,
            field_type=FieldType.OBJECT_REF,
            ref_target=RefTarget(ref=ref, title=_schema_title_from_ref(ref)),
        )

    json_type = definition.get("type")
    const = definition.get("const")
    if json_type is None and const is not None:
        if isinstance(const, bool):
            json_type = FieldType.BOOLEAN.value
        elif isinstance(const, int):
            json_type = FieldType.INTEGER.value
        elif isinstance(const, float):
            json_type = FieldType.NUMBER.value
        elif isinstance(const, str):
            json_type = FieldType.STRING.value

    try:
        field_type = FieldType(json_type)
    except ValueError as exc:
        raise ValueError(f"Unsupported scalar type for field {name}: {json_type}") from exc

    if const is not None and not isinstance(const, (str, int, float, bool)):
        raise ValueError(f"Unsupported const for field {name}: {const!r}")

    minimum = definition.get("minimum")
    if minimum is not None and not isinstance(minimum, (int, float)):
        raise ValueError(f"Unsupported minimum for field {name}: {minimum!r}")

    min_length = definition.get("minLength")
    if min_length is not None and not isinstance(min_length, int):
        raise ValueError(f"Unsupported minLength for field {name}: {min_length!r}")

    field_format = definition.get("format")
    if field_format is not None and not isinstance(field_format, str):
        raise ValueError(f"Unsupported format for field {name}: {field_format!r}")

    pattern = definition.get("pattern")
    if pattern is not None and not isinstance(pattern, str):
        raise ValueError(f"Unsupported pattern for field {name}: {pattern!r}")

    return NormalizedField(
        name=name,
        required=required,
        shape=FieldShape.SCALAR,
        field_type=field_type,
        const=const,
        format=field_format,
        pattern=pattern,
        minimum=minimum,
        min_length=min_length,
    )


def _normalize_array_field(
    *,
    name: str,
    required: bool,
    definition: dict[str, Any],
) -> NormalizedField:
    items = definition.get("items")
    if not isinstance(items, dict):
        raise ValueError(f"Array field {name} must define object items")

    min_items = definition.get("minItems")
    if min_items is not None and not isinstance(min_items, int):
        raise ValueError(f"Unsupported minItems for field {name}: {min_items!r}")

    if "$ref" in items:
        ref = items["$ref"]
        if not isinstance(ref, str):
            raise ValueError(f"Invalid array $ref for field {name}")
        return NormalizedField(
            name=name,
            required=required,
            shape=FieldShape.ARRAY,
            field_type=FieldType.OBJECT_REF,
            ref_target=RefTarget(ref=ref, title=_schema_title_from_ref(ref)),
            min_items=min_items,
        )

    item_type = items.get("type")
    try:
        field_type = FieldType(item_type)
    except ValueError as exc:
        raise ValueError(f"Unsupported array item type for field {name}: {item_type}") from exc

    return NormalizedField(
        name=name,
        required=required,
        shape=FieldShape.ARRAY,
        field_type=field_type,
        min_items=min_items,
    )


def _normalize_map_field(
    *,
    name: str,
    required: bool,
    definition: dict[str, Any],
) -> NormalizedField:
    additional_properties = definition.get("additionalProperties")
    if not isinstance(additional_properties, dict):
        raise ValueError(f"Map field {name} must define object additionalProperties")

    raw_value_types = additional_properties.get("type")
    if isinstance(raw_value_types, str):
        raw_types = (raw_value_types,)
    elif isinstance(raw_value_types, list):
        raw_types = tuple(raw_value_types)
    else:
        raise ValueError(f"Map field {name} must define primitive additionalProperties types")

    value_types: list[FieldType] = []
    allows_null = False
    for raw_type in raw_types:
        if raw_type == "null":
            allows_null = True
            continue
        try:
            field_type = FieldType(raw_type)
        except ValueError as exc:
            raise ValueError(
                f"Unsupported map value type for field {name}: {raw_type}"
            ) from exc
        if field_type in {FieldType.OBJECT_REF, FieldType.JSON_VALUE}:
            raise ValueError(f"Unsupported nested map value type for field {name}: {raw_type}")
        value_types.append(field_type)

    if not value_types and not allows_null:
        raise ValueError(f"Map field {name} must allow at least one primitive or null value type")

    return NormalizedField(
        name=name,
        required=required,
        shape=FieldShape.MAP,
        field_type=FieldType.JSON_VALUE,
        map_value_types=tuple(value_types),
        map_allows_null=allows_null,
    )


def _normalize_field(
    *,
    name: str,
    required: bool,
    definition: dict[str, Any],
) -> NormalizedField:
    if definition.get("type") == "array":
        return _normalize_array_field(name=name, required=required, definition=definition)
    if definition.get("type") == "object" and "additionalProperties" in definition and "$ref" not in definition:
        return _normalize_map_field(name=name, required=required, definition=definition)
    return _normalize_scalar_field(name=name, required=required, definition=definition)


def _normalize_schema(path: Path, *, kind: str) -> NormalizedSchema:
    raw = _load_json(path)
    _expect_object_schema(raw, path)

    return _normalize_schema_from_raw(raw, path=path, kind=kind, family=path.parent.name)


def _normalize_schema_from_raw(
    raw: dict[str, Any],
    *,
    path: Path,
    kind: str,
    family: str,
) -> NormalizedSchema:

    schema_id = raw.get("$id")
    title = raw.get("title")
    properties = raw.get("properties")
    required_fields = set(raw.get("required", []))

    if not isinstance(schema_id, str) or not schema_id:
        raise ValueError(f"Schema must define non-empty $id: {path}")
    if not isinstance(title, str) or not title:
        raise ValueError(f"Schema must define non-empty title: {path}")
    if not isinstance(properties, dict):
        raise ValueError(f"Schema must define properties object: {path}")

    normalized_fields = tuple(
        _normalize_field(
            name=field_name,
            required=field_name in required_fields,
            definition=field_definition,
        )
        for field_name, field_definition in properties.items()
        if isinstance(field_definition, dict)
    )

    message_type = None
    message_version = None
    for field in normalized_fields:
        if field.name == "messageType":
            if not isinstance(field.const, str):
                raise ValueError(f"messageType must be a string const: {path}")
            message_type = field.const
        if field.name == "messageVersion":
            if not isinstance(field.const, int):
                raise ValueError(f"messageVersion must be an int const: {path}")
            message_version = field.const

    if kind == "message" and (message_type is None or message_version is None):
        raise ValueError(f"Message schema must define messageType/messageVersion consts: {path}")

    return NormalizedSchema(
        schema_id=schema_id,
        title=title,
        kind=kind,
        family=family,
        message_type=message_type,
        message_version=message_version,
        fields=normalized_fields,
    )


def load_message_schema(path: Path) -> NormalizedSchema:
    return _normalize_schema(path, kind="message")


def load_shared_schema(path: Path) -> NormalizedSchema:
    return _normalize_schema(path, kind="shared")


def load_envelope_schema(path: Path) -> NormalizedSchema:
    raw = _load_json(path)
    _expect_envelope_schema(raw, path)
    flattened = _flatten_envelope_schema(raw, path=path)
    return _normalize_schema_from_raw(flattened, path=path, kind="envelope", family="envelopes")


def _flatten_envelope_schema(raw: dict[str, Any], *, path: Path) -> dict[str, Any]:
    all_of = raw.get("allOf")
    if not isinstance(all_of, list):
        return raw

    schema_index = _load_envelope_schema_index(path.parent)
    merged_required: list[str] = []
    merged_properties: dict[str, Any] = {}

    for item in all_of:
        if not isinstance(item, dict):
            raise ValueError(f"Unsupported allOf entry in envelope schema: {path}")
        resolved = _resolve_envelope_schema_fragment(item, schema_index=schema_index, path=path)
        for required_name in resolved.get("required", []):
            if required_name not in merged_required:
                merged_required.append(required_name)
        properties = resolved.get("properties", {})
        if not isinstance(properties, dict):
            raise ValueError(f"Envelope allOf entry must define object properties: {path}")
        merged_properties.update(properties)

    return {
        "$schema": raw.get("$schema"),
        "$id": raw.get("$id"),
        "title": raw.get("title"),
        "type": "object",
        "required": merged_required,
        "properties": merged_properties,
        "additionalProperties": False,
    }


def _load_envelope_schema_index(envelope_root: Path) -> dict[str, dict[str, Any]]:
    schema_index: dict[str, dict[str, Any]] = {}
    for schema_path in sorted(envelope_root.glob("*.json")):
        schema = _load_json(schema_path)
        schema_id = schema.get("$id")
        if isinstance(schema_id, str) and schema_id:
            schema_index[schema_id] = schema
    return schema_index


def _resolve_envelope_schema_fragment(
    fragment: dict[str, Any],
    *,
    schema_index: dict[str, dict[str, Any]],
    path: Path,
) -> dict[str, Any]:
    if "$ref" not in fragment:
        return fragment

    ref = fragment["$ref"]
    if not isinstance(ref, str):
        raise ValueError(f"Envelope $ref must be a string: {path}")
    target = schema_index.get(ref)
    if target is None:
        raise ValueError(f"Unknown envelope schema reference: {ref}")
    return _flatten_envelope_schema(target, path=path)
