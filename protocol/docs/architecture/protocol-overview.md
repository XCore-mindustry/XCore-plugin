# Protocol Overview

## Purpose
Define the official communication model used between XCore components.

## Principles
- protocol-first
- language-neutral
- strict canonical contracts
- compatibility through explicit adapters
- stable route metadata
- generated consumption surfaces from canonical definitions

## Message Families
- moderation
- discord integration
- maps RPC
- chat and heartbeat
- telemetry snapshots
- rating seasons (season events, season administration, prize administration and account-merge RPC)
- security (permission changes, staff role sync and staff password reset RPC)
- cases (durable reports, notice delivery wake-ups and live evidence RPC)

## Protocol 0.16.0

`discord.admin-access.changed.command` and `player.password-reset.command` are removed with their
routes, generated models and fixtures. They served the flag-based admin model that the game
servers no longer run. Discord roles reach the servers as grants through `security.staff.sync`,
and a password reset travels as `security.staff.reset-password` and is stored with the player's
grants, so every server sees it on its next read.

Upgrade the bot and the game servers to releases that no longer send or read the two commands
before moving them to 0.16.0. Messages still waiting on the old streams expire after their
two-minute TTL.

## Protocol 0.15.0

Cases adds `cases.report.submitted`, `cases.notice.command` and the `cases.evidence.request` / `cases.evidence.response` RPC pair. Maps adds detached inspection through `maps.inspect.request` / `maps.inspect.response`. Shared evidence and inspection types preserve canonical camelCase fields; report reasons and finding codes remain open strings.

Report events use `xcore:evt:cases:report`, a seven-day TTL and replayable delivery. Notice commands use `xcore:cmd:cases-notice:{server}` with a two-minute TTL. Evidence and inspection requests use `xcore:rpc:req:{server}` with 15-second and 60-second TTLs respectively; both reply on `xcore:rpc:resp:{requester}`. Response messages remain nested route metadata, following the existing RPC convention.

`moderation.ban.created` v1 gains optional positive `banId`; existing payloads without it still validate. Deploy bot readers built against 0.15.0 before servers start sending `banId`. Older strict readers reject the field, so roll servers back before rolling the bot back. The Java record constructor gains a final nullable `Integer banId` argument.

Block summary nested objects use `CaseBlockEntryV1` and `CaseBlockAreaV1`, because generators require named object references. `CaseChatLineV1.playerPid` follows the existing signed PID policy. Report references reuse `PlayerRefV1`, and inspection files reuse `MapFileSourceV1`, including its existing optional Discord attribution.

Schema validation enforces upper bounds, date-time/URI formats and report IP omission; generated models retain the existing validation subset. Consumers must validate canonical payloads and omit `ip` from report player references.

## Boundary
Application repositories consume this protocol. They do not independently redefine canonical wire contracts.

## Consumption Model
The authored source of truth is the protocol spec layer:
- schemas
- shared subtypes
- envelopes
- route manifests

Those definitions should feed generated Java/Python DTO and model artifacts. Consumer repositories keep only thin adapters between internal models and generated protocol artifacts.
