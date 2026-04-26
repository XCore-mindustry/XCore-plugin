# Maps Current-to-V1 Mapping

## Scope
This note maps the current `XCore-plugin` and `XCore-discord-bot` maps transport payloads to canonical `xcore-protocol` maps v1 contracts.

The protocol repository remains canonical-only. Legacy aliases and temporary adapter behavior belong in consumer repositories.

## Current Sources
- `TransportEvents.MapsListRequest`
- `TransportEvents.MapsListResponse`
- `TransportEvents.MapEntry`
- `TransportEvents.MapRemoveRequest`
- `TransportEvents.MapRemoveResponse`
- `TransportEvents.LoadMapsV2`
- `RedisBus.rpc_maps_list`
- `RedisBus.rpc_remove_map`
- `RedisBus.publish_maps_load`

## Canonical Routes
| Current route type | Current type | Canonical message | Stream | TTL |
| --- | --- | --- | --- | --- |
| RPC request | `maps.list` | `maps.list.request` | `xcore:rpc:req:{server}` | `10000` |
| RPC response | `maps.list` response payload | `maps.list.response` | `xcore:rpc:resp:{requester}` | request-scoped |
| RPC request | `maps.remove` | `maps.remove.request` | `xcore:rpc:req:{server}` | `10000` |
| RPC response | `maps.remove` response payload | `maps.remove.response` | `xcore:rpc:resp:{requester}` | request-scoped |
| Command | `maps.load` | `maps.load.command` | `xcore:cmd:maps-load:{server}` | `300000` |

## Payload Mapping

### `maps.list.request`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `server` | `server` | Required target server name. |
| absent | `messageType` | Canonical value: `maps.list.request`. |
| absent | `messageVersion` | Canonical value: `1`. |

### `maps.list.response`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| response context / request server | `server` | Required in canonical response. |
| `maps[]` | `maps[]` | Array of canonical `MapEntryV1`. |
| `maps[].name` | `maps[].name` | Required. |
| `maps[].fileName` | `maps[].fileName` | Required camelCase basename. |
| `maps[].author` | `maps[].author` | Required; use explicit fallback such as `Unknown` before publishing if needed. |
| `maps[].width` | `maps[].width` | Optional integer. |
| `maps[].height` | `maps[].height` | Optional integer. |
| `maps[].fileSizeBytes` | `maps[].fileSizeBytes` | Optional integer. |
| `maps[].like` | `maps[].like` | Optional integer. |
| `maps[].dislike` | `maps[].dislike` | Optional integer. |
| `maps[].reputation` | `maps[].reputation` | Optional integer. |
| `maps[].popularity` | `maps[].popularity` | Optional number. |
| `maps[].interest` | `maps[].interest` | Optional number. |
| `maps[].gameMode` | `maps[].gameMode` | Optional string. |

### `maps.remove.request`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `server` | `server` | Required target server name. |
| `fileName` | `fileName` | Required `.msav` basename. |
| `file_name` | none | Legacy Discord-side alias; do not publish as canonical. |

### `maps.remove.response`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| response context / request server | `server` | Required in canonical response. |
| `result` | `result` | Human-readable server result. |

### `maps.load.command`
| Current field | Canonical field | Notes |
| --- | --- | --- |
| `server` | `server` | Required target server name. |
| `urls[]` | `files[]` | Rename to describe the file sources rather than only their URL. |
| `urls[].url` | `files[].url` | Required download URI. |
| `urls[].filename` | `files[].filename` | Required `.msav` basename. |

## Open Consumer Migration Notes
- `XCore-discord-bot` currently normalizes both `fileName` and `file_name` in map entries and publishes both keys for remove requests. Canonical producers should emit only `fileName`.
- `XCore-plugin` currently responds with map list payloads that do not include `messageType`, `messageVersion`, or response `server`; adapters should add these during migration.
- `LoadMapsV2.urls` should map to canonical `files` before entering the protocol surface.
