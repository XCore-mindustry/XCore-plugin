# Chat And Heartbeat Current-to-V1 Mapping

## Scope
This note maps the current `XCore-plugin` and `XCore-discord-bot` chat and heartbeat transport payloads to canonical `xcore-protocol` v1 contracts.

The protocol repository remains canonical-only. Legacy aliases and temporary adapter behavior belong in consumer repositories.

## Current Sources
- `TransportEvents.MessageEvent`
- `TransportEvents.GlobalChatEvent`
- `TransportEvents.DiscordMessageEvent`
- `TransportEvents.PrivateMessageEvent`
- `TransportEvents.ServerActionEvent`
- `TransportEvents.PlayerJoinLeaveEvent`
- `TransportEvents.ServerHeartbeatEvent`
- `RedisTransportTopology`
- Discord bot `contracts.py`
- Discord bot `redis_bus.py`

## Canonical Routes
| Current route type | Current type | Canonical message | Stream | TTL |
| --- | --- | --- | --- | --- |
| Event | `chat.message` | `chat.message` | `xcore:evt:chat:message` | `60000` |
| Event | `chat.global` | `chat.global` | `xcore:evt:chat:global` | `60000` |
| Command | `chat.discord_ingress` | `chat.discord-ingress.command` | `xcore:cmd:discord-message:{server}` | `60000` |
| Event | current private-message relay | `chat.private` | `xcore:evt:chat:private` | `60000` |
| Event | `server.action` | `server.action` | `xcore:evt:server:action` | `60000` |
| Event | `player.join_leave` | `player.join-leave` | `xcore:evt:player:joinleave` | `60000` |
| Event | current server heartbeat | `server.heartbeat` | `xcore:evt:server:heartbeat` | `60000` |

## Payload Mapping

### `chat.message`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `authorName` | `authorName` | Required. |
| `message` | `message` | Required. |
| `server` | `server` | Required. |

### `chat.global`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `authorName` | `authorName` | Required. |
| `message` | `message` | Required. |
| `server` | `server` | Required. |

### `chat.discord-ingress.command`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `authorName` | `authorName` | Required. |
| `message` | `message` | Required. |
| `server` | `server` | Required target server. |
| current type `chat.discord_ingress` | `messageType = chat.discord-ingress.command` | Canonical name moves to hyphenated payload identity while stream stays stable. |

### `chat.private`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `fromUuid` | `fromUuid` | Required sender UUID. |
| `fromPid` | `fromPid` | Required sender PID. |
| `fromName` | `fromName` | Required sender display name. |
| `toUuid` | `toUuid` | Required recipient UUID. |
| `toPid` | `toPid` | Required recipient PID. |
| `message` | `message` | Required private-message body. |
| `server` | `server` | Required source server for cross-server relay filtering. |
| current `TransportEvents.PrivateMessageEvent` | `messageType = chat.private` | Canonical relay event keeps the existing stable stream and payload shape. |

### `server.action`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `message` | `message` | Required. |
| `server` | `server` | Required. |

### `player.join-leave`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `playerName` | `playerName` | Required. |
| `server` | `server` | Required. |
| `join` | `joined` | Canonical renaming for clearer boolean semantics. |

### `server.heartbeat`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `serverName` | `serverName` | Required. |
| `discordChannelId` | `discordChannelId` | Required integer. |
| `players` | `players` | Required integer. |
| `maxPlayers` | `maxPlayers` | Required integer. |
| `version` | `version` | Required string. |
| `host` | `host` | Optional. |
| `port` | `port` | Optional. |
| `serverHost` | none | Legacy consumer alias; do not publish canonically. |
| `serverPort` | none | Legacy consumer alias; do not publish canonically. |

## Open Consumer Migration Notes
- Discord bot consumer models currently tolerate snake_case and legacy alias variants such as `author_name`, `player_name`, `server_name`, `serverHost`, and `serverPort`. Canonical producers should emit only the canonical camelCase fields.
- `PlayerJoinLeaveEvent` currently uses `join`; adapters should translate it to canonical `joined` before crossing into the protocol surface.
- Heartbeat currently appears in consumer behavior and producer code but should now be treated as an explicit canonical event family with stable route metadata.
- Raw heartbeat fallback handling should remain a consumer transport concern unless a future protocol ADR explicitly promotes a canonical raw event contract.
