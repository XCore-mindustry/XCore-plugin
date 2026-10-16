# The report flow. Every message takes the same arguments in en, ru and uk.

# The panel button and its form (labels: 45 characters at most).
reports-panel = Report a player
reports-field-who = Player ID or exact nickname
reports-field-what = What happened?
reports-field-when = When (optional)
reports-field-where = Server or map (optional)

# The staff card.
reports-card-reported-as = Reported as
reports-card-unresolved = The player was not identified. Ban and Mute will ask for the player ID.
reports-card-reason = Report reason
reports-card-comment = Reporter's description
reports-card-when = When
reports-card-where = Where
reports-card-context = In game
reports-card-in-force = In force now
reports-card-reporters = Reporters: { $count }
reports-card-more = …and { $count } more
reports-online = The player is on the server
reports-offline = The player has left the server
reports-no-live = No chat or block history: the player was not on a server when the report was filed.
reports-same-network = Same network as the reporter
reports-current-ban = Ban until { $until }
reports-current-ban-permanent = Permanent ban
reports-current-mute = Mute until { $until }

# Reasons a player picks in the game.
reports-reason-griefing = Griefing
reports-reason-toxicity = Abuse in chat
reports-reason-cheating = Cheating
reports-reason-nsfw = Inappropriate content
reports-reason-evasion = Evading a sanction
reports-reason-other = Other

# Reasons staff pick when rejecting.
reports-reject-no-violation = No violation
reports-reject-no-evidence = Not enough evidence
reports-reject-already-handled = Already dealt with
reports-reject-duplicate = Duplicate
reports-reject-abuse = Abusive report

# What the reporter is told. Never who decided, never what the sanction was.
reports-outcome-action = Your report #{ $number } has been reviewed and action was taken. Thank you.
reports-outcome-no-violation = Your report #{ $number } has been reviewed: no rule violation was found.
reports-outcome-no-evidence = Your report #{ $number } has been reviewed: there was not enough evidence to act on it.
reports-outcome-already-handled = Your report #{ $number } has been reviewed: this had already been dealt with.
reports-outcome-duplicate = Your report #{ $number } is closed: it repeats an earlier report.
reports-outcome-abuse = Your report #{ $number } was rejected: the report itself breaks the rules.
reports-outcome-closed = Your report #{ $number } has been reviewed and closed without action.

# Ban, Mute, History and Refresh.
reports-form-pid = Player ID
reports-form-length = Duration (30m, 12h, 7d)
reports-case-reference = case #{ $number }
reports-confirm-ban = Ban { $player } for { $duration }?
reports-confirm-mute = Mute { $player } for { $duration }?
reports-error-other-player = This ID belongs to another player, not the one this report is about.
reports-history-title = Latest records for { $player }:
reports-history-empty = There are no records for this player.
reports-refreshed = Chat, block history and sanctions have been updated.
reports-refreshed-offline = The player is not on a server: only the sanctions were updated.

# Lines for staff in the case post and the forum.
reports-merged = +1 report ({ $total } in total) from { $reporter }: { $reason }
reports-external-banned = The player was banned outside this case ({ $name }). The report is closed as handled.
reports-external-muted = The player was muted outside this case ({ $name }). The report is closed as handled.
reports-burst-title = Report intake is limited
reports-burst-alert = Limit reached: { $limit } report cases were opened from the game in { $minutes } min. Until the rate drops, new in-game reports are refused; a report on a player who already has an open case still joins it.
