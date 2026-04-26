# Repository Layout

## Top-Level Areas
- `docs/` — decisions, architecture, policies, migration notes
- `spec/` — source-of-truth schemas and route definitions used as generator inputs
- `fixtures/` — valid, invalid, and legacy protocol examples
- `generators/` — code generation configuration and related tooling
- `java/` — generated Java SDK/model output for the canonical protocol surface
- `python/` — generated Python SDK/model output and thin validation helpers
- `compat/` — cross-language compatibility checks
- `scripts/` — repository tooling and validation entrypoints

## Current Intent
Start small, keep the scope narrow, and avoid turning this repository into a generic shared-code dump.

Generated DTO/model artifacts are part of the supported protocol surface, but runtime application logic remains out of scope.
