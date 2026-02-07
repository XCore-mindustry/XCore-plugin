# ==============================================================================
# Terms
# ==============================================================================
-xcore = XCore server
# ==============================================================================
# General & Help
# ==============================================================================
commands-help-params = { "[" }page]
commands-help-description = Lists all commands.
commands-help-start-content = { "[" }orange]-- Commands Page[lightgray] { $page }[gray]/[lightgray]{ $totalPages }[orange] --
commands-help-content = { "[" }orange] /{ $commandName }[white] { $commandParams }[lightgray] - { $commandDescription }
commands-information-params = { "" }
commands-information-description = Show information about the server
commands-info-title = { "[" }orange]{ -xcore } — { $xcorServerName }
commands-info-text =
    { "[" }accent]XCore[white] is a [cyan]free[white] server for playing [accent]Mindustry[white].
    { "" }
    { "" }XCore Version — [accent]{ $xcoreVersion }[white]
commands-sync-params = { "" }
commands-sync-description = Sync your game with the server. Run this to fix errors like ghost units.
commands-discord-params = { "" }
commands-discord-description = Redirects you to discord server
welcome =
    { "[" }accent]Welcome to { $serverName }!
    { "" }[lightgray]Type [accent]/help[lightgray] to see a list of commands
    { "" }[lightgray]Type [accent]/vote [gray]<y/n>[lightgray] to vote for kicking a player
    { "" }[lightgray]Type [accent]/votekick [gray]<ID/name> <reason…>[lightgray] to start a vote-kick
    { "" }[lightgray]Type [accent]/t [gray]<message…>[lightgray] to send a message to your teammates
    { "" }[lightgray]Type [accent]/g [gray]<message…>[lightgray] to send a message to all servers
    { "" }[lightgray]Type [accent]/tr [gray]<language/auto>[lightgray] to enable the translator
    { "" }[lightgray]Type [accent]/discord[lightgray] to redirect you to our discord server
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-params = <message…>
commands-t-description = Send a message only to your teammates.
commands-t-chat = { "[" }{ "#" }{ $color }][Team] [coral]>[accent] { $name }[coral]:[white] { $message }
commands-g-params = <message…>
commands-g-description = Send a message across all servers
commands-a-params = <message…>
commands-a-description = Send a message only to admins.
commands-tr-params = <language>
commands-tr-description = Set the translator language.
commands-tr-success = { "[" }accent]The translator language has been successfully changed to [grey]{ $translatorLanguage }[]!
commands-tr-off = { "[" }accent]Translator is [scarlet]off[]!
commands-tr-not-found = { "[" }scarlet]⚠ There is no such language.
chat-discord-format = { "[" }blue][Discord][] { $author }: { $message }
chat-global-format = { "[" }royal][[[orange]GLOBAL [lightgray](from [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-params = <password>
commands-login-description = Admin request. Don't use if you don't know what you're doing.
commands-login-incorrect-password = { "[" }scarlet]⚠ Incorrect password!
commands-login-success = { "[" }green]Admin rights granted.
commands-login-confirmed = { "[" }green]Your admin request confirmed.
commands-login-admin-password-created =
    { "[" }green]Admin password created.
    { "" }[red]Don't forget your password! If you forget it, you will need to ask a general administrator to reset it.
commands-login-request-approval-discord = { "[" }accent]You need to approve your admin request on discord [gray]#admin-bots[]
commands-logout-params = { "" }
commands-logout-description = Log out. This will [scarlet]revoke your admin rights.
commands-logout-successful = { "[" }green]Admin rights revoked.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-params = <player-id> <period> [reason…]
commands-ban-description = Ban a player. [scarlet]Admin only
commands-ban-success = { $nickname } [scarlet]banned
commands-unban-params = <player-id>
commands-unban-description = Unban a player. [scarlet]Admin only.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]successfully unbanned.
commands-mute-params = <player-id> <period> [reason…]
commands-mute-description = Mute a player. [scarlet]Admin only.
commands-mute-success = { "[" }accent]Successfully muted { $nickname }
commands-unmute-params = <player-id>
commands-unmute-description = Unmute player. [scarlet]Admin only.
commands-unmute-success = { "[" }green]Successfully unmuted []{ $nickname }
ban-content =
    { $nickname } [accent]have been [scarlet]banned[].
    To appeal the ban, visit discord(channel [gray]{ support-channel }[])
    { "" }[cyan]{ $discordUrl }
