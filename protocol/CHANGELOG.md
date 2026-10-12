# Changelog

## 0.14.0

- Add maps upload RPC with typed per-file results; retain the existing load command.
- Add optional uploader and declared-creator Discord handle snapshots.
- Enforce scalar minima, minimum string lengths, patterns and minimum array sizes in generated Python model constructors and decoders, matching schema constraints.
- New readers accept 0.13 payloads; strict 0.13 readers do not accept new handle fields or upload RPC. Coordinate server/bot upgrades. Java constructors for MapDiscordUploadV1 gain two nullable fields.

## 0.13.0

- Added optional `discordUpload` to `MapFileSourceV1` and `MapEntryV1`, carrying the uploader's Discord identity, submission time in epoch milliseconds and an optional declared creator.
- Generated Java and Python `MapDiscordUploadV1` models and added valid, invalid and roundtrip fixtures.
- Existing v1 payloads without attribution remain valid. Older strict decoders reject the new field: deploy the updated bot and map servers together.
- Java record constructors for `MapEntryV1` and `MapFileSourceV1` gain an optional attribution argument. Pass `null` when no attribution is available.
