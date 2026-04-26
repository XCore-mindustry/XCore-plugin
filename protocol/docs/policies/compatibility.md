# Compatibility Policy

## Canonical Rule
Canonical specs and schemas define the authoritative protocol surface. For canonical producers, they define the only official outbound payload form.

## Repository Boundary Rule
`xcore-protocol` describes only the canonical protocol surface. Temporary compatibility adapters, legacy aliases, and old payload translations belong in consumer/application repositories during migration.

Generated Java/Python protocol artifacts are part of the supported canonical consumption surface for the currently generated families: maps, chat/heartbeat, discord, and moderation. They do not replace canonical specs as the source of truth, and they must stay strict rather than absorb compatibility aliases or tolerant-reader behavior.

Compatibility glue must wrap generated canonical artifacts in consumer repos rather than altering canonical specs or generated outputs.

## Change Rule
No contract change is complete without updating:
- schema/spec
- fixtures
- validation/tests
- migration notes when relevant
