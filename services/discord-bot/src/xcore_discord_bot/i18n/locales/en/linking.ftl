## Linking a Discord account to a game account: /link, /link-status, /unlink and a code sent
## to the bot in a direct message. Ids start with "link-" or "unlink-".

link-code-required = Link code is required.
link-code-not-found = Link code not found.
link-code-expired = This link code has expired.
link-player-not-found = Player for this code was not found.
link-taken = This Mindustry account is already linked to another Discord account.
link-done = Account linked to `{ $name }` (`pid={ $pid }`). Return in-game in a moment.
link-server-offline = Server `{ $server }` that issued this code is offline. Try again when it is back, or get a new code in-game.
link-rejected = `{ $server }` refused to link `{ $name }` (`pid={ $pid }`): `{ $reason }`.
link-failed = Linking `{ $name }` (`pid={ $pid }`) failed on `{ $server }`: `{ $reason }`. If the code no longer works, get a new one in-game.
link-unknown = `{ $server }` did not confirm the link of `{ $name }` (`pid={ $pid }`), so it is not known whether it happened. Check /link-status before trying again.
link-status-none = No Mindustry accounts are linked to this Discord account.
link-status-title = Linked Mindustry accounts
unlink-not-yours = This player is not linked to your Discord account.
unlink-no-uuid = Cannot unlink player: UUID is missing.
unlink-no-server = No server is online to unlink `{ $name }`. Try again later.
unlink-done = `{ $name }` is unlinked from your Discord account.
unlink-rejected = `{ $server }` refused to unlink `{ $name }`: `{ $reason }`.
unlink-failed = Unlinking `{ $name }` failed on `{ $server }`: `{ $reason }`.
unlink-unknown = `{ $server }` did not confirm the unlink of `{ $name }`, so it is not known whether it happened. Check /link-status before trying again.