ban-cancelled = { "[" }accent]Player [scarlet]{ $nickname }[accent] ban has been cancelled
tempban-content =
    { $nickname }[accent] have been banned.
    Admin: { $adminName }[accent]
    Reason: "[gold]{ $reason }[]"
    You will be unbanned in: { $days } days, { $hours } hours and { $minutes } minutes
    To appeal your ban, visit discord(channel [gray]{ support-channel }[]):
    { "" }[cyan]{ $discordUrl }
tempban-player-banned = { "[" }scarlet] Admin { $adminName }[scarlet] banned player [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    { "[" }scarlet]You were muted by administrator [accent]{ $adminName }[blue] for { $remainMinutes }:{ $remainSeconds } minutes,
    reason: { $reason }
you-are-muted =
    { "[" }scarlet]You can't write in the chat. [accent]You have been muted by an admin { $adminName }[blue] for { $remainMinutes }:{ $remainSeconds } minutes,
    reason: { $reason }
kick-pirated-game = { "[" }accent]Unauthorized client detected. [scarlet]Access denied[]. Please play using the [lime]official[] version from [blue]Steam[], [blue]Google Play[], or [blue]itch.io[].
kick-recently-kicked =
    { "[" }accent]You were recently kicked from this server.
    Wait [cyan]{ $remainMinutes }:{ $remainSeconds }[accent] before joining again.
kick-bot-protection = Maybe you are a bot. If not, try to reconnect.
kick-admintools-outdated =
    { "[" }green]The required AdminTools version: [grey]{ $requiredVersion }[]
    { "" }[scarlet]Your AdminTools version: [grey]{ $version }[]
    { "" }
    { "" }[cyan]Please update your AdminTools to join this server.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-params = <ID/name> <reason…>
commands-votekick-description = Vote to kick a player from the server.
commands-vote-params = <y/n>
commands-vote-description = Vote on the current vote-kick session.
commands-vote-vote-with = { "[" }scarlet]⚠ Vote with [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] voted to kick { $target } [grey]#[white]{ $targetId }[lightgray] for [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    { "" }[lightgray]Type [orange]/vote <y/n>[] to vote.
votekick-left = { $player }[lightgray] left. Their vote was cancelled. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = { "[" }lightgray]Vote failed. Not enough votes to kick { $target }[lightgray].
votekick-cancelled = { "[" }scarlet]Vote to kick { $target }[scarlet] was cancelled by { $admin }.
votekick-success =
    { "[" }orange]Vote passed. { $target }[orange] kicked for [scarlet]{ $minutes }[] { $minutes ->
        [one] minute
       *[other] minutes
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-params = { "[" }map-name]
commands-map-description = Statistics of a specific map
commands-map-title = { "[" }orange]{ -xcore } — Statistics
commands-map-content =
    { "" }[white]Map statistics for [green]{ $name }
    { "" }[white]Author:[green] { $author }[orange] | [white]Size:[green] { $width }x{ $height }[orange]
    { "" }[white]Reputation:[green] { $reputation }[orange] | [white]Popularity:[green] { $popularity }[orange] | [white]Interest:[green] { $interest }[orange]
    { "" }[white]Times played:[green] { $played }[orange] | [white]Played this year:[green] { $playedYear }[orange] | [white]Last played:[green] { $lastPlayed }[orange]
    { "" }[white]Min time:[green] { $min }[orange] | [white]Avg time:[green] { $avg }[orange] | [white]Max time:[green] { $max }[orange]
    { "" }[green]{ $desc }[white]
