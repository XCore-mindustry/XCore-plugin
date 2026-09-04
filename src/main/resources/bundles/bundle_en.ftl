# ==============================================================================
# Terms
# ==============================================================================
-xcore = XCore server
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Main menu
commands-main-description = Open the interactive Main menu.
menu-main-title = [orange]{ -xcore } — Main menu
menu-main-content = Main server menu
help-menu = Help menu
commands-help-description = Open the interactive help menu.
help-menu-title = [orange]{ -xcore } — Commads
help-menu-content =
    [gray]Page [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Select a command to see detailed usage:
help-menu-button = [accent]/{ $command } [gray]» Description: [white]{ $description }
help-command-with-overload-count = { $name } of ({ $count })
help-command-title = [orange]» Name: [white]/{ $name }
help-command-header =
    [orange]» [accent]Syntax: [white]{ $syntax }
    [orange]» [accent]Info: [lightgray]{ $description }
help-aliases = [orange]» [accent]Aliases: [white]{ $aliases }
help-args-title = [orange]» [accent]Arguments:
help-usages-title = [orange]» [accent]Usage:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]For [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]No additional arguments required.
help-no-arg-description = No description.
help-no-description = No description provided for this command.
help-legacy-command-content =
    [orange]» [accent]Command: [white]/{ $name }
    [orange]» [accent]Parameters: [white]{ $params }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](This is a legacy command with limited info)
help-legacy-command-content-no-params =
    [orange]» [accent]Command: [white]/{ $name }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](This is a legacy command with limited info)
help-back = [lightgray]« Back
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Page number to display.
# login
commands-login-password-description = Your admin password.
# ban
commands-ban-id-description = Player ID to ban.
commands-ban-period-description = Ban duration (e.g. 1d, 2h, 30m).
commands-ban-reason-description = Reason for the ban.
# unban
commands-unban-id-description = Player ID to unban.
# mute
commands-mute-id-description = Player ID to mute.
commands-mute-period-description = Mute duration (e.g. 1h, 30m).
commands-mute-reason-description = Reason for the mute.
# unmute
commands-unmute-id-description = Player ID to unmute.
# votekick
commands-votekick-target-description = Player to kick (ID or name).
commands-votekick-reason-description = Reason for the kick.
# vote
commands-vote-choice-description = Your vote: y (yes), n (no), or c (cancel, admin only).
# t (team chat)
commands-t-message-description = Message to send to teammates.
# g (global chat)
commands-g-message-description = Message to send to all servers.
# tr (translator)
commands-tr-language-description = Language code, 'auto', or 'off'.
# stats
commands-stats-id-description = Player ID to view stats for
# rank
commands-rank-player-description = Player to view rank for
# map
commands-map-map-description = Map name or index.
# maps / maps-text
commands-maps-page-description = Page number.
commands-maps-text-page-description = Page number.
# rtv / artv
commands-rtv-map-description = Map to vote for (optional).
commands-artv-map-description = Map to force change to.
# ai
commands-ai-state-description = AI state: attack (a) or idle (i).
# event / events
commands-events-page-description = Page number.
# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Show information about the server.
commands-info = Information
commands-info-title = [orange]{ -xcore } — Server name: [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] is a [cyan]free[white] server for playing [accent]Mindustry[white].
    { "" }
    XCore Version — [accent]{ $version }[white]
commands-sync-description = Sync your game with the server. Run this to fix errors like ghost units.
commands-discord-description = Redirects you to discord server.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Manage your Discord connection here.
    { "" }
    [white]Status: { $status }
    [white]Server: [accent]{ $discordUrl }[]
