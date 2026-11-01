# Discord Messages

Discord linking protocol contracts belong here. Discord roles reach the game servers as grants
through `security.staff.sync` (see `../security/README.md`).

## Canonical V1 Messages
- `discord.link-code-created` — link-code creation event.
- `discord.link.confirm.command` — confirm a Discord link for one server context.
- `discord.unlink.command` — unlink a Discord identity from a player.
- `discord.link.status-changed` — broadcast that a link became active/inactive.
- `discord.account.operation.request` / `discord.account.operation.response` — targeted RPC that
  links or unlinks on one server and reports the outcome.

### discord.account.operation
- Link goes to the server that issued the code; unlink to the server the player is on, or one
  writable server when they are offline. A request is never fanned out to every server.
- `operationId` is new for each confirmed intent and the same when that intent is repeated. It is
  not the transport `correlation_id`.
- A server does not start a request after `expiresAt`. Unlink only clears the link when the stored
  `discord_linked_at` still equals `expectedLinkedAt`, so an old unlink cannot remove a newer link.
- `success` means the Mongo write happened; `rejected` and `failed` are confirmed refusals or
  failures. `unknown` means the outcome is not proven. A requester's timeout is also unknown and is
  not retried on its own.

## Field Policy
- Payload fields use camelCase.
- Player identity belongs under `player` using `PlayerRefV1`.
- Discord identity belongs under `discord` using `DiscordIdentityRefV1`.
- Human/system initiators belong under `actor` using `ActorRefV1`, not ad hoc requester strings.
- Secondary origin/cause actors belong under `source` using `ActorRefV1` when the command distinguishes who acted from what authority triggered it.
- Canonical status/action fields should use explicit enums when the allowed values are closed.
- Legacy flat fields such as `requestedBy` and `adminSource` are consumer migration concerns, not canonical protocol fields.
