# Discord Messages

Discord linking and admin-access related protocol contracts belong here.

## Canonical V1 Messages
- `discord.link-code-created` — link-code creation event.
- `discord.link.confirm.command` — confirm a Discord link for one server context.
- `discord.unlink.command` — unlink a Discord identity from a player.
- `discord.link.status-changed` — broadcast that a link became active/inactive.
- `discord.admin-access.changed.command` — change Discord-driven admin access state.

## Field Policy
- Payload fields use camelCase.
- Player identity belongs under `player` using `PlayerRefV1`.
- Discord identity belongs under `discord` using `DiscordIdentityRefV1`.
- Human/system initiators belong under `actor` using `ActorRefV1`, not ad hoc requester strings.
- Secondary origin/cause actors belong under `source` using `ActorRefV1` when the command distinguishes who acted from what authority triggered it.
- Canonical status/action fields should use explicit enums when the allowed values are closed.
- Legacy flat fields such as `requestedBy` and `adminSource` are consumer migration concerns, not canonical protocol fields.
