# Permission roles

Staff rights come from roles only. `PlayerData.admin` and the native admin list no longer decide
who is staff, and the old `[permissions] mode` key is ignored. A server without a usable
`permissions.toml` does not start.

## 1. Describe the roles

`permissions.toml` lives next to `secrets.toml` and is shared by every server.

```toml
schemaVersion = 1

[roles.moderator]
weight = 10
permissions = ["xcore.moderation.mute", "xcore.moderation.unmute", "xcore.moderation.kick"]

[roles.admin]
weight = 50
parents = ["moderator"]
permissions = ["xcore.*", "-xcore.permissions.manage", "mindustry.admin"]

[discord]
guildId = "123456789012345678"

[[discord.bindings]]
roleId = "111111111111111111"
role = "moderator"

[[discord.bindings]]
roleId = "222222222222222222"
role = "admin"
```

- A rule starting with `-` denies; a denial always wins over an allowance.
- `mindustry.admin` makes the player a Mindustry admin (`Player.admin`). Nothing else does.
- `weight` is the hierarchy: staff cannot act on a player whose weight is equal or higher.
- `perm nodes` lists every node, `perm roles` shows the file as the server read it.

A missing file or a file with an error stops the server at start. `perm reload` on a running
server refuses such a file and keeps the roles in use.

## 2. Move the existing admins

Admins of the old flag-based setup have no rights until they hold a role. Run once, on one
server, from its console:

```
perm migrate            # dry run: prints what would be granted, writes nothing
perm migrate --apply    # grants; safe to repeat
```

Players whose admin came from a Discord role get the role bound to it, with the Discord role as
the source. Native admin list entries are only reported: grant them by hand if they are still
needed.

## 3. How staff work

- staff rights work only after `/login`; the first login sets the password;
- `Player.admin` is computed from the roles, `PlayerData.admin` is no longer written or read
  for rights;
- `[permissions] trust_native_admins = true` lets an entry of the native admin list stand in for
  a login on that server. Leave it `false` unless a server has no Mongo-backed staff yet.

## Console commands

```
perm user <player> info
perm user <player> role add <role> [--server <name>] [--for <period>] --reason "<text>"
perm user <player> role remove <role|grant id> --reason "<text>"
perm user <player> deny <node> [--for <period>] --reason "<text>"
perm user <player> undeny <grant id> --reason "<text>"
perm user <player> reset-password --reason "<text>"
perm check <player> <node> [--server <name>]
perm nodes [filter] | perm roles | perm reload | perm prune
```

Changing commands run from the local console only; the read-only ones and `perm reload` also work
through `gcmd`. Players see their own roles with `/perm me`.

Roles given by Discord are removed in Discord, not with `role remove`.

## When Mongo or Redis is down

A change reaches the other servers by a Redis event and, if the event is lost, by the reread every
60 seconds. If the reread fails, the last known set keeps working for 15 minutes; after that staff
rights are off until a read succeeds. Denials hold the whole time.

A temporary grant ends by the clock: its rights and the admin flag go within a second of the
expiry, whether or not the store answers.

## Password reset

`perm user <player> reset-password` (or the bot) forgets the password and the remembered devices;
the next `/login` sets a new password. The reset is stored with the player's grants, so a
connection that was logged in with the old password is logged out on the server that made the
reset at once and on every other server by the next reread at the latest, even if Redis is down.
