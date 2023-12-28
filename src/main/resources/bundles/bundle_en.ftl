commands-help-params = [page]
commands-help-description = Lists all commands-
commands-help-start-content = [orange]-- Commands Page[lightgray] {$page}[gray]/[lightgray]{$totalPages}[orange] --
commands-help-content = [orange] /{$commandName}[white] {$commandParams}[lightgray] - {$commandDescription}
commands-t-params = <message...>
commands-t-description = Send a message only to your teammates
commands-g-params = <message...>
commands-g-description = Send a message across all servers
commands-t-chat = [{"#"}{$color}][Team] [coral]>[accent] {$name}[coral]:[white] {$message}
commands-a-params = <message...>
commands-a-description = Send a message only to admins-
commands-sync-params = {""}
commands-sync-description = Re-synchronize world state-
commands-discord-params = {""}
commands-discord-description = Redirects you to discord server
commands-js-params = <code...>
commands-js-description = Execute javascript- [scarlet]JS Access users only
commands-artv-params = [map...]
commands-artv-description = Change map- [scarlet]Admin only
commands-artv-map-skipped = {$nickname}[accent] skipped map-
commands-rtv-params = [map...]
commands-rtv-description = Rock the vote to change map
commands-stats-params = [player-id]
commands-stats-description = Player Statistics
commands-stats-content = {$nickname} [grey]#{$pid} Statistics
    {""}[brown]PlayTime: [grey]{$totalPlayTime}[] minutes
    Hexed Rank: [grey]{$hexedRankTag} {$hexedRankName}
    MiniPvP rating: {$pvpRating}
commands-lb-params = {""}
commands-lb-description = Enable/disable leaderboard
commands-lb-success = { $leaderboardEnabled ->
[true] [accent]Leaderboard [green]enabled
*[other] [accent]Leaderboard [scarlet]disabled
}
commands-login-params = <password>
commands-login-description = Admin request- Don't use if you don't know what you're doing
commands-login-incorrect-password = [scarlet]⚠ Incorrect password!
commands-login-success = [green]Admin rights granted
commands-login-confirmed = [green]Your admin request confirmed
commands-login-admin-password-created = [green]Admin password created-
    {""}[red]Don't forget your password! If you forget it, you will need to ask the general admin to reset it-
commands-login-request-approval-discord = [accent]You need to approve your admin request on discord- Channel [gray]#admin-bots[]
commands-tr-params = <language>
commands-tr-description = Set the translator language
commands-tr-success = [accent]The translator language has been successfully changed to [grey]{$translatorLanguage}[]!
commands-tr-off = [accent]Translator is [scarlet]off[]!
commands-tr-not-found = [scarlet]⚠ There is no such language
commands-maps-params = [page]
commands-maps-description = List all maps on server
commands-ban-params = <player-id> <period> [reason...]
commands-ban-description = Ban player- [scarlet]Admin only
commands-ban-success = {$nickname} [scarlet]banned
commands-unban-params = <player-id>
commands-unban-description = Unban player- [scarlet]Admin only
commands-unban-success = {$nickname}[accent] #{$pid} successfully unbanned
                                    commands-mute-params =
                                    <player-id>
                                        <period> [reason...]
commands-mute-description = Mute player- [scarlet]Admin only
commands-mute-success = [accent]Successfully muted player {$nickname}
commands-unmute-params = <player-id>
commands-unmute-description = Unmute player- [scarlet]Admin only
commands-unmute-success = Successfully unmuted player {$nickname}
commands-maps-page-must-number = [scarlet]'page' must be a number
commands-maps-start-content = [accent]Actual map: []{$mapName}[white]
    {""}[orange][gold]Maps list [lightgray]{$page}[gray]/[lightgray]{$pageCount}
commands-maps-content = {""}
    {$index}. [orange] - [white]{$mapName}[orange] | [white]{$mapWidth}x{$mapHeight}[orange] | By: [sky]{$mapAuthor}
