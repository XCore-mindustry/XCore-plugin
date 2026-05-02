# Shared Types

Reusable subtypes such as actor refs, player refs, server refs, expiration info, and map refs belong here.

## Field Policy
- Shared subtype field names use canonical camelCase.
- Reused player-shaped fragments should prefer `playerUuid`, `playerPid`, and `playerName` for clarity.
- Reused actor-shaped fragments should prefer `actorName`, `actorDiscordId`, and `actorType`.
- Map file metadata should use `fileName` consistently for `.msav` basenames.
- Do not preserve family-specific legacy aliases in shared canonical subtypes.
