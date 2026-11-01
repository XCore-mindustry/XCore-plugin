# XCore Protocol Repository Blueprint

## Goal
Describe the structure, module boundaries, testing model, and release approach for the `xcore-protocol` repository.

## Repository Mission
`xcore-protocol` is the canonical source of truth for XCore cross-service communication artifacts.

It contains:
- protocol specs
- fixtures
- compatibility policy
- generation inputs and tooling
- generated Java and Python protocol DTO/model support
- cross-language compatibility checks

It does not contain application business logic.

## Proposed Repository Structure

```text
xcore-protocol/
  README.md
  docs/
    adr/
    architecture/
    policies/
    migrations/
  spec/
    asyncapi/
    envelopes/
    messages/
    shared/
    routes/
  fixtures/
    valid/
    invalid/
    legacy/
  generators/
    java/
    python/
  java/
    core/
    validation/
    jackson/
    testkit/
  python/
    xcore_protocol/
    tests/
  compat/
    java-python/
  scripts/
```

## Spec Layer

### Responsibilities
- AsyncAPI/channel overview
- JSON Schema message and envelope definitions
- route manifest files
- shared subtypes
- generator inputs for language bindings

### Requirements
- one canonical schema per message type/version
- no duplicate canonical field naming
- explicit message family ownership

## Fixture Layer

### Valid fixtures
Golden examples that every SDK must parse and preserve.

### Invalid fixtures
Examples that must fail strict canonical validation.

### Legacy fixtures
Historical shapes accepted only through compatibility adapters during the migration window.

## Java Modules

### `java/core`
- generated DTOs/models and metadata constants
- route descriptors
- schema references

### `java/validation`
- canonical validation against protocol definitions
- human-readable validation errors

### `java/jackson`
- serialization/deserialization helpers
- protocol-specific mapper configuration

### `java/testkit`
- fixture loaders
- golden-file assertions
- roundtrip helpers

## Python Modules

### `python/xcore_protocol`
- generated protocol models
- validation helpers
- envelope builders/parsers
- metadata constants
- fixture loading helpers

### `python/tests`
- schema validation checks
- fixture compatibility checks
- roundtrip tests

## What Stays Outside Shared SDKs
The following remain application-specific and should stay in consumer repos:

- reconnect and worker loop orchestration
- Redis connection lifecycle management
- app-specific failure recovery and backoff policy
- presentation logic
- business orchestration

## Generation Layer

### Inputs
- canonical message schemas
- shared subtype schemas
- envelope definitions
- route manifests

### Outputs
- generated Java DTO/model artifacts
- generated Python model artifacts
- metadata constants and route bindings

### Handwritten support
Thin handwritten code may wrap generated artifacts for validation, serialization setup, and fixture/test helpers, but consumer repos should not redefine the owned wire DTO layer.

## Compatibility Layer
The repository should support cross-language tests that prove:

- Java can parse canonical fixtures used by Python
- Python can parse canonical fixtures used by Java
- Java-serialized canonical payloads validate in Python
- Python-serialized canonical payloads validate in Java

## CI Requirements

### Spec validation
- JSON Schema validity
- AsyncAPI validity
- route manifest consistency

### Fixture validation
- valid fixtures pass
- invalid fixtures fail
- legacy fixtures only pass through explicit compatibility tests

### SDK validation
- Java tests
- Python tests
- Java/Python roundtrip compatibility tests

## Versioning Model

### Repository versioning
Use semantic versioning:
- major = breaking protocol changes
- minor = additive protocol changes
- patch = non-breaking fixes/docs/test updates

### Message versioning
Keep message versions independent from repository version.

## Governance Expectations
- protocol owners approve message, routing, and compatibility changes
- domain owners approve business meaning within their family
- no contract change is complete without schema, fixtures, and tests

## Adoption Model
`XCore-plugin` and `XCore-discord-bot` consume released versions of generated Java/Python protocol artifacts and use mapping layers to translate between internal models and generated protocol DTOs/models.

## Success Criteria
- A future agent can bootstrap the repository structure without inventing module boundaries.
- Shared generated SDK/model scope is clear enough to avoid turning `xcore-protocol` into a generic shared-code dump.