commands-votekick-params = <ID/name> <reason...>
commands-votekick-description = Vote to kick a player-
commands-vote-params = <y/n>
commands-vote-description = Vote to kick the current player
commands-vote-vote-with = [scarlet]⚠ Vote with [orange]/vote <y/n>
commands-rank-params = [player...]
commands-rank-description = Shows information about your/player rank
commands-rank-content = {$nickname}
    {$rankTag} [accent]{$rankName}
    {""}[gold]Wins: {$points}/{$requiredPoints}
commands-ranks-params = {""}
commands-ranks-description = Shows information about ranks
commands-ranks-content = {$rankTag} [accent]{$rankName}
    {""}[gold]Requirements: [grey]{$requiredPoints} [accent]wins[]
commands-ranks-footer = The amount of wins increases only when defeating a player of your rank or higher-
commands-top-params = {""}
commands-top-description = Top players
commands-top-hexed-content = [orange]{$index}. {$nickname}[accent]: [blue]{$rankName} [cyan]{$points} []wins
commands-top-pvp-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$rating}
commands-spectate-params = {""}
commands-spectate-description = Spectate the game- This will clear your unit and change your team so you can view the game easily- Run again to return to your team-
commands-ai-params = <idle/i/attack/a>
commands-ai-description = Control ai
commands-ai-usage = [red]attack(i) []or [accent]idle(i)
rtv-vote = {$nickname}[lightgray] voted to change the current map to [orange]{$mapName}[lightgray]. ([accent]{$votes}[]/[accent]{$votesRequired}[])
    Type [orange]y[] or [orange]n[] to vote
rtv-left = {$nickname}[lightgray] left- His vote to change the current map was cancelled- ([accent]{$votes}[]/[accent]{$votesRequired}[])
rtv-fail = [lightgray]Vote failed- Not enough votes to change the current map to [orange]{$mapName}[].
rtv-success = [orange]Vote passed- Map [accent]{$mapName}[] will be loaded in [accent]{$mapLoadDelay}[] seconds...
votekick-vote = {$nickname} [grey]#[white]{$nicknameId}[lightgray] voted to kick {$targetNickname} [grey]#[white]{$targetNicknameId}[lightgray] for [orange]{$reason}[lightgray]. ([accent]{$votes}[]/[accent]{$votesRequired}[])
    {""}[lightgray]Type [orange]/vote <y/n>[] to vote-
votekick-left = {$nickname}[lightgray] left- Their vote for kicking a player was cancelled- ([accent]{$votes}[]/[accent]{$votesRequired}[])
votekick-fail = [lightgray]Vote failed- Not enough votes to kick {$nickname}[lightgray] from the server-
votekick-success = [orange]Vote passed- {$nickname}[orange] kicked from the server for [scarlet]{$minutes}[] minutes
player-joined = {$nickname} [grey]#[white]{$pid}[grey] [accent]has joined
player-left = {$nickname} [grey]#[white]{$pid}[grey] [accent]has left
notification-votekick-playtime = [accent]Congratulations! You have played for [lightgray]{0}[] minutes and can now start a vote-kick-
notification-global-chat-playtime = [accent]Congratulations! You have played for [lightgray]{0}[] minutes and can now send messages to global chat-
    {""}[lightgray]Type [accent]/g [gray]<message...>[lightgray] to send a message-

error-access-denied = [scarlet]⚠ Access denied
error-ip-changed = [scarlet]⚠ Your IP address has changed- Admin privileges have been revoked-
error-not-enough-params = [scarlet]⚠ Not enough position params
error-player-not-found = [scarlet]Player not found
error-player-not-teammate = [scarlet]Target player is not in your team-
error-player-admin = [scarlet]⚠ Don't try to kick an admin ⚠
error-already-voted = [scarlet]⚠ You have already voted- Calm down-
error-globalchat-total-playtime = [scarlet]⚠ In order to send message to global chat you need to play for {$globalChatPlayTime} minutes-
error-votekick-total-playtime = [scarlet]⚠ In order to start a vote-kick you need to play for {$votekickPlayTime} minutes-
error-vote-yourself = [scarlet]⚠ You cannot vote for yourself-
error-vote-in-progress = [scarlet]⚠ A vote session is already in progress-
error-no-voting = [scarlet]⚠ No voting at the moment-
error-map-not-found = [scarlet]⚠ Map not found! [accent]Use [cyan]/maps[] to see a list of all available maps
error-page-between = [scarlet]⚠ 'page' must be a number between[orange] 1[] and [orange]{$pageCount}[]
error-page-number = [scarlet]'page' must be a number
error-wrong-number = [scarlet]⚠ Wrong number format
error-wrong-period-format = [scarlet]⚠ Wrong period format- Example: 1h 30m, 30 ({hours})
error-invalid-id = [scarlet]⚠ Invalid player-id
error-spectator = [scarlet]⚠ You are a spectator-
error-admin-password-too-short = [scarlet]⚠ Admin password must be at least 4 characters long
error-wrong-admin-password = [scarlet]⚠ Wrong admin password

