# Chat Messages

Chat, global chat, and heartbeat-related contracts belong here.

## Canonical V1 Messages
- `chat.message` — game-to-cross-service chat event.
- `chat.global` — global chat relay event.
- `chat.discord-ingress.command` — Discord-to-server chat command.
- `chat.private` — cross-server private-message relay event.
- `server.action` — server lifecycle/status broadcast.
- `player.join-leave` — player session visibility event.
- `player.custom-nickname.changed.command` — cross-server player custom nickname sync command.
- `player.active-badge.changed.command` — cross-server player active badge sync command.
- `player.badge-inventory.changed.command` — cross-server player badge inventory sync command.
- `player.badge-symbol-color-mode.changed.command` — cross-server player badge symbol color mode sync command.
- `player.password-reset.command` — cross-server player password reset sync command.
- `server.heartbeat` — canonical server status heartbeat event.

## Field Policy
- Payload fields use camelCase.
- Canonical chat payloads use `authorName`, `message`, and `server`.
- Canonical private-message relay payloads use `fromUuid`, `fromPid`, `fromName`, `toUuid`, `toPid`, `message`, and `server`.
- Canonical join/leave uses `joined` instead of current legacy `join` boolean naming.
- Canonical player-session sync commands use `playerUuid`, explicit `server`, and camelCase value fields.
- Canonical badge inventory sync commands use `activeBadge` and `unlockedBadges` as the canonical badge-state fields.
- Canonical heartbeat uses `serverName`, `discordChannelId`, `players`, `maxPlayers`, `version`, optional `host`, and optional `port`.
- Legacy aliases such as `author_name`, `player_name`, `uuid`, `server_name`, `serverHost`, and `serverPort` are consumer migration concerns, not canonical protocol fields.
