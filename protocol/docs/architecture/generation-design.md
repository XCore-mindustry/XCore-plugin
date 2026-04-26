# Generation Design

## Goal
Define how `xcore-protocol` generates Java and Python protocol artifacts from canonical protocol definitions without turning the repository into a runtime framework or compatibility dumping ground.

## Design Summary
- **Generator host language:** Python
- **Authored source of truth:** JSON Schema message/shared/envelope specs plus route manifests
- **Generated Java output:** immutable Java records, metadata constants, and route descriptor registries
- **Generated Python output:** frozen dataclasses, metadata constants, and route descriptor registries
- **Validation authority:** canonical JSON Schema validation remains authoritative
- **Compatibility policy:** generated canonical artifacts stay strict; legacy aliases remain in consumer-side adapters

## Why Python
Python is the best host language for the initial generator because:

1. The repository already has a Python toolchain (`pyproject.toml`, `uv`, `pytest`).
2. Current schema validation and fixture checks already run in Python.
3. JSON and YAML processing is straightforward and lightweight in Python.
4. A single Python generator can target both Java and Python outputs without introducing a third ecosystem.
5. Generator code is repository tooling, not runtime code, so Python is a good fit for fast iteration and low bootstrap cost.

## Non-Goals
The generation layer does **not** generate:
- Redis worker loops
- reconnect logic
- business orchestration
- Discord presentation code
- Mindustry integration
- legacy compatibility adapters

Those responsibilities stay in application repositories.

## Source Inputs

### Canonical inputs
The generator consumes:
- `spec/messages/**/*.json`
- `spec/shared/types/*.json`
- `spec/envelopes/*.json`
- `spec/routes/*.yaml`

### Source-of-truth rule
These files are the authored protocol surface. Generated artifacts must always be derivable from them.

## Internal Generator Model
The generator should normalize source inputs into a small internal model before writing language-specific code.

### Schema model
The normalized schema model should capture:
- schema id
- title
- message or subtype kind
- properties
- required fields
- const values for message identity
- array item references
- object references

### Route model
The normalized route model should capture:
- `messageType`
- `messageVersion`
- `kind`
- `stream`
- `targetScope`
- `ttlMs`
- `replayable`
- `idempotentConsumerRecommended`
- `owner`
- response metadata for RPC routes when present

### Supported schema subset for phase 1
The first generator version should support the subset already used in current canonical contracts:
- `type: object`
- `properties`
- `required`
- `const`
- `$ref`
- arrays of primitives or refs
- primitive types: string, integer, number, boolean
- validation metadata such as `format`, `minimum`, `minLength`, and `pattern`
- `additionalProperties: false`

If unsupported schema shapes appear later, the generator should fail loudly rather than guess.

## Output Strategy

## Java Output

### Model shape
Generate immutable Java records for canonical protocol messages and shared subtypes.

Example shape:

```java
public record MapsListRequestV1(String server) {
    public static final String MESSAGE_TYPE = "maps.list.request";
    public static final int MESSAGE_VERSION = 1;
}
```

### Why records
- concise and readable
- immutable by default
- good fit for DTO-style canonical payloads
- work well with modern Java and Jackson
- avoid Lombok or mutable bean boilerplate

### Java package layout
Generated Java output should be grouped by protocol concern:

```text
org.xcore.protocol.generated.shared
org.xcore.protocol.generated.messages.moderation
org.xcore.protocol.generated.messages.discord
org.xcore.protocol.generated.messages.maps
org.xcore.protocol.generated.messages.chat
org.xcore.protocol.generated.envelopes
org.xcore.protocol.generated.routes
```

### Handwritten Java support
Handwritten Java code may wrap generated artifacts for:
- validation helpers
- Jackson configuration
- fixture/testkit support
- registry helpers

Application repositories should depend on generated Java artifacts rather than re-declare wire DTOs.

## Python Output

### Model shape
Generate frozen Python dataclasses with slots.

Example shape:

```python
@dataclass(frozen=True, slots=True)
class MapsListRequestV1:
    server: str

    MESSAGE_TYPE: ClassVar[str] = "maps.list.request"
    MESSAGE_VERSION: ClassVar[int] = 1
```

### Why dataclasses
- no extra runtime dependency required for canonical models
- simple, inspectable generated code
- no alias or tolerant-reader behavior leaks into canonical DTOs
- keeps JSON Schema as the authoritative validator