commands-maps-params = { "[" }page]
commands-maps-description = List of all maps on this server.
commands-maps-title = { "[" }orange]{ -xcore } — Map List
commands-maps-content = { "" }[white]Page [green]{ $page }[] of [green]{ $total }[]
commands-maps-text-params = { "[" }page]
commands-maps-text-description = List of all maps on this server.
commands-maps-text-start-content =
    { "[" }accent]Current map: []{ $name }[white]
    { "" }[orange][gold]Map list [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | By: [sky]{ $author }
commands-artv-params = { "[" }map…]
commands-artv-description = Force change map. [scarlet]Admin only
commands-artv-map-skipped = { $nickname }[accent] skipped map.
commands-rtv-params = { "[" }map…]
commands-rtv-description = Rock the vote to change map
commands-like-params = { "" }
commands-like-description = Vote for the current map (increases reputation)
commands-dislike-params = { "" }
commands-dislike-description = Vote against the current map
map-vote-title = { "[" }orange]{ -xcore } — [scarlet]GAME OVER!
map-vote-content =
    { "" }
    { "" }Next map: [accent]{ $mapName }[] by [accent]{ $author }[white].
    { "" }New game starts in [accent]{ $seconds }[white] seconds.
    { "" }
    { "" }[cyan]Did you like this map?
map-vote-like = { "[" }green]👍 Like
map-vote-dislike = { "[" }red]👎 Dislike
map-vote-like-selected = { "[" }gray]You liked it
map-vote-dislike-selected = { "[" }gray]You disliked it
map-rtv = { "[" }orange]Voting
map-artv = { "[" }red]Instant Change
map-maps = Maps
rtv-vote =
    { $nickname }[lightgray] voted to change the current map to [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Type [orange]y[] or [orange]n[] to vote.
rtv-left = { $nickname }[lightgray] left. Their vote to change the current map was cancelled. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = { "[" }lightgray]Vote failed. Not enough votes to change the current map to [orange]{ $mapName }[].
rtv-success = { "[" }orange]Vote passed. Map [accent]{ $mapName }[] will be loaded in [accent]{ $mapLoadDelay }[] seconds…
rtv-cancelled = { "[" }lightgray]Vote to change the current map to [orange]{ $mapName }[lightgray] was cancelled by { $admin }.
# ==============================================================================
# Statistics & Ranks
# ==============================================================================
commands-stats-params = { "[" }player-id]
commands-stats-description = View a player's Statistics
commands-stats-content =
    { $nickname } [grey]#{ $pid } Statistics
    { "" }[brown]PlayTime: [grey]{ $totalPlayTime }[] minutes
    Hexed Rank: [grey]{ $hexedRankTag } { $hexedRankName }
    MiniPvP rating: { $pvpRating }
commands-lb-params = { "" }
commands-lb-description = Enable/disable leaderboard
commands-lb-success =
    { $leaderboardEnabled ->
        [true] { "[" }accent]Leaderboard [green]enabled
       *[other] { "[" }accent]Leaderboard [scarlet]disabled
    }
leaderboard = { "[" }blue]Leaderboard
commands-rank-params = { "[" }player…]
commands-rank-description = Shows information about this player's rank.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    { "" }[gold]Wins: { $points }/{ $requiredPoints }
commands-ranks-params = { "" }
commands-ranks-description = Shows information about ranks.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    { "" }[gold]Requirements: [grey]{ $requiredPoints } [accent]wins[]
commands-ranks-footer = The amount of wins increases only when defeating a player of your rank or higher.
commands-top-params = { "" }
commands-top-description = Top players
commands-top-hexed-content = { "[" }orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []wins
commands-top-pvp-content = { "[" }orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
# ==============================================================================
# Game Modes (Hexed, PvP, Spectate, AI)
# ==============================================================================
commands-spectate-params = { "" }
commands-spectate-description = Spectate the game. This will clear your unit and change your team so you can view the game easily.
commands-spectate-success = { "[" }green]You are now spectating
commands-ai-params = <idle/i/attack/a>
commands-ai-description = Control AI
commands-ai-usage = { "[" }red]attack(i) []or [accent]idle(i)
hexed-popup = { "[" }blue]{ $minutes }:{ $seconds }[] until the game ends.
hexed-eliminated = { $nickname } [gold]has been [scarlet]eliminated[]!
hexed-leaderboard-content = { "[" }orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]hexes
hexed-ranks-newbie = Newbie
hexed-ranks-regular = Regular
hexed-ranks-advanced = Advanced
hexed-ranks-veteran = Veteran
hexed-ranks-davastator = Devastator
hexed-ranks-the_legend = The Legend
hexed-game-over-header = Game Over. Winners:
hexed-game-over-winner-row =
    { "[" }orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] hex
       *[other] hexes
    }
