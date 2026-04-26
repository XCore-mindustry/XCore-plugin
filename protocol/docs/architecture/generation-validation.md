# Generation Validation

## Goal
Define the reusable validation commands for the current generated protocol surface covering maps, chat/heartbeat, discord, and moderation artifacts.

## Current Commands

### Regenerate and check outputs
```bash
uv run python scripts/validate-generation.py
```

This command:
- regenerates the current generated outputs for all registered entrypoint families
- currently covers maps, chat/heartbeat, discord, and moderation generated artifacts
- fails if generated files are stale after generation

### Validate generated Python models
```bash
uv run python scripts/validate-generated-models.py
```

This command runs the generated-model roundtrip tests for the generated maps, chat/heartbeat, discord, and moderation slices, plus the current Java/Python compatibility checks.

### Full Python validation for the current repository state
```bash
uv run pytest
```

## CI Intent
The current CI expectation for the generated protocol surface is:
1. regenerate maps, chat/heartbeat, discord, and moderation outputs
2. confirm generated files are current
3. run generated-model tests for maps, chat/heartbeat, discord, and moderation
4. run the existing Python schema/fixture suite

## Failure Semantics
- stale generated files mean generation output was not committed or generator logic drifted
- generated-model test failures mean the typed canonical surface drifted from fixtures/specs across any registered generated family
- schema validation failures remain the authoritative signal that the canonical contract was broken

## Registry-Driven Scope
Generation and generated-model validation are registry-driven:

- `scripts/validate-generation.py` iterates the configured generator entrypoint families
- `scripts/validate-generated-models.py` runs the generated-model suites registered per family

This keeps the validation flow aligned with the currently supported generated protocol surface without hardcoding family names in the scripts.
