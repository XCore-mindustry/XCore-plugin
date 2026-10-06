# Switching a server to permission roles

Roles are off until `[permissions] mode = "roles"` is set. With `mode = "legacy"` (the default)
nothing changes: `PlayerData.admin` and the native admin list decide who is staff.

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

A file with an error stops a server that starts in roles mode. `perm reload` on a running server
refuses such a file and keeps the roles in use.

## 2. Move the existing admins

Run on one server, from its console:

```
perm migrate            # dry run: prints what would be granted, writes nothing
perm migrate --apply    # grants; safe to repeat
```

Players whose admin came from a Discord role get the role bound to it, with the Discord role as
the source. Native admin list entries are only reported: grant them by hand if they are still
needed.

## 3. Switch

Set `mode = "roles"` on every server and restart them. From then on:

- staff rights work only after `/login`; the first login sets the password;
- `Player.admin` is computed from the roles, `PlayerData.admin` is no longer written;
- `trust_native_admins = true` lets an entry of the native admin list stand in for a login on
  that server. Leave it `false` unless a server has no Mongo-backed staff yet.

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

## Rolling back

Set `mode = "legacy"` and restart. The grants stay in `permission_grants` and are ignored.