### Python package layout

```text
xcore_protocol.generated.shared
xcore_protocol.generated.messages.moderation
xcore_protocol.generated.messages.discord
xcore_protocol.generated.messages.maps
xcore_protocol.generated.messages.chat
xcore_protocol.generated.envelopes
xcore_protocol.generated.routes
```

### Handwritten Python support
Handwritten Python code may wrap generated artifacts for:
- schema validation helpers
- fixture loaders
- compatibility/golden tests

Consumer repositories should keep compatibility parsing outside generated canonical models.

## Message Identity Strategy
Generated models should expose canonical message identity as constants instead of requiring application code to pass `messageType` and `messageVersion` manually during construction.

Serialization helpers may inject canonical identity fields into payload dictionaries. Deserialization helpers may verify them before constructing the generated model.

This keeps application usage ergonomic while preserving the strict canonical wire shape.

## Field Naming Strategy
- Preserve canonical payload field names exactly as defined in specs.
- Java generated fields use canonical camelCase names.
- Python generated fields also use canonical camelCase names, even though snake_case would be more idiomatic Python, because these are wire DTOs.

This avoids alias creep in the canonical model layer.

## Envelope Strategy
Envelope schemas are part of the canonical input set, but envelope generation may be introduced after message/shared-type generation if schema composition complexity slows down phase 1.

Recommended approach:
- generate basic envelope DTOs once the message/shared generation pipeline is stable
- keep envelope builders handwritten at first because they involve operational concerns such as message IDs, correlation IDs, producer strings, and expiration calculations

## Route Generation Strategy
Route manifests should generate:
- per-message route constants/descriptors
- a registry keyed by `(messageType, messageVersion)`

Generated route artifacts should expose:
- message kind
- stream pattern
- target scope
- TTL
- replayability
- idempotency hint
- owner
- RPC response metadata where relevant

The route manifest remains the source of truth. Application repositories should eventually consume generated route metadata rather than duplicate route descriptors manually.

## Validation Strategy

### Canonical validation authority
JSON Schema validation remains authoritative for canonical correctness.

Generated models do not replace schema validation; they provide a typed consumption surface.

### Generated model tests
The generation layer should add tests for:
- loading valid fixtures into generated models
- serializing generated models back to canonical payload dictionaries
- validating generated output against canonical schemas
- route registry correctness

### Cross-language checks
Compatibility tests should continue proving that Java and Python stay aligned on:
- canonical fixtures
- route metadata interpretation
- serialized output for generated canonical artifacts

## File Generation Policy
Generated files should be committed to the repository.

Benefits:
- diffs make protocol changes reviewable
- consumers can depend on released/generated source immediately
- repository state stays bootstrappable without extra generation steps before inspection

The repository should also provide a check command that fails when generated files are stale.

## Proposed Generator Layout

```text
generators/
  README.md
  xcore_protocol_codegen/
    __init__.py
    cli.py
    discovery.py
    schema_model.py
    route_model.py
    naming.py
    java_writer.py
    python_writer.py
  tests/
```

## CLI Shape
Recommended initial commands:

```bash
uv run python -m xcore_protocol_codegen generate
uv run python -m xcore_protocol_codegen generate --language python
uv run python -m xcore_protocol_codegen generate --language java
uv run python -m xcore_protocol_codegen generate --family maps
uv run python -m xcore_protocol_codegen check
```

## Rollout Plan

### Phase 1
- build the generator in Python
- generate shared types and maps message family
- generate maps route bindings
- add Python generated-model tests

### Why maps first
Maps is a good proof slice because it is:
- small and bounded
- already canonicalized
- structurally varied enough to test requests, responses, arrays, and shared refs
- less domain-heavy than moderation

### Phase 2
- extend generation to chat/heartbeat and discord families

### Phase 3
- extend generation to moderation family
- add richer cross-language compatibility checks

### Phase 4
- add envelope DTO generation if still useful after message/shared generation is stable

## Decision Summary
- Use **Python** for generator implementation.
- Keep **JSON Schema and route manifests** as authored source inputs.
- Generate **Java records** and **Python frozen dataclasses**.
- Keep **schema validation authoritative**.
- Keep **compatibility adapters outside generated canonical models**.
- Start with **maps** as the first proof slice.
