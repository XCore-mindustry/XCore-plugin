# Changelog

## 0.16.0

- Remove `discord.admin-access.changed.command` v1 and `player.password-reset.command` v1, with their routes, generated Java/Python models and fixtures. Staff rights come only from grants: Discord roles reach the servers through `security.staff.sync`, password resets through `security.staff.reset-password`.
- Breaking for consumers that still reference `DiscordAdminAccessChangedCommandV1`, `PlayerPasswordResetCommandV1` or their route constants; upgrade the bot and the game servers to releases without them first.

## 0.15.0

- Add the cases family: durable report events, notice wake-up commands and live evidence RPC, with generated Java/Python models and routes.
- Add detached map inspection RPC with typed metadata, status and findings; reason and finding codes remain open strings.
- Add shared case chat, block summary and map inspection types. Nested block entries and areas use named shared types; chat PIDs retain the repository's signed PID policy.
- Map inspection counts and dimensions, and per-block action counts, are nonnegative. Inspected map `name`/`author` are capped at 256 characters, `description` at 1024 and `mods` at 32 entries; servers truncate metadata before sending. Evidence responses carry at most 30 chat lines.
- Add optional positive `banId` to `moderation.ban.created` v1. Existing payloads without it remain valid; the Java constructor gains a final nullable `Integer` argument.
- Deploy 0.15.0 bot readers before servers emit `banId`; older strict readers reject it. Roll servers back before reverting the bot.
- Schema validation remains authoritative for upper bounds, formats and the report-specific omission of player IPs. Generated models enforce the established supported subset.

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