discord-menu-open = Open Discord
discord-menu-link = Link account
discord-menu-status = Refresh status
discord-menu-unlink = Unlink account
discord-menu-status-not-linked = [lightgray]not linked[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Link Discord account
discord-link-menu-content =
    [white]On our Discord server, run the bot slash command:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Expires in: [accent]{ $expireMinutes }[] min
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Refresh code
discord-link-menu-copy = Copy code
discord-link-menu-regenerate = Generate new code
discord-link-menu-status = Back to Discord menu
welcome =
    [accent]Welcome to { $serverName }!
    [lightgray]Type [accent]/help[lightgray] to see a list of commands
    [lightgray]Type [accent]/vote [gray]<y/n>[lightgray] to vote for kicking a player
    [lightgray]Type [accent]/votekick [gray]<ID/name> <reason…>[lightgray] to start a vote-kick
    [lightgray]Type [accent]/t [gray]<message…>[lightgray] to send a message to your teammates
    [lightgray]Type [accent]/g [gray]<message…>[lightgray] to send a message to all servers
    [lightgray]Type [accent]/tr [gray]<language/auto>[lightgray] to enable the translator
    [lightgray]Type [accent]/discord[lightgray] to open the Discord menu and link your account
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Send a message only to your teammates.
commands-t-chat = [{ "#" }{ $color }][Team] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Send a message across all servers.
commands-a-description = Send a message only to admins.
commands-msg-description = Send a private message to a player.
commands-msg-id-description = Player ID.
commands-msg-message-description = Private message text.
commands-reply-description = Reply to the last player in private messages.
commands-reply-message-description = Private reply text.
commands-inbox-description = Open the private messages menu.
commands-inbox-id-description = Player ID.
commands-tr-description = Set the translator language.
commands-badge-description = Open the badge menu and manage your active badge.
commands-tr-success = [accent]The translator language has been successfully changed to [grey]{ $translatorLanguage }[]!
commands-tr-off = [accent]Translator is [scarlet]off[]!
commands-tr-not-found = [scarlet]⚠ There is no such language.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBAL [lightgray](from [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][PM][] [lightgray]from [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][PM][] [lightgray]to [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]You have [white]{ $count }[accent] unread private { $count ->
        [one] message
       *[other] messages
    }.
private-message-join-notification =
    [accent]You have [white]{ $count }[accent] unread private { $count ->
        [one] message
       *[other] messages
    }. Use [white]/inbox[accent] to open { $count ->
        [one] it
       *[other] them
    }.
private-message-block-success = [accent]Private messages from [white]{ $target } [gray]#{ $pid }[accent] are now blocked.
private-message-block-already = [lightgray]Private messages from [white]{ $target } [gray]#{ $pid }[lightgray] are already blocked.
private-message-unblock-success = [accent]Private messages from [white]{ $target } [gray]#{ $pid }[accent] are no longer blocked.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] is not blocked.
private-message-menu-title = [orange]{ -xcore } — Private messages
private-message-menu-content =
    [white]Page [green]{ $page }[] of [green]{ $total }[]
    [white]Unread: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]Your inbox is empty.
private-message-menu-entry-unread = [accent]Unread[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Read[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Message
private-message-details-content =
    [white]From: [accent]{ $author } [gray]#{ $pid }[]
    [white]Time: [accent]{ $time }[]
    [white]Status: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = unread
private-message-status-read = read
private-message-blocked-title = [orange]{ -xcore } — Blocked players
private-message-blocked-content =
    [white]Page [green]{ $page }[] of [green]{ $total }[]
    [white]Blocked: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]You have no blocked players.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = New message
private-message-blocked = Blocked
private-message-block = Block sender
private-message-unblock = Unblock sender
private-message-reply-title = Reply
private-message-reply-message = Enter a message for [accent]#{ $pid }[]
private-message-compose-target-title = New message
private-message-compose-target-message = Enter the player ID in format [accent]#123[]
private-message-compose-body-title = Message text
private-message-compose-body-message = Enter a private message for [accent]{ $pid }[]
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Activate admin rights if your linked Discord account already has access.
commands-login-incorrect-password = [scarlet]⚠ Incorrect password!
commands-login-success = [green]Admin rights granted.
commands-login-confirmed = [green]Discord admin access confirmed.
commands-login-admin-password-created =
    [green]Admin password created.
    [red]Don't forget your password! If you forget it, you will need to ask a general administrator to reset it.
commands-login-request-approval-discord = [accent]Your account does not have Discord admin access. Get the admin role in Discord and try again.
commands-discord-link-created =
    [green]Discord link code created: [accent]{ $code }[]
    [lightgray]On our Discord server, run the bot slash command [accent]/link { $code }[] within [accent]{ $expireMinutes }[] min.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Discord account linked: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]This Mindustry account is already linked. Use [accent]/discord status[] or [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Failed to create Discord link code. Try again later.
commands-discord-status-not-linked = [lightgray]Your account is not linked to Discord.
commands-discord-status-linked = [green]Linked Discord: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Your account is not linked to Discord.
commands-discord-unlink-success = [green]Discord link removed.
commands-logout-description = Log out. This will [scarlet]revoke your admin rights.
commands-logout-successful = [green]Admin rights revoked.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Ban a player. [scarlet]Admin only.
commands-ban-success = { $nickname } [scarlet]banned
commands-unban-description = Unban a player. [scarlet]Admin only.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]successfully unbanned.
commands-mute-description = Mute a player. [scarlet]Admin only.
commands-mute-success = [accent]Successfully muted { $nickname }
commands-unmute-description = Unmute player. [scarlet]Admin only.
commands-unmute-success = [green]Successfully unmuted []{ $nickname }
ban-content = [scarlet]⚠ Banned[]
    [accent]{ $nickname }[white] — you are permanently banned from this server.
    [lightgray]To appeal, visit Discord channel [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Player [scarlet]{ $nickname }[accent] ban has been cancelled
tempban-content = [scarlet]⚠ Banned[]
    [accent]{ $nickname }[white] — you are temporarily banned from this server.
    { "" }
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Reason: [gold]{ $reason }
    [orange]» [accent]Time left: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
    [orange]» [accent]Expires: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    { "" }
    [lightgray]To appeal, visit Discord channel [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] Admin { $adminName }[scarlet] banned player [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Chat restricted[]
    [lightgray]You were muted by administrator [accent]{ $adminName }[lightgray].
    [orange]» [accent]Reason: [gold]{ $reason }
    [orange]» [accent]Time left: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Chat restricted[]
    [lightgray]You cannot send messages while this mute is active.
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Reason: [gold]{ $reason }
    [orange]» [accent]Time left: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Unauthorized client detected. [scarlet]Access denied[]. Please play using the [lime]official[] version from [blue]Steam[], [blue]Google Play[], or [blue]itch.io[].
kick-recently-kicked =
    [accent]You were recently kicked from this server.
    Wait [cyan]{ DURATION($remaining, style: "timer") }[accent] before joining again.
kick-admintools-outdated =
    [green]The required AdminTools version: [grey]{ $requiredVersion }[]
    [scarlet]Your AdminTools version: [grey]{ $version }[]
    { "" }
    [cyan]Please update your AdminTools to join this server.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Vote to kick a player from the server.
commands-vote-description = Vote in the current active vote.
commands-vote-vote-with = [scarlet]⚠ Vote with [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] voted to kick { $target } [grey]#[white]{ $targetId }[lightgray] for [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Type [orange]/vote <y/n>[] to vote.
votekick-left = { $player }[lightgray] left. Their vote was cancelled. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Vote failed. Not enough votes to kick { $target }[lightgray].
votekick-cancelled = [scarlet]Vote to kick { $target }[scarlet] was cancelled by { $admin }.
votekick-success =
    [orange]Vote passed. { $target }[orange] kicked for [scarlet]{ $minutes }[] { $minutes ->
        [one] minute
       *[other] minutes
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Statistics of a specific map.
commands-map-title = [orange]{ -xcore } — Map
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [gray]by [sky]{ $author }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Overview[]
    [gray]Size: [white]{ $width }x{ $height } [darkgray]|[gray] Votes: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Activity[]
    [gray]Played total: [white]{ $played } [darkgray]|[gray] This year: [white]{ $playedYear }[]
    [gray]Last played: [white]{ $lastPlayed }[]
    [gray]Popularity: [white]{ $popularity } [darkgray]|[gray] Interest: [white]{ $interest } [darkgray]|[gray] Reputation: [white]{ $reputation }[]
    { "" }
    [accent]■ Match duration[]
    [gray]Min: [white]{ $min } [darkgray]|[gray] Avg: [white]{ $avg } [darkgray]|[gray] Max: [white]{ $max }[]
commands-maps-description = List of all maps on this server.
commands-maps-title = [orange]{ -xcore } — Maps
commands-maps-content =
    [gray]Current map: [accent]{ $current }[]
    [white]Page [green]{ $page }[] of [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = List of all maps on this server.
commands-maps-text-start-content =
    [accent]Current map: []{ $name }[white]
    [orange][gold]Map list [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | By: [sky]{ $author }
commands-artv-description = Force change map. [scarlet]Admin only.
commands-artv-map-skipped = { $nickname }[accent] skipped map. Next map: { $name }.
commands-artv-event-skipped = { $nickname }[accent] skipped event. Next event: { $name }.
commands-rtv-description = Rock the vote to change map.
commands-vnw-description = Vote to start the next wave early.
commands-avnw-description = Force start the next wave early. [scarlet]Admin only.
commands-like-description = Vote for the current map (increases reputation).
commands-dislike-description = Vote against the current map.
map-vote-title = [orange]{ -xcore } — [scarlet]GAME OVER!
map-vote-content =
    { "" }
    Next map: [accent]{ $mapName }[] by [accent]{ $author }[white].
    New game starts in [accent]{ $seconds }[white] { $seconds ->
        [one] second
       *[other] seconds
    }.
    { "" }
    [cyan]Did you like this map?
map-vote-like = [green]👍 Like
map-vote-dislike = [red]👎 Dislike
map-vote-like-selected = [gray]You liked it
map-vote-dislike-selected = [gray]You disliked it
map-rtv = [orange]Voting
map-artv = [red]Instant Change
map-maps = Maps
map-maps-back = ← Back to map list
current-map = Current map
next-map = Next map
rtv-vote =
    { $nickname }[lightgray] voted to change the current map to [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Type [orange]y[] or [orange]n[] to vote.
rtv-left = { $nickname }[lightgray] left. Their vote to change the current map was cancelled. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Vote failed. Not enough votes to change the current map to [orange]{ $mapName }[].
rtv-success = [orange]Vote passed. Map [accent]{ $mapName }[] will be loaded in [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
    [one] second
   *[other] seconds
}…
rtv-cancelled = [lightgray]Vote to change the current map to [orange]{ $mapName }[lightgray] was cancelled by { $admin }.
vnw-vote =
    { $nickname }[lightgray] voted to start wave [orange]{ $wave }[lightgray] early. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Type [orange]y[] or [orange]n[] to vote.
vnw-left = { $nickname }[lightgray] left. Their vote to start wave [orange]{ $wave }[lightgray] early was cancelled. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Vote failed. Not enough votes to start wave [orange]{ $wave }[] early.
vnw-success = [orange]Vote passed. Wave [accent]{ $wave }[] is starting now.
vnw-cancelled = [lightgray]Vote to start wave [orange]{ $wave }[lightgray] early was cancelled by { $admin }.
vnw-obsolete = [lightgray]Wave [orange]{ $wave }[lightgray] has already started, so the vote result is no longer needed.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = View a player's Statistics.
commands-settings-description = Open your player settings.
player-menu-player = Player
player-menu-player-title = [orange]{ -xcore } — Player Statistics
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profile[]
    [gray]Name: [white]{ $nickname } [darkgray]|[gray] Admin: [lime]{ $admin }[]
    [gray]Badge: [white]{ $activeBadge } [darkgray]|[gray] System: [coral]{ $systemBadge }[]
    [gray]Joined: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Game Ratings[]
    [gray]Play time: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Hexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } pts)[]
    [lightgray]{ $hexedProgress }[]
    { "" }
    [accent]■ Matches: [white]{ $gamesPlayed } [gray]games [darkgray]|[lime] { $gamesWon } [gray]wins [darkgray]|[sky] { $winRate }% [gray]win rate[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Surv: { $survivalSummary }[]
    [gray]• [white]Hexed: { $hexedSummary }[]
    { "" }
    [accent]■ Combat Efficiency[]
    [gray]Blocks (Build/Decon/Destroy): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]
player-menu-players = Online players
player-menu-players-title = [orange]{ -xcore } — Online players
player-menu-players-content = [white]Page [green]{ $page }[] of [green]{ $total }[]
player-menu-players-empty = No online players found
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Settings
player-menu-settings-title = [orange]{ -xcore } — Player Settings
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profile[]
    [gray]Name: [white]{ $nickname } [darkgray]|[gray] Display: [lime]{ $customNickname }[]
    [gray]Badge: [white]{ $activeBadge } [darkgray]|[gray] System: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Visibility[]
    [gray]Leaderboard: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Chat[]
    [gray]Global: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Translator: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Localization[]
    [gray]Language: [white]{ $language }[]
player-menu-settings-chat = Chat settings
player-menu-settings-chat-title = [orange]{ -xcore } — Chat Settings
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Chat visibility[]
    [gray]Global chat: [white]{ $globalChat }[]
    [gray]Discord relay: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Translation[]
    [gray]Translator language: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Selection Translator language
player-menu-settings-language-title = [orange]{ -xcore } — Selection language
player-menu-settings-customNickname = Edit name
player-menu-settings-customNickname-title = [orange]{ -xcore } — Edit name
player-menu-settings-customNickname-message = [lightgray]Leave blank to reset
player-menu-settings-customNickname-reset = [scarlet]Reset name
player-menu-settings-description = Edit description
player-menu-settings-description-title = [orange]{ -xcore } — Edit description
player-menu-settings-badges = Badges
player-menu-settings-global-chat-on = [green]Global chat
player-menu-settings-global-chat-off = [red]Global chat
player-menu-settings-discord-relay-on = [green]Discord relay
player-menu-settings-discord-relay-off = [red]Discord relay
audit-menu-open = Audit history
audit-menu-actions-open = Actions
audit-menu-history-title = [orange]{ -xcore } — Audit history
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Entries shown: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Newest entries
audit-menu-history-page-older = Older entries
audit-menu-history-more = More entries available
audit-menu-history-end = End of history
audit-menu-history-empty = No audit entries found for this player yet.
audit-menu-history-hint = Select an entry below to inspect details.
audit-menu-actions-title = [orange]{ -xcore } — Audit actions
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Entries shown: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = No audit actions found for this player yet.
audit-menu-actions-hint = Select an entry below to inspect what this player did.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Audit details
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Audit event[]
    [gray]Action: [white]{ $action }[]
    [gray]Actor: [white]{ $actor }[]
    [gray]Reason: [white]{ $reason }[]
    { "" }
    [accent]■ Timing[]
    [gray]Occurred: [white]{ $occurredAt }[]
    [gray]Duration: [white]{ $duration }[]
    [gray]Expires: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Metadata[]
    [gray]Audit ID: [white]{ $auditId }[]
audit-menu-unknown-actor = Unknown
audit-menu-unknown-target = Unknown
audit-menu-reason-unspecified = Not specified
audit-menu-duration-permanent = Permanent
audit-menu-action-ban = Ban
audit-menu-action-unban = Unban
audit-menu-action-mute = Mute
audit-menu-action-unmute = Unmute
audit-menu-action-warn = Warn
audit-menu-action-kick = Kick
audit-menu-action-note = Note
audit-menu-action-quarantine = Quarantine
audit-menu-action-unquarantine = Unquarantine
player-menu-player-max-rank = Max rank reached
player-menu-player-hexed-progress = [gray]Wins needed for [white]{ $nextRankName }[gray]: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]no data[]
player-menu-player-pvp-summary = [gray]games [white]{ $gamesPlayed }[], wins [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]waves: max [lime]{ $bestWave }[], avg [white]{ $averageWave }[] [gray](runs: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]matches [white]{ $gamesPlayed }[], top-1 [lime]{ $gamesWon }[], best place [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value }d
player-menu-time-hours = { $value }h
player-menu-time-minutes = { $value }m
settings-language-label = Language: [green]{ $lang }[]
settings-translator-label = Translator: [green]{ $lang }[]
badge-menu-title = [orange]{ -xcore } — Badges
badge-menu-content =
    [white]System Badge: [green]{ $systemBadge }[]
    [white]Active Badge: [green]{ $activeBadge }[]
    [white]Symbol Color: [green]{ $symbolColorMode }[]
badge-menu-empty = [lightgray]You do not have any unlocked badges yet.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Symbol color: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Badge symbol color
badge-menu-symbol-color-content =
    [white]Current mode: [green]{ $mode }[]
    [lightgray]Choose how the badge symbol should be colored.
badge-menu-symbol-color-default = Default badge color
badge-menu-symbol-color-player-color = Match player color
badge-menu-view-all = View all badges
badge-menu-all-title = [orange]{ -xcore } — All badges
badge-menu-all-content = [lightgray]Browse all badges, their status, and descriptions.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Clear active badge
badge-state-system = System
badge-state-system-active = Active system
badge-state-active = Active
badge-state-unlocked = Unlocked
badge-state-locked = Locked
badge-set-success = [accent]Active badge set to [green]{ $badge }[].
badge-clear-success = [accent]Active badge cleared.
badge-grant-success = [accent]Granted [green]{ $badge }[] to [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent]Revoked [green]{ $badge }[] from [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ Badge [accent]{ $badge }[scarlet] is already unlocked.
badge-not-owned = [scarlet]⚠ Player does not own badge [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ Badge [accent]{ $badge }[scarlet] was not found.
error-badge-not-unlocked = [scarlet]⚠ Badge [accent]{ $badge }[scarlet] is not unlocked.
error-badge-not-selectable = [scarlet]⚠ Badge [accent]{ $badge }[scarlet] cannot be selected manually.
badge-admin-name = Admin
badge-admin-description = Automatic badge shown for administrators.
badge-developer-name = Developer
badge-developer-description = Awarded to XCore developers.
badge-translator-name = Translator
badge-translator-description = Awarded to contributors who translate XCore.
badge-map-maker-name = Map Maker
badge-map-maker-description = Awarded to creators of maps used on the server.
badge-contributor-name = Contributor
badge-contributor-description = Awarded for contributions to XCore or its community.
badge-bug-finder-name = Bug Finder
badge-bug-finder-description = Awarded for regular high-quality bug reports.
badge-event-winner-name = Event Winner
badge-event-winner-description = Awarded to winners of special server events.
badge-veteran-name = Veteran
badge-veteran-description = Awarded to long-term respected players.
commands-lb-description = Enable/disable leaderboard.
commands-lb-success =
    { $leaderboardEnabled ->
        [true] [accent]Leaderboard [green]enabled.
       *[other] [accent]Leaderboard [scarlet]disabled.
    }
leaderboard = [blue]Leaderboard
commands-observer-description = Switch to observer mode. This removes your current unit and moves you to the spectator team.
commands-rank-description = Show your rank or another player's rank.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Wins: { $points }/{ $requiredPoints }
commands-ranks-description = Shows information about ranks.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Requirements: [grey]{ $requiredPoints } [accent]wins[]
commands-ranks-footer = The amount of wins increases only when defeating a player of your rank or higher.
commands-top-description = Top players.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []wins
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Top Players: [accent]{ $category }
top-menu-content =
    [lightgray]Select a player to open their profile.[]
    [lightgray]Page [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Players: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Category: [green]{ $category }[]
    [gray]No players found yet.
top-menu-categories-title = [orange]{ -xcore } — Top Category
top-menu-categories-content =
    [lightgray]Choose which ranking to show.[]
    [lightgray]Current: [green]{ $category }[]
top-menu-category-button = [accent]Category: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Playtime
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Your position: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Your position: [gray]not found[]
top-menu-entry-mini-pvp = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Surrender in Hexed. This destroys your current team, removes your unit, and moves you to the spectator team.
commands-surrender-success = [green]You surrendered and are now spectating
commands-observer-success = [green]You are now spectating
commands-observer-exit-success = [green]You are no longer spectating
commands-ai-description = Control AI.
commands-ai-usage = [red]attack(i) []or [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] until the game ends.
hexed-eliminated = { $nickname } [gold]has been [scarlet]eliminated[]!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]hexes
hexed-ranks-newbie = Newbie
hexed-ranks-regular = Regular
hexed-ranks-advanced = Advanced
hexed-ranks-veteran = Veteran
hexed-ranks-davastator = Devastator
hexed-ranks-the_legend = The Legend
hexed-game-over-header = Game Over. Winners:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] hex
       *[other] hexes
    }
hexed-game-over-no-winners = Game Over. Unfortunately, I couldn't find the winning players.
hexed-game-over-restart = New game in 10 seconds…
pvp-team-won = Your team has won. Your rating has been increased by { $increased }
pvp-team-lose = Your team has lost. Your rating has been reduced by { $reduced }
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]rating
pvp-you-spectator = [scarlet]You have been eliminated. Please wait for the next game.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined = { $nickname } [grey]#[white]{ $pid }[grey] [accent]has joined.
player-left = { $nickname } [grey]#[white]{ $pid }[grey] [accent]has left.
notification-votekick-playtime =
    [accent]Congratulations! You have played for [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
        [one] minute
       *[other] minutes
    } and can now start a vote-kick.
notification-global-chat-playtime =
    [accent]Congratulations! You have played for [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
        [one] minute
       *[other] minutes
    } and can now send messages to global chat.
    [lightgray]Type [accent]/g [gray]<message…>[lightgray] to send a message.
notification-admin-kick = { $admin }[accent] kicked { $target }[].
notification-admin-wave-skip = { $admin }[accent] has skipped the wave.
server-restart-countdown =
    Restart in { $seconds ->
        [one] { $seconds } second
       *[other] { $seconds } seconds
    }
like-map-success = [green]You liked this map!
like-map-changed = [green]You changed your mind to a Like!
dislike-map-success = [orange]You disliked this map.
dislike-map-changed = [orange]You changed your mind to a Dislike.
like-event-success = [green]You liked this event!
like-event-changed = [green]You changed your mind to a Like!
dislike-event-success = [orange]You disliked this event.
dislike-event-changed = [orange]You changed your mind to a Dislike.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Event management menu.
commands-events-description = List of all events on the servers.
event-events = Events
event-menu-main = Main events
event-menu-main-title = [orange]{ -xcore } — Events
event-menu-main-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Event hub[]
    [lightgray]Browse active and planned server events from one place.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Current event[]
    [gray]Status: [white]{ $currentEventState }[]
    [gray]Selected: [white]{ $currentEventName }[]
    { "" }
    [accent]■ Voting[]
    [gray]Vote session: [white]{ $voteStatus }[]
    { "" }
    [accent]■ Actions[]
    [gray]Open the catalog, inspect the current event, or prepare a new one.[]
event-menu-event = Event
event-menu-event-title = [orange]{ -xcore } — Event
event-menu-event-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Overview[]
    [gray]Author: [white]{ $author }[]
    [gray]Map: [white]{ $mapName }[]
    [gray]Type: [white]{ $eventType }[] [darkgray]|[gray] State: [white]{ $eventState }[]
    [gray]Temporary: [white]{ $isTemporary }[]
    { "" }
    [accent]■ Schedule[]
    [gray]Created: [white]{ $createdEventTime }[]
    [gray]Planned start: [white]{ $plannedStartTime }[]
    [gray]Planned end: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Reputation[]
    [gray]Likes: [white]{ $like }[] [darkgray]|[gray] Dislikes: [white]{ $dislike }[]
event-menu-event-map = View map
event-menu-events = Events list
event-menu-events-title = [orange]{ -xcore } — Events List
event-menu-events-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Event catalog[]
    [lightgray]Page [green]{ $page }[]/[green]{ $total }[] [gold]•[] [lightgray]Events: [green]{ $count }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filters[]
    [gray]Finished: [white]{ $finished }[]
    [gray]Major: [white]{ $major }[] [darkgray]|[gray] Active: [white]{ $active }[]
    { "" }
    [accent]■ List[]
    [gray]Select an event below to inspect its card.[]
event-menu-events-empty =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Event catalog[]
    [lightgray]There are no events matching the current filters yet.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filters[]
    [gray]Finished: [white]{ $finished }[]
    [gray]Major: [white]{ $major }[] [darkgray]|[gray] Active: [white]{ $active }[]
event-menu-events-row = [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-events-selected = [green]●[] [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-create-start = Create
event-menu-create-start-title = [orange]{ -xcore } — Event Creation
event-menu-create-start-message = Enter the name of the future event
event-menu-create-start-default = { $playerName }'s Event
event-menu-create-start-map = Create event for this map
event-menu-edit = Edit
event-menu-edit-title = [orange]{ -xcore } — Edit Event
event-menu-edit-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Identity[]
    [gray]Author: [white]{ $author }[]
    [gray]Type: [white]{ $eventType }[]
    { "" }
    [accent]■ Map[]
    [gray]Selected map: [white]{ $mapName }[]
    { "" }
    [accent]■ Schedule[]
    [gray]Planned start: [white]{ $plannedStartTime }[]
    [gray]Planned end: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Flags[]
    [gray]Temporary: [white]{ $isTemporary }[]
event-menu-edit-name = Name
event-menu-edit-name-reset = [scarlet]Reset name
event-menu-edit-name-title = [orange]{ -xcore } — Edit Event
event-menu-edit-name-message = Edit name:
event-menu-edit-description = Description
event-menu-edit-description-title = [orange]{ -xcore } — Edit Event
event-menu-edit-description-message = Edit description:
event-menu-edit-map = Edit map
event-menu-edit-temporary-active = [green]Temporary
event-menu-edit-temporary-inactive = [gray]Temporary
event-menu-edit-major-active = [green]Major
event-menu-edit-major-inactive = [gray]Major
event-menu-edit-planned-start = Event start
event-menu-edit-planned-start-title = [orange]{ -xcore } — Edit Event
event-menu-edit-planned-start-message = Enter start time in ms or using m/h/d:
event-menu-edit-planned-end = Event end
event-menu-edit-planned-end-title = [orange]{ -xcore } — Edit Event
event-menu-edit-planned-end-message = Enter end time in ms or using m/h/d:
event-menu-maps = Maps
event-menu-maps-title = [orange]{ -xcore } — Select Map
event-menu-maps-content = [white]Page [green]{ $page }[] of [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] voted to change the current event to [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Type [orange]y[] or [orange]n[] to vote.
vote-event-left = { $nickname }[lightgray] left. Their vote to change the event has been cancelled. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Vote failed. Not enough votes to change the event to [orange]{ $name }[].
vote-event-success = [orange]Vote passed. The event [accent]{ $name }[] will be loaded upon the next map change.
vote-event-cancelled = [lightgray]The vote to change the event to [orange]{ $name }[lightgray] was cancelled by administrator { $admin }.
event-vote = [orange]Vote
event-avote = [red]Instant Change
event-menu-vote-stop = Stop voting
event-menu-stop = Stop event
event-menu-this-event = [orange]Current Event
event-menu-type-major = Major event
event-menu-type-regular = Regular event
event-menu-state-none = No active event
event-menu-state-planned = Planned
event-menu-state-active = Active now
event-menu-state-finished = Finished
event-menu-vote-status-running = Running
event-menu-vote-status-idle = Not running
date-time-picker-title = [orange]{ -xcore } — Date & Time
date-time-picker-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $field }[]
    [lightgray]Current value: [white]{ $value }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Date[]
    [gray]Pick a day first, then refine the time below.[]
    { "" }
    [accent]■ Time[]
    [gray]Use presets or fine adjustments for precise planning.[]
    { "" }
    [accent]■ Manual input[]
    [gray]Use manual entry only when you need exact milliseconds or a relative +m/+h/+d value.[]
date-time-picker-field-generic = Planned time
date-time-picker-today = Today
date-time-picker-tomorrow = Tomorrow
date-time-picker-plus-2d = +2 days
date-time-picker-plus-7d = +7 days
date-time-picker-now = Now
date-time-picker-time-0000 = 00:00
date-time-picker-time-0600 = 06:00
date-time-picker-time-1200 = 12:00
date-time-picker-time-1800 = 18:00
date-time-picker-minus-1d = -1d
date-time-picker-plus-1d = +1d
date-time-picker-minus-1h = -1h
date-time-picker-plus-1h = +1h
date-time-picker-minus-15m = -15m
date-time-picker-plus-15m = +15m
date-time-picker-reset = Reset
date-time-picker-manual = Manual input
date-time-picker-manual-title = [orange]{ -xcore } — Manual Time Input
date-time-picker-manual-message = Enter absolute milliseconds or relative time like +30m, +2h, +1d.
event-end = The [green]{ $name }[] event has ended!
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Access denied.
error-ip-changed = [scarlet]⚠ Your IP address has changed. Admin privileges have been revoked.
error-not-enough-params = [scarlet]⚠ Not enough position params.
error-player-not-found = [scarlet]Player not found.
error-player-not-teammate = [scarlet]⚠ Target player is not in your team.
error-player-admin = [scarlet]⚠ Don't try to kick an admin. ⚠
error-already-voted = [scarlet]⚠ You have already voted. Calm down.
error-playtime-requirement =
    [scarlet]⚠ You need to play for at least { $time } { $time ->
        [one] minute
       *[other] minutes
    } to use this feature.
error-globalchat-total-playtime =
    [scarlet]⚠ In order to send message to global chat you need to play for { $globalChatPlayTime } { $globalChatPlayTime ->
        [one] minute
       *[other] minutes
    }.
error-votekick-total-playtime =
    [scarlet]⚠ In order to start a vote-kick you need to play for { $votekickPlayTime } { $votekickPlayTime ->
        [one] minute
       *[other] minutes
    }.
error-vote-yourself = [scarlet]⚠ You cannot vote on your own vote session.
error-vote-in-progress = [scarlet]⚠ A vote session is already in progress.
error-no-voting = [scarlet]⚠ There is no vote session at the moment.
error-wave-vote-unavailable = [scarlet]⚠ Starting a new wave early is only available in wave-based modes.
error-no-map = [scarlet]⚠ Map not set.
error-map-not-event = [scarlet]⚠ Map is not part of the current event.
error-map-not-found = [scarlet]⚠ Map not found! [accent]Use [cyan]/maps[] to see a list of all available maps.
error-maps-empty = [scarlet]⚠ Map list is empty.
error-event-not-found = [scarlet]⚠ Event not found! [accent]Use [cyan]/events[] to see the list of available events.
error-page-between = [scarlet]⚠ 'page' must be a number between[orange] 1[] and [orange]{ $totalPages }[].
error-page-number = [scarlet]'page' must be a number.
error-wrong-number = [scarlet]⚠ Wrong number format.
error-wrong-period-format = [scarlet]⚠ Wrong period format- Example: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ Invalid player-id.
error-spectator = [scarlet]⚠ You are a spectator and cannot use this command.
error-admin-password-too-short = [scarlet]⚠ Admin password must be at least 4 characters long.
error-wrong-admin-password = [scarlet]⚠ Incorrect admin password.
error-internal = [scarlet]Internal error.
error-processing-request = [scarlet]An error occurred while processing the request.
error-team-not-found = [scarlet]⚠ Team not found.
error-no-access = [scarlet]⚠ No Access.
error-nickname-too-long = [scarlet]⚠ Nickname is too long. Max { $max } visible characters.
error-private-message-invalid-pid = [scarlet]⚠ Invalid private-message pid. Use format [lightgray]#123[].
error-private-message-self = [scarlet]⚠ You cannot send a private message to yourself.
error-private-message-empty = [scarlet]⚠ Message cannot be empty.
error-private-message-too-long = [scarlet]⚠ Message is too long. Max { $max } characters.
error-private-message-cooldown = [scarlet]⚠ Wait { DURATION($seconds) } before sending another private message.
error-private-message-target-unavailable = [scarlet]⚠ This player is unavailable for private messages right now.
error-private-message-no-reply-target = [scarlet]⚠ No recent private-message contact to reply to.
error-private-message-not-found = [scarlet]⚠ Message not found.
error-private-message-block-self = [scarlet]⚠ You cannot block yourself.
error-private-message-block-limit = [scarlet]⚠ Block list limit reached ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Ban duration
ban-menu-duration-message = Enter ban duration for { $nickname }. Example: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Ban reason
ban-menu-reason-message = Enter ban reason for { $nickname }. Leave empty for default reason.
ban-menu-confirm-title = [orange]{ -xcore } - Confirm ban
ban-menu-confirm-content =
    [white]Target: { $nickname }[]
    [white]Duration: [accent]{ $duration }[]
    [white]Reason: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Ban player
error-invalid-syntax = [scarlet]⚠ Invalid command syntax. Usage: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Invalid command sender. This command requires: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Invalid argument: '{ $error }'.
exception-unexpected = [scarlet]⚠ An internal error occurred while performing this command.
exception-invalid-argument = [scarlet]⚠ Invalid command argument: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Unknown command.
exception-no-permission = [scarlet]⚠ Access denied.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' cannot run this command. Required sender: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' cannot run this command. Allowed senders: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Invalid command syntax. Usage: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ Could not parse boolean from '{ $input }'.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' is not a valid number within range [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' is not a valid character.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' is not a valid option. Allowed: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Invalid string format for '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Invalid UUID format: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ Input '{ $input }' does not match pattern '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' is not a valid color.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' is not a valid duration format.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Missing component '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Invalid component '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Could not resolve { $primary } or { $fallback } from '{ $input }'.
argument-parse-failure-flag-unknown = [scarlet]⚠ Unknown flag: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Duplicate flag: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Duplicate flag: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ No flag started. Don't know what to do with '{ $input }'.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Missing argument for flag: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ You don't have permission to use flag '{ $flag }'.
# ==============================================================================
# Button Status
# ==============================================================================
finished = finished
finished-neutral = [orange]Finished
finished-active = [green]Finished
finished-inactive = [red]Finished
major = Major
major-neutral = [orange]Major
major-active = [green]Major
major-inactive = [red]Major
active = Active
active-neutral = [orange]Active
active-active = [green]Active
active-inactive = [red]Active
admin = Admin
admin-neutral = [orange]Admin
admin-active = [green]Admin
admin-inactive = [red]Admin
player-leaderboard-active = [green]Leaderboard: enabled[]
player-leaderboard-inactive = [red]Leaderboard: disabled[]
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = hours
days = days
success = [green]Successfully
empty = [accent]Empty
never = Never
save = Save
close = [scarlet]Close
previous = [accent]« Previous
next = [accent]Next »
cancel = Cancel
back = Back
yes = Yes
no = No
test = Test
no-description = No description
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Auto
on = On
off = Off
error-command-disabled = [scarlet]⚠ Command [accent]/{ $command }[scarlet] is disabled on this server.
error-feature-disabled = [scarlet]⚠ This feature is disabled on this server.
none = None
error-nickname-badge-glyph = [scarlet]⚠ Custom nickname cannot contain reserved badge icons.