hours = hours
days = days
support-channel = #reports-appeals
ban-content = {$nickname} [accent]have been banned-
    To appeal the ban, visit discord(channel [gray]{support-channel}[])
    {""}[cyan]{$discordUrl}
ban-cancelled = [accent]Player [scarlet]{$nickname}[accent] ban has been cancelled
tempban-content = {$nickname}[accent] have been banned-
    Admin: {$adminName}[accent]
    Reason: "[gold]{$reason}[]"
    You will be unbanned in: {$days} days, {$hours} hours and {$minutes} minutes
    To appeal your ban, visit discord(channel [gray]{support-channel}[]):
    {""}[cyan]{$discordUrl}
tempban-player-banned = [scarlet] Admin {$adminName}[scarlet] banned player [gray]'[]{$playerName}[gray]'

you-are-muted-by = [scarlet]You were muted by administrator [accent]{$adminName}[blue] for {$remainMinutes}:{$remainSeconds} minutes,
    reason: {$reason}
you-are-muted = [scarlet]You can't write in the chat. [accent]You are muted by administrator {$adminName}[blue] for {$remainMinutes}:{$remainSeconds} minutes,
    reason: {$reason}
success = [green]Successfully
empty = [accent]Empty
leaderboard = [blue]Leaderboard
pvp-team-won = Your team has won. Your rating has been increased by {0}
pvp-team-lose = Your team lost. Your rating has been reduced by {0}
pvp-leaderboard-content = [orange]{$index}. {$nickname}[accent]:[cyan] {$rating} [accent]rating
hexed-popup = [blue]{$minutes}:{$seconds}[] until endgame
hexed-eliminated = {$nickname} [gold]eliminated!
hexed-leaderboard-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$hexes} [accent]hexes
hexed-ranks-newbie = Newbie
hexed-ranks-regular = Regular
hexed-ranks-advanced = Advanced
hexed-ranks-veteran = Veteran
hexed-ranks-davastator = Devastator
hexed-ranks-the_legend = The Legend
pvp-you-spectator = [scarlet]You lost. Please wait for the next game
kick-pirated-game = [accent]Playing on a pirated version of the game is forbidden- Install the official version of the game from [blue]Apple App Store or Google Play Store[] or [blue]https://anuke-itch-io/mindustry
kick-recently-kicked = [accent]You were recently kicked from this server
    Wait [cyan]{$remainMinutes}:{$remainSeconds}[accent]
welcome = [accent]Welcome to {$serverName}!
    {""}[lightgray]Type [accent]/help[lightgray] to see a list of commands
    {""}[lightgray]Type [accent]/vote [gray]<y/n>[lightgray] to vote for kicking a player
    {""}[lightgray]Type [accent]/votekick [gray]<ID/name> <reason...>[lightgray] to start a vote-kick
    {""}[lightgray]Type [accent]/t [gray]<message...>[lightgray] to send a message to your teammates
    {""}[lightgray]Type [accent]/g [gray]<message...>[lightgray] to send a message to all servers
    {""}[lightgray]Type [accent]/tr [gray]<language/auto>[lightgray] to enable the translator
    {""}[lightgray]Type [accent]/discord[lightgray] to redirect you to our discord server