hexed-game-over-no-winners = Game Over. Unfortunately, I couldn't find the winning players.
hexed-game-over-restart = New game in 10 seconds…
pvp-team-won = Your team has won. Your rating has been increased by { $increased }
pvp-team-lose = Your team has lost. Your rating has been reduced by { $reduced }
pvp-leaderboard-content = { "[" }orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]rating
pvp-you-spectator = { "[" }scarlet]You have been eliminated. Please wait for the next game.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined = { $nickname } [grey]#[white]{ $pid }[grey] [accent]has joined.
player-left = { $nickname } [grey]#[white]{ $pid }[grey] [accent]has left.
notification-votekick-playtime = { "[" }accent]Congratulations! You have played for [lightgray]{ 0 }[] minutes and can now start a vote-kick.
notification-global-chat-playtime =
    { "[" }accent]Congratulations! You have played for [lightgray]{ 0 }[] minutes and can now send messages to global chat.
    { "" }[lightgray]Type [accent]/g [gray]<message…>[lightgray] to send a message.
notification-admin-kick = { $admin }[accent] kicked { $target }[].
notification-admin-wave-skip = { $admin }[accent] has skipped the wave.
notification-server-restart = Restart in { $seconds }
like-map-success = { "[" }green]You liked this map!
like-map-changed = { "[" }green]You changed your mind to a Like!
dislike-map-success = { "[" }orange]You disliked this map.
dislike-map-changed = { "[" }orange]You changed your mind to a Dislike.
like-event-success = { "[" }green]You liked this event!
like-event-changed = { "[" }green]You changed your mind to a Like!
dislike-event-success = { "[" }orange]You disliked this event.
dislike-event-changed = { "[" }orange]You changed your mind to a Dislike.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-params = { "" }
commands-event-description = Event management menu
commands-events-params = { "[" }page]
commands-events-description = List of all events on the servers.
event-events = Events
event-menu-main = Main
event-menu-main-title = { "[" }orange]{ -xcore } — Events
event-menu-main-content = Main events page
event-menu-event = Event
event-menu-event-title = { "[" }orange]{ -xcore } — Event
event-menu-event-content =
    { "" }[white]Event statistics [green]{ $name }
    { "" }[white]Author:[green] { $author }[orange] | [white]Map:[green] { $mapName }[orange]
    { "" }[white]Is Major?:[green] { $isMajor }[orange] | [white]Conducted?:[green] { $isConducted }[orange]
    { "" }[white]Is Active?:[green] { $isActive }[orange] | [white]Is Temporary?:[green] { $isTemporary }[orange]
    { "" }[white]Likes:[green] { $like }[orange] | [white]Dislikes:[green] { $dislike }[orange]
    { "" }[green]{ $description }[white]
event-menu-event-map = View map
event-menu-events = Events list
event-menu-events-title = { "[" }orange]{ -xcore } — Events List
event-menu-events-content = { "" }[white]Page [green]{ $page }[] of [green]{ $total }[]
event-menu-events-empty = No events found
event-menu-events-selected = { "[" }green]●[] { $name }
event-menu-create-start = Create
event-menu-create-start-title = { "[" }orange]{ -xcore } — Event Creation
event-menu-create-start-message = Enter the name of the future event
event-menu-create-start-default = { $playerName }'s Event
event-menu-edit = Edit
event-menu-edit-title = { "[" }orange]{ -xcore } — Edit Event
event-menu-edit-content =
    { "" }[white]Event statistics [green]{ $name }
    { "" }[white]Author:[green] { $author }[orange] | [white]Map:[green] { $mapName }[orange]
    { "" }[white]Is Major?:[green] { $isMajor }[orange] | [white]Is Temporary?:[green] { $isTemporary }[orange]
    { "" }[green]{ $description }[white]
