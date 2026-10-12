# Changelog

## 0.13.0

- Added optional `discordUpload` to `MapFileSourceV1` and `MapEntryV1`, carrying the uploader's Discord identity, submission time in epoch milliseconds and an optional declared creator.
- Generated Java and Python `MapDiscordUploadV1` models and added valid, invalid and roundtrip fixtures.
- Existing v1 payloads without attribution remain valid. Older strict decoders reject the new field: deploy the updated bot and map servers together.
- Java record constructors for `MapEntryV1` and `MapFileSourceV1` gain an optional attribution argument. Pass `null` when no attribution is available.
