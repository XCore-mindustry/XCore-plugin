# Shared Types

Reusable subtypes such as actor refs, player refs, server refs, expiration info, and map refs belong here.

## Field Policy
- Shared subtype field names use canonical camelCase.
- Reused player-shaped fragments should prefer `playerUuid`, `playerPid`, and `playerName` for clarity.
- Player PIDs (`playerPid`, `fromPid`, `toPid`) are signed integers with no lower bound: technical admins assign zero or negative PIDs to special players (for example event participants). Use an absent optional field, never a negative value, to mean "no PID".
- Reused actor-shaped fragments should prefer `actorName`, `actorDiscordId`, and `actorType`.
- Map file metadata should use `fileName` consistently for `.msav` basenames.
- Do not preserve family-specific legacy aliases in shared canonical subtypes.
