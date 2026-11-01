# Actor Semantics Policy

## Purpose
This document defines the canonical semantic meaning of `actor` and `source` in protocol v1 payloads.

It is guidance for producers, adapters, and reviewers. It does not change any v1 schema and must not be interpreted as introducing new required fields or new enum values beyond the existing canonical contract.

## Core Distinction

### `actor`
`actor` identifies the concrete initiator of the action.

Use `actor` for the actual person or system instance that performed the action being described by the payload.

Examples:
- the Discord user who requested an unlink
- the moderator who issued a ban
- the game server instance that emitted a server-originated command
- a scheduled system task that applied an automated change

### `source`
`source` identifies the provenance or triggering authority behind the action.

Use `source` when the payload needs to preserve what mechanism, rule, or upstream authority caused the action, even when that source is not the same thing as the direct initiator.

Typical provenance tokens are values such as:
- `DISCORD_ROLE`
- `NONE`
- `COMMAND`

In other words:
- `actor` answers **who actually did it**
- `source` answers **what authority, mechanism, or origin explains why it happened**

## Usage Rule
- Always use `actor` for the concrete initiator.
- Use `source` only when the message needs to distinguish provenance from the direct initiator.
- If there is no meaningful second provenance concept, do not invent one just to populate `source`.

This matches the existing v1 family guidance where `actor` holds the initiator and `source` is reserved for secondary origin or cause information.

## `actorType` Semantics
`actorType` classifies the kind of initiator represented by an `ActorRefV1`.

Canonical v1 uses the existing enum values:
- `system`
- `discord`
- `server`
- `player`
- `unknown`

Producers and adapters should apply them as follows.

### `system`
Use `actorType = system` when the action originates from server-side automation or non-user-owned system behavior.

Examples:
- Discord role synchronization applying admin access automatically
- scheduled maintenance or cleanup tasks
- an internally triggered command with no named human user
- policy enforcement executed by background automation

This value is appropriate when the action is real and intentional but is not attributable to a specific human or server-player identity.

### `discord`
Use `actorType = discord` when the concrete actor is a verified Discord user.

Examples:
- a Discord user confirms a link
- a Discord moderator triggers an unlink or admin-access change

When known, `actorDiscordId` should carry the stable Discord identity for that actor.

### `server`
Use `actorType = server` when the concrete actor is a game server instance.

Examples:
- one server issues a command to another server
- a server-originated integration publishes an action on behalf of that server runtime

This is for a server instance acting as the initiator, not for anonymous automation. If the action is better described as platform automation rather than a named server instance, prefer `system`.

### `player`
Use `actorType = player` when the concrete actor is a Mindustry player.

Examples:
- an in-game moderator issues a moderation command
- a player triggers an unlink or other player-originated command from the game side

### `unknown`
Use `actorType = unknown` when actor information is absent, incomplete, or cannot be verified reliably.

Examples:
- migrated legacy payloads that only preserve a loose string
- events where the initiator was not captured
- compatibility paths where the actor kind cannot be inferred safely

Prefer a specific type whenever the producer truly knows it. Use `unknown` to preserve correctness, not as a shortcut.

## Applying `actor` And `source` Together
When both fields appear, they should describe different semantic roles.

Example:
- `actor` = the Discord moderator who requested the change
- `source` = `DISCORD_ROLE` because the effective authority came from role synchronization or Discord-side role state

Another example:
- `actor` = `system`
- `source` = `COMMAND`

This means the command was applied by automation and the provenance of that automation was a command-driven workflow rather than a named user.

## Compatibility And Versioning Rule
This policy is semantic guidance only.

- It does not add fields.
- It does not rename fields.
- It does not expand or narrow the existing v1 schema surface.
- It does not override the canonical rule that compatibility adapters belong in consumer repositories.

If a future change would alter payload meaning in a way that existing v1 consumers could not safely interpret, that change requires normal versioned protocol evolution rather than an in-place reinterpretation.