event-menu-edit-name = Name
event-menu-edit-name-title = { "[" }orange]{ -xcore } — Edit Event
event-menu-edit-name-message = Edit name:
event-menu-edit-description = Description
event-menu-edit-description-title = { "[" }orange]{ -xcore } — Edit Event
event-menu-edit-description-message = Edit description:
event-menu-edit-map = Edit map
event-menu-edit-temporary-active = { "[" }green]Temporary
event-menu-edit-temporary-inactive = { "[" }gray]Temporary
event-menu-edit-major-active = { "[" }green]Major
event-menu-edit-major-inactive = { "[" }gray]Major
event-menu-edit-planned-start = Event start
event-menu-edit-planned-start-title = { "[" }orange]{ -xcore } — Edit Event
event-menu-edit-planned-start-message = Enter start time in ms or using m/h/d:
event-menu-edit-planned-end = Event end
event-menu-edit-planned-end-title = { "[" }orange]{ -xcore } — Edit Event
event-menu-edit-planned-end-message = Enter end time in ms or using m/h/d:
event-menu-maps = Maps
event-menu-maps-title = { "[" }orange]{ -xcore } — Select Map
event-menu-maps-content = { "" }[white]Page [green]{ $page }[] of [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] voted to change the current event to [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Type [orange]y[] or [orange]n[] to vote.
vote-event-left = { $nickname }[lightgray] left. Their vote to change the event has been cancelled. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = { "[" }lightgray]Vote failed. Not enough votes to change the event to [orange]{ $name }[].
vote-event-success = { "[" }orange]Vote passed. The event [accent]{ $name }[] will be loaded upon the next map change.
vote-event-cancelled = { "[" }lightgray]The vote to change the event to [orange]{ $name }[lightgray] was cancelled by administrator { $admin }.
event-vote = { "[" }orange]Vote
event-avote = { "[" }red]Instant Change
event-menu-vote-stop = Stop voting
event-menu-stop = Stop event
event-menu-this-event = { "[" }orange]Current Event
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = { "[" }scarlet]⚠ Access denied
error-ip-changed = { "[" }scarlet]⚠ Your IP address has changed. Admin privileges have been revoked.
error-not-enough-params = { "[" }scarlet]⚠ Not enough position params
error-player-not-found = { "[" }scarlet]Player not found
error-player-not-teammate = { "[" }scarlet]Target player is not in your team.
error-player-admin = { "[" }scarlet]⚠ Don't try to kick an admin ⚠
error-already-voted = { "[" }scarlet]⚠ You have already voted. Calm down.
error-globalchat-total-playtime = { "[" }scarlet]⚠ In order to send message to global chat you need to play for { $globalChatPlayTime } minutes.
error-votekick-total-playtime = { "[" }scarlet]⚠ In order to start a vote-kick you need to play for { $votekickPlayTime } minutes.
error-vote-yourself = { "[" }scarlet]⚠ You cannot vote on your own vote session.
error-vote-in-progress = { "[" }scarlet]⚠ A vote session is already in progress.
error-no-voting = { "[" }scarlet]⚠ There is no vote session at the moment.
error-no-map = { "[" }scarlet]⚠ Map not set
error-map-not-event = { "[" }scarlet]⚠ Map is not part of the current event.
error-map-not-found = { "[" }scarlet]⚠ Map not found! [accent]Use [cyan]/maps[] to see a list of all available maps
error-maps-empty = { "[" }scarlet]⚠ Map list is empty
error-event-not-found = { "[" }scarlet]⚠ Event not found! [accent]Use [cyan]/events[] to see the list of available events.
error-page-between = { "[" }scarlet]⚠ 'page' must be a number between[orange] 1[] and [orange]{ $totalPages }[]
error-page-number = { "[" }scarlet]'page' must be a number
error-wrong-number = { "[" }scarlet]⚠ Wrong number format
error-wrong-period-format = { "[" }scarlet]⚠ Wrong period format- Example: 1h 30m, 30 ({ hours })
error-invalid-id = { "[" }scarlet]⚠ Invalid player-id
error-spectator = { "[" }scarlet]⚠ You are a spectator. Run /spectate to return.
error-admin-password-too-short = { "[" }scarlet]⚠ Admin password must be at least 4 characters long
error-wrong-admin-password = { "[" }scarlet]⚠ Incorrect admin password
error-internal = { "[" }scarlet]Internal error
error-processing-request = { "[" }scarlet]An error occurred while processing the request.
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = hours
days = days
success = { "[" }green]Successfully
empty = { "[" }accent]Empty
never = Never
save = Save
close = Close
previous = <- Previous
next = Next ->
yes = Yes
no = No
test = Test
