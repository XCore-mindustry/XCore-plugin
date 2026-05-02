# Moderation Messages

This is the first planned migration family.

Expected initial contracts:
- moderation.ban.created
- moderation.mute.created
- moderation.vote-kick.created
- moderation.kick-banned.command
- moderation.pardon.command
- moderation.audit.appended

## Field Policy
- Payload fields use camelCase.
- Target player identity belongs under `target` using the appropriate shared target/player ref.
- Acting moderator or initiator identity belongs under `actor` using `ActorRefV1`.
- Vote-kick participant entries use player-oriented names: `playerName`, `playerPid`, and optional `discordId`.
- Canonical moderation enum-like fields such as `entryType` should remain closed sets in schema and generated outputs.
- Legacy aliases such as `starter*`, `adminName`, `admin_name`, `pid`, and similar migration-era fields are consumer concerns, not canonical protocol fields.
