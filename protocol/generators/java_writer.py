"""Write generated Java protocol models for the supported subset."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from .discovery import GenerationPlan, generated_java_root
from .families import FamilyConfig, require_family_config
from .model import FieldShape, FieldType, NormalizedField, NormalizedSchema
from .route_descriptors import RouteDescriptor

SHARED_PACKAGE = "org.xcore.protocol.generated.shared"
ROUTES_PACKAGE = "org.xcore.protocol.generated.routes"
ENVELOPES_PACKAGE = "org.xcore.protocol.generated.envelopes"


@dataclass(frozen=True, slots=True)
class GeneratedFile:
    path: Path
    content: str


def write_java_outputs(plan: GenerationPlan) -> tuple[Path, ...]:
    outputs = render_java_outputs(plan)
    written: list[Path] = []
    for output in outputs:
        output.path.parent.mkdir(parents=True, exist_ok=True)
        output.path.write_text(output.content, encoding="utf-8")
        written.append(output.path)
    return tuple(written)


def check_java_outputs(plan: GenerationPlan) -> tuple[Path, ...]:
    stale_paths: list[Path] = []
    for output in render_java_outputs(plan):
        if not output.path.exists() or output.path.read_text(encoding="utf-8") != output.content:
            stale_paths.append(output.path)
    return tuple(stale_paths)


def render_java_outputs(plan: GenerationPlan) -> tuple[GeneratedFile, ...]:
    output_root = generated_java_root()
    shared_root = output_root / "org" / "xcore" / "protocol" / "generated" / "shared"
    routes_root = output_root / "org" / "xcore" / "protocol" / "generated" / "routes"
    envelopes_root = output_root / "org" / "xcore" / "protocol" / "generated" / "envelopes"
    outputs: list[GeneratedFile] = []

    for schema in plan.shared_schemas:
        outputs.append(
            GeneratedFile(
                path=shared_root / f"{schema.title}.java",
                content=_render_shared_record(schema),
            )
        )

    for schema in plan.envelope_schemas:
        outputs.append(
            GeneratedFile(
                path=envelopes_root / f"{schema.title}.java",
                content=_render_envelope_record(schema),
            )
        )

    for family_input in plan.family_inputs:
        if not family_input.message_schemas:
            continue
        messages_root = _java_messages_root(output_root, family_input.config)
        outputs.append(
            GeneratedFile(
                path=messages_root / f"{family_input.config.java_messages_class}.java",
                content=_render_family_container(
                    package_name=family_input.config.java_package,
                    container_name=family_input.config.java_messages_class,
                    schemas=family_input.message_schemas,
                ),
            )
        )

    for family_input in plan.family_inputs:
        routes = plan.route_descriptors_for(family_input.family)
        if not routes:
            continue
        outputs.append(
            GeneratedFile(
                path=routes_root / f"{family_input.config.java_routes_class}.java",
                content=_render_family_routes(
                    class_name=family_input.config.java_routes_class,
                    schema_family_index=_schema_family_index(plan),
                    config=family_input.config,
                    routes=routes,
                ),
            )
        )

    return tuple(outputs)


def _schema_family_index(plan: GenerationPlan) -> dict[str, str]:
    return {
        schema.title: family_input.family
        for family_input in plan.family_inputs
        for schema in family_input.message_schemas
    }


def _java_messages_root(output_root: Path, config: FamilyConfig) -> Path:
    package_parts = config.java_package.split(".")
    return output_root.joinpath(*package_parts)


def _render_shared_record(schema: NormalizedSchema) -> str:
    components = ",\n".join(f"        {_component_declaration(field)}" for field in schema.fields if field.const is None)
    imports = {"import java.util.Objects;"}
    if any(field.shape == FieldShape.MAP for field in schema.fields if field.const is None):
        imports.add("import java.util.Map;")
    return (
        f"package {SHARED_PACKAGE};\n\n"
        + "\n".join(sorted(imports))
        + "\n\n"
        f"public record {schema.title}(\n"
        f"{components}\n"
        ") {\n"
        f"{_render_compact_constructor(schema, indent='    ')}\n"
        "}\n"
    )


def _render_envelope_record(schema: NormalizedSchema) -> str:
    components = tuple(field for field in schema.fields if field.const is None)
    declaration = ",\n".join(f"        {_component_declaration(field)}" for field in components)
    imports = {"import java.util.Objects;"}
    if any(field.shape == FieldShape.MAP for field in components):
        imports.add("import java.util.Map;")
    if any(field.shape == FieldShape.ARRAY for field in components):
        imports.add("import java.util.List;")
    constants = _render_java_const_fields(schema.fields)
    return (
        f"package {ENVELOPES_PACKAGE};\n\n"
        + "\n".join(sorted(imports))
        + "\n\n"
        + f"public record {schema.title}(\n"
        + f"{declaration}\n"
        + ") {\n"
        + (constants + "\n\n" if constants else "")
        + f"{_render_compact_constructor(schema, indent='    ')}\n"
        + "}\n"
    )


def _render_family_container(
    *,
    package_name: str,
    container_name: str,
    schemas: tuple[NormalizedSchema, ...],
) -> str:
    nested_records = "\n\n".join(_render_nested_message_record(schema) for schema in schemas)
    imports = _render_family_imports(schemas)
    return (
        f"package {package_name};\n\n"
        + imports
        + f"public final class {container_name} {{\n"
        + f"    private {container_name}() {{\n"
        + f'        throw new AssertionError("No {package_name}.{container_name} instances");\n'
        "    }\n\n"
        + f"{nested_records}\n"
        "}\n"
    )


def _render_family_imports(schemas: tuple[NormalizedSchema, ...]) -> str:
    imports = {"import java.util.Objects;"}
    if any(field.shape == FieldShape.ARRAY for schema in schemas for field in schema.fields if field.const is None):
        imports.add("import java.util.List;")
    if any(field.shape == FieldShape.MAP for schema in schemas for field in schema.fields if field.const is None):
        imports.add("import java.util.Map;")
    imports.update(
        f"import {SHARED_PACKAGE}.{field.ref_target.title};"
        for schema in schemas
        for field in schema.fields
        if field.const is None and field.ref_target is not None
    )
    return "\n".join(sorted(imports)) + "\n\n"


def _render_nested_message_record(schema: NormalizedSchema) -> str:
    components = tuple(field for field in schema.fields if field.const is None)
    declaration = ",\n".join(f"            {_component_declaration(field)}" for field in components)
    message_type = schema.message_type
    message_version = schema.message_version
    if message_type is None or message_version is None:
        raise ValueError(f"Message schema missing identity: {schema.title}")
    return (
        f"    public record {schema.title}(\n"
        f"{declaration}\n"
        "    ) {\n"
        f"        public static final String MESSAGE_TYPE = \"{message_type}\";\n"
        f"        public static final int MESSAGE_VERSION = {message_version};\n\n"
        f"{_render_compact_constructor(schema, indent='        ')}\n"
        "    }"
    )


def _render_family_routes(
    *,
    config: FamilyConfig,
    class_name: str,
    routes: tuple[RouteDescriptor, ...],
    schema_family_index: dict[str, str],
) -> str:
    route_imports = _render_route_imports(routes, schema_family_index)
    constants = "\n\n".join(
        _render_route_constant(route, schema_family_index=schema_family_index) for route in routes
    )
    index_entries = ",\n".join(
        f'            entry(key("{route.message.message_type}", {route.message.message_version}), {route.constant_name})'
        for route in routes
    )
    maps_alias_block = _render_java_route_aliases(config)
    return (
        f"package {ROUTES_PACKAGE};\n\n"
        "import static java.util.Map.entry;\n\n"
        "import java.util.Map;\n"
        + route_imports
        + "\n"
        + f"public final class {class_name} {{\n"
        + f"    private {class_name}() {{\n"
        + f'        throw new AssertionError("No org.xcore.protocol.generated.routes.{class_name} instances");\n'
        "    }\n\n"
        "    public record MessageKey(String messageType, int messageVersion) {}\n\n"
        "    public record RouteResponseDescriptor(\n"
        "            String messageType,\n"
        "            int messageVersion,\n"
        "            Class<?> payloadType,\n"
        "            String stream\n"
        "    ) {}\n\n"
        "    public record RouteDescriptor(\n"
        "            String family,\n"
        "            String methodName,\n"
        "            String messageType,\n"
        "            int messageVersion,\n"
        "            Class<?> payloadType,\n"
        "            String kind,\n"
        "            String stream,\n"
        "            String targetScope,\n"
        "            int ttlMs,\n"
        "            boolean replayable,\n"
        "            boolean idempotentConsumerRecommended,\n"
        "            String owner,\n"
        "            RouteResponseDescriptor response\n"
        "    ) {}\n\n"
        + constants
        + "\n\n    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(\n"
        + index_entries
        + "\n    );"
        + maps_alias_block
        + "\n\n"
        "    private static MessageKey key(String messageType, int messageVersion) {\n"
        "        return new MessageKey(messageType, messageVersion);\n"
        "    }\n"
        "}\n"
    )


def _render_route_imports(
    routes: tuple[RouteDescriptor, ...],
    schema_family_index: dict[str, str],
) -> str:
    imports = set()
    for route in routes:
        imports.add(_message_container_import(route.message.schema_title, schema_family_index))
        if route.response is not None:
            imports.add(_message_container_import(route.response.message.schema_title, schema_family_index))
    if not imports:
        return ""
    return "\n".join(sorted(imports)) + "\n"


def _message_container_import(schema_title: str, schema_family_index: dict[str, str]) -> str:
    family = schema_family_index[schema_title]
    config = _family_config_for_name(family)
    container_name = config.java_messages_class
    package_name = config.java_package
    return f"import {package_name}.{container_name};"


def _family_container_name(family: str) -> str:
    return _family_config_for_name(family).java_messages_class


def _family_package(family: str) -> str:
    return _family_config_for_name(family).java_package


def _family_config_for_name(family: str) -> FamilyConfig:
    return require_family_config(family)


def _render_java_route_aliases(config: FamilyConfig) -> str:
    if "MAPS_ROUTES_BY_MESSAGE" not in config.route_aliases:
        return ""
    return (
        "\n\n"
        "    public static final Map<MessageKey, RouteDescriptor> MAPS_ROUTES_BY_MESSAGE = ROUTES_BY_MESSAGE;"
    )


def _render_route_constant(
    route: RouteDescriptor,
    *,
    schema_family_index: dict[str, str],
) -> str:
    response = "null"
    if route.response is not None:
        response_family = schema_family_index[route.response.message.schema_title]
        response = (
            "new RouteResponseDescriptor(\n"
            f'                    "{route.response.message.message_type}",\n'
            f"                    {route.response.message.message_version},\n"
            f"                    {_message_payload_type(route.response.message.schema_title, response_family)}.class,\n"
            f'                    "{route.response.stream}"\n'
            "            )"
        )
    return (
        f"    public static final RouteDescriptor {route.constant_name} = new RouteDescriptor(\n"
        f'            "{route.family}",\n'
        f'            "{route.method_name}",\n'
        f'            "{route.message.message_type}",\n'
        f"            {route.message.message_version},\n"
        f"            {_message_payload_type(route.message.schema_title, route.family)}.class,\n"
        f'            "{route.kind}",\n'
        f'            "{route.stream}",\n'
        f'            "{route.target_scope}",\n'
        f"            {route.ttl_ms},\n"
        f"            {str(route.replayable).lower()},\n"
        f"            {str(route.idempotent_consumer_recommended).lower()},\n"
        f'            "{route.owner}",\n'
        f"            {response}\n"
        "    );"
    )


def _message_payload_type(schema_title: str, family: str) -> str:
    return f"{_family_container_name(family)}.{schema_title}"


def _render_compact_constructor(schema: NormalizedSchema, *, indent: str) -> str:
    lines = [f"{indent}public {schema.title} {{"]
    validations = _render_field_validations(schema, indent=indent + "    ")
    if validations:
        lines.extend(validations)
    lines.append(f"{indent}}}")
    return "\n".join(lines)


def _render_field_validations(schema: NormalizedSchema, *, indent: str) -> list[str]:
    validations: list[str] = []
    for field in schema.fields:
        if field.const is not None:
            continue
        validations.extend(_field_validation_lines(field, indent=indent))
    return validations


def _field_validation_lines(field: NormalizedField, *, indent: str) -> list[str]:
    field_name = field.name
    if field.shape == FieldShape.MAP:
        map_lines = [
            f'{indent}{field_name} = Objects.requireNonNull({field_name}, "{field_name} must not be null");',
            f"{indent}for (Map.Entry<String, Object> entry : {field_name}.entrySet()) {{",
            *_map_entry_validation_lines(field, indent=indent + "    "),
            f"{indent}}}",
            f"{indent}{field_name} = {_java_map_copy_expression(field_name, allows_null=field.map_allows_null)};",
        ]
        if field.required:
            return map_lines
        return [
            f"{indent}if ({field_name} != null) {{",
            *[line.replace(indent, indent + "    ", 1) for line in map_lines],
            f"{indent}}}",
        ]
    if field.shape == FieldShape.ARRAY:
        item_type = _array_item_type(field)
        lines = [
            f'{indent}{field_name} = Objects.requireNonNull({field_name}, "{field_name} must not be null");',
            f"{indent}{field_name} = List.copyOf({field_name});",
            f"{indent}for ({item_type} item : {field_name}) {{",
        ]
        item_checks = _value_validation_lines(
            field,
            value_expression="item",
            field_name=f"{field_name}[]",
            indent=indent + "    ",
            for_array_item=True,
        )
        if field.min_items is not None:
            lines.insert(
                2,
                f"{indent}if ({field_name}.size() < {field.min_items}) {{\n"
                f"{indent}    throw new IllegalArgumentException(\"{field_name} must contain at least {field.min_items} item(s)\");\n"
                f"{indent}}}",
            )
        if item_checks:
            lines.extend(item_checks)
        lines.append(f"{indent}}}")
        if field.required:
            return lines
        return [
            f"{indent}if ({field_name} != null) {{",
            *[line.replace(indent, indent + "    ", 1) for line in lines],
            f"{indent}}}",
        ]

    checks = _value_validation_lines(
        field,
        value_expression=field_name,
        field_name=field_name,
        indent=indent,
        for_array_item=False,
    )
    if field.required:
        return checks
    if not checks:
        return []
    return [
        f"{indent}if ({field_name} != null) {{",
        *[line.replace(indent, indent + "    ", 1) for line in checks],
        f"{indent}}}",
    ]


def _value_validation_lines(
    field: NormalizedField,
    *,
    value_expression: str,
    field_name: str,
    indent: str,
    for_array_item: bool,
) -> list[str]:
    lines: list[str] = []
    if field.field_type == FieldType.OBJECT_REF:
        lines.append(f'{indent}Objects.requireNonNull({value_expression}, "{field_name} must not be null");')
        return lines

    if field.field_type == FieldType.STRING:
        lines.append(f'{indent}Objects.requireNonNull({value_expression}, "{field_name} must not be null");')
        if field.min_length is not None:
            lines.append(
                f'{indent}if ({value_expression}.length() < {field.min_length}) {{'
            )
            lines.append(
                f'{indent}    throw new IllegalArgumentException("{field_name} must be at least {field.min_length} characters");'
            )
            lines.append(f"{indent}}}")
        return lines

    if field.field_type in {FieldType.INTEGER, FieldType.NUMBER} and field.minimum is not None:
        comparator = _numeric_comparator(value_expression, field.field_type, field.minimum)
        lines.append(f"{indent}if ({comparator}) {{")
        lines.append(
            f'{indent}    throw new IllegalArgumentException("{field_name} must be >= {field.minimum}");'
        )
        lines.append(f"{indent}}}")
        return lines

    if field.field_type == FieldType.BOOLEAN and not for_array_item:
        return []

    return lines


def _numeric_comparator(value_expression: str, field_type: FieldType, minimum: int | float) -> str:
    value = repr(float(minimum)) if field_type == FieldType.NUMBER else str(int(minimum))
    if field_type == FieldType.NUMBER:
        return f"Double.compare({value_expression}, {value}) < 0"
    return f"{value_expression} < {value}"


def _component_declaration(field: NormalizedField) -> str:
    return f"{_component_type(field)} {field.name}"


def _component_type(field: NormalizedField) -> str:
    if field.shape == FieldShape.ARRAY:
        return f"List<{_base_type(field)}>"
    if field.shape == FieldShape.MAP:
        return "Map<String, Object>"
    return _base_type(field)


def _array_item_type(field: NormalizedField) -> str:
    if field.shape != FieldShape.ARRAY:
        raise ValueError(f"Field is not an array: {field}")
    return _base_type(field)


def _base_type(field: NormalizedField) -> str:
    if field.field_type == FieldType.STRING:
        return "String"
    if field.field_type == FieldType.INTEGER:
        return "Integer" if not field.required else "int"
    if field.field_type == FieldType.NUMBER:
        return "Double" if not field.required else "double"
    if field.field_type == FieldType.BOOLEAN:
        return "Boolean" if not field.required else "boolean"
    if field.field_type == FieldType.OBJECT_REF and field.ref_target is not None:
        return field.ref_target.title
    raise ValueError(f"Unsupported field type for Java generation: {field}")


def _render_java_const_fields(fields: tuple[NormalizedField, ...]) -> str:
    lines: list[str] = []
    for field in fields:
        if field.const is None:
            continue
        const_type, const_literal = _java_const_declaration(field.const)
        lines.append(
            f"    public static final {const_type} {_java_const_field_name(field.name)} = {const_literal};"
        )
    return "\n".join(lines)


def _java_const_declaration(value: str | int | float | bool | None) -> tuple[str, str]:
    if isinstance(value, bool):
        return "boolean", str(value).lower()
    if isinstance(value, int):
        return "int", str(value)
    if isinstance(value, float):
        return "double", repr(value)
    if isinstance(value, str):
        return "String", f'"{value}"'
    raise ValueError(f"Unsupported Java const value: {value!r}")


def _java_const_field_name(field_name: str) -> str:
    normalized = field_name.replace("-", "_")
    result: list[str] = []
    for index, char in enumerate(normalized):
        if char == "_":
            result.append("_")
            continue
        if char.isupper() and index > 0 and normalized[index - 1] != "_":
            result.append("_")
        result.append(char.upper())
    return "".join(result)


def _java_map_copy_expression(field_name: str, *, allows_null: bool) -> str:
    if allows_null:
        return (
            f"java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>({field_name}))"
        )
    return f"Map.copyOf({field_name})"


def _map_entry_validation_lines(field: NormalizedField, *, indent: str) -> list[str]:
    lines: list[str] = []
    lines.append(f"{indent}Objects.requireNonNull(entry.getKey(), \"{field.name} keys must not be null\");")
    if field.map_allows_null:
        lines.append(f"{indent}if (entry.getValue() == null) {{")
        lines.append(f"{indent}    continue;")
        lines.append(f"{indent}}}")
    else:
        lines.append(
            f"{indent}Objects.requireNonNull(entry.getValue(), \"{field.name} values must not be null\");"
        )

    allowed_checks = " && ".join(
        _java_map_value_check(value_type, expression="entry.getValue()")
        for value_type in field.map_value_types
    )
    if allowed_checks:
        lines.append(f"{indent}if ({allowed_checks}) {{")
        allowed_types = ", ".join(_java_map_value_label(value_type) for value_type in field.map_value_types)
        if field.map_allows_null:
            allowed_types += ", null"
        lines.append(
            f'{indent}    throw new IllegalArgumentException("{field.name} values must be one of: {allowed_types}");'
        )
        lines.append(f"{indent}}}")
    return lines


def _java_map_value_check(value_type: FieldType, *, expression: str) -> str:
    if value_type == FieldType.STRING:
        return f"!({expression} instanceof String)"
    if value_type == FieldType.INTEGER:
        return f"!({expression} instanceof Integer)"
    if value_type == FieldType.NUMBER:
        return f"!({expression} instanceof Number)"
    if value_type == FieldType.BOOLEAN:
        return f"!({expression} instanceof Boolean)"
    raise ValueError(f"Unsupported Java map value type: {value_type}")


def _java_map_value_label(value_type: FieldType) -> str:
    if value_type == FieldType.STRING:
        return "string"
    if value_type == FieldType.INTEGER:
        return "integer"
    if value_type == FieldType.NUMBER:
        return "number"
    if value_type == FieldType.BOOLEAN:
        return "boolean"
    raise ValueError(f"Unsupported Java map value label: {value_type}")
