# Chat Messages

Chat, global chat, and heartbeat-related contracts belong here.

## Canonical V1 Messages
- `chat.message` — game-to-cross-service chat event.
- `chat.global` — global chat relay event.
- `chat.discord-ingress.command` — Discord-to-server chat command.
- `chat.private` — cross-server private-message relay event.
- `server.action` — server lifecycle/status broadcast.
- `player.join-leave` — player session visibility event.
- `server.heartbeat` — canonical server status heartbeat event.

## Field Policy
- Payload fields use camelCase.
- Canonical chat payloads use `authorName`, `message`, and `server`.
- Canonical private-message relay payloads use `fromUuid`, `fromPid`, `fromName`, `toUuid`, `toPid`, `message`, and `server`.
- Canonical join/leave uses `joined` instead of current legacy `join` boolean naming.
- Canonical heartbeat uses `serverName`, `discordChannelId`, `players`, `maxPlayers`, `version`, optional `host`, and optional `port`.
- Legacy aliases such as `author_name`, `player_name`, `server_name`, `serverHost`, and `serverPort` are consumer migration concerns, not canonical protocol fields.
