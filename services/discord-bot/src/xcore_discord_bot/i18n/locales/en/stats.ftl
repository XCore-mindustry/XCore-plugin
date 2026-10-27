## /stats: a player's profile. Ids start with "stats-". Counts that can grow large are written
## with NUMBER(..., useGrouping: 1), so they get the digit grouping of the language.

stats-no-linked-account = You have no linked game account. Get a code in the game and use `/link`, or pass a player ID.
stats-user-not-linked = That user has no linked game account.
stats-player-not-found = Player not found. Enter a player ID or @username, or type a nickname and select a player from the suggestions.
stats-lookup-by-user-admin-only = Only admins can look a player up by their Discord account. Pass a player ID instead.

# Who the player is

stats-unknown = Unknown
stats-unavailable = Unavailable right now
stats-no-id = no ID
stats-online = 🟢 **Online**
stats-online-on = 🟢 **Online** on `{ $server }`
stats-joined = joined { $since }
stats-offline = ⚫ Offline
stats-offline-updated = ⚫ Offline · profile updated { $updated }
stats-discord-not-linked = Not linked
stats-discord-linked = linked { $since }
stats-badge-shown = **{ $badge }** (shown)
stats-also-linked = Also linked: { $accounts }

# Field names

stats-playtime = ⏱️ Playtime
stats-first-seen = 📅 First seen
stats-season-ratings = 🏆 Season ratings
stats-games = 🎮 Games
stats-building = 🧱 Building
stats-hexed-rank = ⬡ Hexed rank
stats-badges = 🎖️ Badges
stats-staff-notes = 🔒 Staff notes

# Games

stats-games-none = No recorded games yet
stats-games-total = `{ NUMBER($games, useGrouping: 1) }` played · `{ NUMBER($wins, useGrouping: 1) }` won · `{ $rate }%` win rate
stats-games-pvp = ⚔️ **PvP** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } game
       *[other] { NUMBER($games, useGrouping: 1) } games
    } · { $wins ->
        [one] { NUMBER($wins, useGrouping: 1) } win
       *[other] { NUMBER($wins, useGrouping: 1) } wins
    } ({ $rate }%)
stats-games-survival = 🛡️ **Survival** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } game
       *[other] { NUMBER($games, useGrouping: 1) } games
    } · best wave { NUMBER($best, useGrouping: 1) } · average { NUMBER($average, useGrouping: 1) }
stats-games-hexed = ⬡ **Hexed** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } game
       *[other] { NUMBER($games, useGrouping: 1) } games
    } · { $wins ->
        [one] { NUMBER($wins, useGrouping: 1) } win
       *[other] { NUMBER($wins, useGrouping: 1) } wins
    }
stats-games-hexed-best = best `#{ $place }` · top 3 ×{ NUMBER($top3, useGrouping: 1) }
stats-building-blocks = Blocks: `{ NUMBER($built, useGrouping: 1) }` built · `{ NUMBER($deconstructed, useGrouping: 1) }` taken apart · `{ NUMBER($destroyed, useGrouping: 1) }` lost
stats-building-units = Units: `{ NUMBER($produced, useGrouping: 1) }` produced · `{ NUMBER($destroyed, useGrouping: 1) }` lost

# The rank of the old Hexed mode

stats-hexed-rank-newbie = Newbie
stats-hexed-rank-regular = Regular
stats-hexed-rank-advanced = Advanced
stats-hexed-rank-veteran = Veteran
stats-hexed-rank-devastator = Devastator
stats-hexed-rank-legend = The Legend
stats-hexed-progress = { $points }/{ $required } wins to { $next }
stats-hexed-max = { $points } wins (max rank)

# Staff notes, which only admins see

stats-ban = 🔨 Ban: { $state }
stats-mute = 🔇 Mute: { $state }
stats-punishments-unavailable = 🔨 Ban and mute: { stats-unavailable }
stats-punishment-none = none
stats-punishment =
    until { $until }
    └ `{ $reason }` by { $admin }
stats-language = 🌐 Language: `{ $language }` · translator: `{ $translator }`
stats-hidden = 🙈 Hidden from the leaderboards

# The admins' buttons under the profile

stats-button-ban = Ban
stats-button-mute = Mute
stats-button-history = History
stats-button-actions = Actions
