# Security Messages

Contracts:
- player.password-reset.command
- security.permissions.changed
- security.staff.sync.request / security.staff.sync.response
- security.staff.reset-password.request / security.staff.reset-password.response

## Permissions

The game servers own permission grants. Other services never write them; they ask a server to
do it through the RPCs below and learn of changes from the event.

### security.permissions.changed
Published after a player's grants were written. `revision` is the revision of the player's grant
document after the write and only grows. A consumer that already holds an equal or newer revision
ignores the event, so duplicates and out-of-order deliveries are harmless. The event carries no
grants: a consumer that needs them reads the store.

### security.staff.sync
Tells a server which bound Discord roles a linked player holds, so it can replace the grants that
come from Discord. Grants from any other source are not touched.

- `roleIds` holds only the roles that are bound to a game role; other Discord roles are left out.
- `complete = true` says `roleIds` is the full set. An empty list is then a revocation: the member
  left the guild or lost every bound role.
- `complete = false` says the sender could not load the member fully. The server must not revoke
  anything on such a request.
- `operationId` is new for every sync attempt and the same on a retry of that attempt. Repeating a
  request with the same `operationId` is safe.
- The response reports the revision after the sync and whether anything changed.

### security.staff.reset-password
Clears the staff password of a player; the next login sets a new one. `changed` is false when
there was no password to clear.

### Errors
Failures use the RPC response envelope with `status = "error"`:
- `NOT_FOUND` — the player is unknown, or is not linked to the Discord account in the request.
- `UNAVAILABLE` — the server cannot reach its store, or does not run with roles enabled.

## Field Policy
- Payload fields use camelCase.
- The player is always `playerUuid`.
