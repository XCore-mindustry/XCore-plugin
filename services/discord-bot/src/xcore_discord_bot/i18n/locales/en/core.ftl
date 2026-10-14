## Shared strings of the cases core. A flow adds its own file (reports.ftl, appeals.ftl, ...) and
## prefixes its ids with its domain: ids are global within a locale.
## Number arguments are printed without digit grouping (1234, not 1,234).

# Errors and refusals

error-generic = Something went wrong. Please try again later.
error-try-again-later = This is not available right now. Please try again in a moment.
error-not-allowed = You do not have permission to do this.
error-not-found = Nothing was found.
error-already-handled = This has already been handled.
error-already-handled-by = This has already been handled by { $name }.
error-opening-blocked = You cannot open new requests at the moment.
error-cooldown =
    Please wait { $seconds ->
        [one] { $seconds } more second
       *[other] { $seconds } more seconds
    } before trying again.

# Buttons

button-yes = Yes
button-no = No
button-cancel = Cancel
button-confirm = Confirm

# Language switch

language-name-en = English
language-name-ru = Русский
language-name-uk = Українська
language-select-prompt = Choose the language the bot uses when it replies to you and sends you messages.
language-select-placeholder = Choose a language
language-changed = Language changed to { $language }.
cmd-language-name = language
cmd-language-description = Choose the language the bot uses with you

# Cases

cases-open-limit = You already have the maximum number of open requests of this type.
cases-action-failed = The action could not be completed. Check the case and try again.
cases-form-invalid = The form is incomplete or contains an invalid value.
cases-press-again = The form could not be opened. Please press the button again.
cases-cancelled = Cancelled.
cases-choice-placeholder = Choose an option
cases-unknown = Unknown
cases-pid = ID { $pid }
cases-opener = Opened by
cases-subject = Concerns
cases-status = Status
cases-assignee = Assigned to
cases-resolution = Outcome
cases-reason = Reason
cases-reject-details = Details
cases-note = Note
cases-files = Screenshots or files (optional)
cases-files-retry = Some files could not be attached. Please add them in your request thread.
cases-sanctions = Sanction history
cases-sanctions-counts = Bans: { $bans } · mutes: { $mutes } · kicks: { $kicks } · warnings: { $warns }
cases-block-summary = Blocks in the last { $minutes } min: placed or changed { $built } (includes rotations and setting changes), removed { $destroyed }.
cases-user-thread-note = Your request is registered as #{ $number }. Staff will review it and reply here. You can add screenshots or more details in this thread.
cases-user-message = New message from { $name }: { $url }
cases-registered = Your request is registered as #{ $number }.
cases-updated = Case #{ $number } updated.
cases-found = Case #{ $number } · { $kind } · { $status }
cases-outcome = Your request #{ $number } has been reviewed.

# The public panel

cases-panel-text = Choose what you need with the buttons below. Staff will review your request in a private thread. Press 🌐 to choose the language of replies.
cases-panel-language = Language
cases-panel-empty = No request type with a panel button is enabled.
cases-panel-published = Cases panel updated: { $url }
cases-configuration-error = Cases are not fully configured: { $details }

# Kinds, statuses and outcomes shown on a card

cases-kind-report = Report
cases-kind-appeal = Ban appeal
cases-kind-badge = Badge request
cases-kind-complaint = Staff complaint
cases-kind-map = Map review
cases-status-creating = Registered
cases-status-open = Open
cases-status-taken = In progress
cases-status-pending = Action in progress
cases-status-resolved = Resolved
cases-status-rejected = Rejected
cases-status-cancelled = Cancelled
cases-resolution-banned = Player banned
cases-resolution-muted = Player muted
cases-resolution-unbanned = Ban lifted
cases-resolution-shortened = Ban shortened
cases-resolution-granted = Badge granted
cases-resolution-upheld = Complaint upheld
cases-resolution-accepted = Map accepted
cases-resolution-rejected = Rejected

# Card buttons. Take, Release and Reject belong to the core; the rest are shared labels for flows.

cases-button-take = Take
cases-button-release = Release
cases-button-reject = Reject
cases-button-history = History
cases-button-refresh = Refresh
cases-button-ban = Ban
cases-button-mute = Mute
cases-button-unban = Unban
cases-button-shorten = Shorten ban
cases-button-grant = Grant badge
cases-button-accept = Accept map
cases-button-resolve = Resolve
cases-button-recheck = Re-check
cases-button-subject = Set subject

# Why a staff action failed

cases-error-player = The player could not be identified. Check the player ID.
cases-error-duration = The duration is not valid.
cases-error-ban = This ban is no longer active.
cases-error-expiry = The new expiry must be earlier than the current one.
cases-error-badge = This badge cannot be granted by hand.
cases-error-map = Check the map file and the selected server.
cases-error-map-unconfirmed = The server did not confirm the upload. Check its map list before trying again.
cases-error-no-server = No game server is available right now. Try again later.
cases-error-timeout = The server did not answer in time. Check whether the action went through before trying again.
cases-error-server = The server could not complete the action. Check whether it went through before trying again.

# Slash commands

cmd-cases-name = cases
cmd-cases-description = Find and manage cases
cmd-cases-panel-name = panel
cmd-cases-panel-description = Public cases panel
cmd-cases-panel-publish-name = publish
cmd-cases-panel-publish-description = Publish or update the public cases panel
cmd-cases-find-name = find
cmd-cases-find-description = Find a case by its number
cmd-cases-find-param-number-name = number
cmd-cases-find-param-number-description = Case number, for example #57
cmd-cases-reopen-name = reopen
cmd-cases-reopen-description = Reopen a resolved or rejected case
cmd-cases-reopen-param-number-name = number
cmd-cases-reopen-param-number-description = Case number, for example #57
cmd-cases-subject-name = subject
cmd-cases-subject-description = Set the staff member a complaint is about
cmd-cases-subject-param-number-name = number
cmd-cases-subject-param-number-description = Case number, for example #57
cmd-cases-subject-param-user-name = user
cmd-cases-subject-param-user-description = The staff member the complaint is about
