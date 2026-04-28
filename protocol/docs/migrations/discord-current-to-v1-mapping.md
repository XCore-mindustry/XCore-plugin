# Discord Current-to-V1 Mapping

## Goal
Map the current Discord linking/admin payloads used by `XCore-plugin` and `XCore-discord-bot` into canonical v1 protocol contracts and their generated canonical Java/Python model surface.

## Scope
- `discord.link-code-created`
- `discord.link.confirm.command`
- `discord.unlink.command`
- `discord.link.status-changed`
- `discord.admin-access.changed.command`

## Core Rules
- Canonical outbound payloads use camelCase only.
- Command and event semantics stay separate.
- Current epoch-millis business timestamps are converted to ISO-8601 UTC in canonical payloads. This rule applies to payload/business timestamps, not envelope timestamp fields.
- Legacy names such as `discord.link_confirm` are migration concerns outside the canonical protocol surface.

## Discord Link Confirm

### Current payload
Current plugin/bot fields:
- `code`
- `playerUuid`
- `playerPid`
- `discordId`
- `discordUsername`
- `server`
- `confirmedAt` (epoch millis)

### Canonical v1 mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `code` | `code` | unchanged semantic |
| `playerUuid` | `player.playerUuid` | grouped under player |
| `playerPid` | `player.playerPid` | grouped under player |
| player nickname from lookup / code store | `player.playerName` | canonical player ref requires name |
| `discordId` | `discord.discordId` | grouped under discord identity |
| `discordUsername` | `discord.discordUsername` | grouped under discord identity |
| `server` | `server` | unchanged semantic |
| `confirmedAt` epoch millis | `confirmedAt` ISO-8601 UTC | mapper converts |

## Discord Link Code Created

### Current payload
Current plugin fields:
- `code`
- `playerUuid`
- `playerPid`
- `playerNickname`
- `server`
- `createdAt` (epoch millis)
- `expiresAt` (epoch millis)

### Canonical v1 mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `code` | `code` | unchanged semantic |
| `playerUuid` | `player.playerUuid` | grouped under player |
| `playerPid` | `player.playerPid` | grouped under player |
| `playerNickname` | `player.playerName` | normalized naming |
| `server` | `server` | unchanged semantic |
| `createdAt` epoch millis | `createdAt` ISO-8601 UTC | mapper converts |
| `expiresAt` epoch millis | `expiresAt` ISO-8601 UTC | mapper converts |

## Discord Unlink

### Current payload
- `playerUuid`
- `playerPid`
- `discordId`
- `requestedBy`
- `server`
- `requestedAt` (epoch millis)

### Canonical v1 mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `playerUuid` | `player.playerUuid` | grouped under player |
| `playerPid` | `player.playerPid` | optional |
| player nickname from lookup | `player.playerName` | canonical player ref currently requires name |
| `discordId` | `discord.discordId` | grouped under discord identity |
| `requestedBy` | `requestedBy` | unchanged semantic |
| `server` | `server` | unchanged semantic |
| `requestedAt` epoch millis | `requestedAt` ISO-8601 UTC | mapper converts |

## Discord Link Status Changed

### Current payload
- `playerUuid`
- `playerPid`
- `playerNickname`
- `discordId`
- `discordUsername`
- `action`
- `server`
- `occurredAt` (epoch millis)

### Canonical v1 mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `playerUuid` | `player.playerUuid` | grouped under player |
| `playerPid` | `player.playerPid` | grouped under player |
| `playerNickname` | `player.playerName` | normalized naming |
| `discordId` | `discord.discordId` | grouped under discord identity |
| `discordUsername` | `discord.discordUsername` | grouped under discord identity |
| `action` | `action` | canonical enum narrowed to `linked` / `unlinked` |
| `server` | `server` | unchanged semantic |
| `occurredAt` epoch millis | `occurredAt` ISO-8601 UTC | mapper converts |

## Discord Admin Access Changed

### Current payload
- `playerUuid`
- `playerPid`
- `discordId`
- `discordUsername`
- `admin`
- `adminSource`
- `requestedBy`
- `reason`
- `server`
- `occurredAt` (epoch millis)

### Canonical v1 mapping

| Current field | Canonical field | Notes |
|---|---|---|
| `playerUuid` | `player.playerUuid` | grouped under player |
| `playerPid` | `player.playerPid` | grouped under player |
| player nickname from lookup/session | `player.playerName` | canonical player ref requires name |
| `discordId` | `discord.discordId` | grouped under discord identity |
| `discordUsername` | `discord.discordUsername` | grouped under discord identity |
| `admin` | `admin` | unchanged semantic |
| `adminSource` | `adminSource` | unchanged semantic |
| `requestedBy` | `requestedBy` | unchanged semantic |
| `reason` | `reason` | unchanged semantic |
| `server` | `server` | unchanged semantic |
| `occurredAt` epoch millis | `occurredAt` ISO-8601 UTC | mapper converts |

## Route And Type Mapping

| Current eventType | Canonical messageType |
|---|---|
| `discord.link_code_created` | `discord.link-code-created` |
| `discord.link_confirm` | `discord.link.confirm.command` |
| `discord.unlink` | `discord.unlink.command` |
| `discord.link_status_changed` | `discord.link.status-changed` |
| `discord.admin_access_changed` | `discord.admin-access.changed.command` |

## Main Migration Notes
- Current plugin route semantics treat link-code-created and link-status-changed as broadcast events; canonical naming keeps those messages event-shaped without a `.command` suffix.
- Current plugin route semantics treat confirm, unlink, and admin-access as commands; canonical naming keeps that distinction explicit with `.command`.
- Current command payloads may not always naturally carry `playerName`; consumer-side migration code may need lookup or session context to populate the canonical `player.playerName` requirement before constructing canonical payloads.

## Recommended Next Step
Keep consumer/plugin mappers aligned with the canonical generated Discord payloads and preserve the existing validation coverage as those mappers are introduced.
