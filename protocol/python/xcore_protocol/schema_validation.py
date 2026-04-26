from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, ValidationError
from referencing import Registry, Resource

from .paths import spec_root


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def _build_registry() -> Registry:
    registry = Registry()
    for schema_path in spec_root().rglob("*.json"):
        schema = load_json(schema_path)
        resource = Resource.from_contents(schema)

        registry = registry.with_resource(schema_path.resolve().as_uri(), resource)

        schema_id = schema.get("$id")
        if isinstance(schema_id, str) and schema_id:
            registry = registry.with_resource(schema_id, resource)

    return registry


def make_validator(schema_path: Path) -> Draft202012Validator:
    schema = load_json(schema_path)
    registry = _build_registry()
    return Draft202012Validator(
        schema,
        registry=registry,
        format_checker=Draft202012Validator.FORMAT_CHECKER,
    )


def validate_instance(schema_path: Path, instance: Any) -> None:
    validator = make_validator(schema_path)
    validator.validate(instance)


def assert_valid(schema_path: Path, instance_path: Path) -> None:
    instance = load_json(instance_path)
    validate_instance(schema_path, instance)


def assert_invalid(schema_path: Path, instance_path: Path) -> ValidationError:
    instance = load_json(instance_path)
    validator = make_validator(schema_path)
    errors = sorted(validator.iter_errors(instance), key=lambda err: list(err.path))
    if not errors:
        raise AssertionError(f"Expected validation failure for {instance_path}, but validation passed")
    return errors[0]


def schema_path_for_message(relative_name: str) -> Path:
    return spec_root() / "messages" / "moderation" / relative_name
