commands-tl-description = [#a4b8ff]TileLogger info and tools[]
commands-tl-memory-description = Show TileLogger memory usage
commands-tl-select-description = Select rectangular area for rollback/fill
commands-tl-subnet-description = Check subnet allow/deny
commands-tl-subnet-subnet-description = IPv4 subnet (e.g. 1.2.3.4/24)

commands-tl-fill-description = Fill selected area with block
commands-tl-fill-block-description = Block name

commands-tl-file-path-description = History file name/path

commands-history-description = Show tile/player history
commands-history-size-description = Number of entries to show
commands-history-target-description = Player name/uuid/id
commands-history-x-description = Tile X coordinate
commands-history-y-description = Tile Y coordinate

commands-rollback-description = Rollback blocks
commands-rollback-target-description = Player name/uuid/id
commands-rollback-time-description = Time period (e.g. 10m, 2h, 1d)
commands-rollback-selection-description = Use selected area

tilelogger-info =
    [#a4b8ff]TileLogger[]
        [#b0b5c8]Author:[] [#ffd37f] (Gorynych)[]
        [#b0b5c8]Build:[]  [#a4b8ff]{$build}[]

tilelogger-memory =
    [#a4b8ff]▬▬▬ Memory Usage (MB) ▬▬▬[]
        [#b0b5c8]JVM:[]     [#ffd37f]{$jvmUsed}[] [#6e7080]/[] [#b0b5c8]{$jvmMax}[]
        [#b0b5c8]History:[] [#ffd37f]{$historyUsed}[] [#6e7080]/[] [#b0b5c8]{$historyCap}[]
        [#b0b5c8]Players:[] [#ffd37f]{$playersUsed}[] [#6e7080]/[] [#b0b5c8]{$playersCap}[]
        [#b0b5c8]Configs:[] [#ffd37f]{$configsUsed}[] [#6e7080]/[] [#b0b5c8]{$configsCap}[]

tilelogger-select-start = [#a4b8ff] Selection Mode:[] [#b0b5c8]Tap top-left corner.[]
tilelogger-select-pos1 = [#a4b8ff] Position 1 set.[] [#b0b5c8]Tap bottom-right corner.[]
tilelogger-select-done = [#98ff98]✔ Area selected.[] [#b0b5c8]Tiles:[] [#ffd37f]{$area}[] { $area ->
    [one] tile
   *[other] tiles
}

tilelogger-subnet-accept = [#98ff98]✔ ACCEPTED[] [#b0b5c8]Subnet:[] [#ffd37f]{$subnet}[]
tilelogger-subnet-deny = [#ff8a8a]✖ DENIED[] [#b0b5c8]Subnet:[] [#ffd37f]{$subnet}[]

tilelogger-fill-success = [#98ff98]✔ Area filled[] [#b0b5c8]with[] {$emoji} {$block}

tilelogger-history-player = [#a4b8ff]📜 History:[] [#ffd37f]{$player}[] [#6e7080]|[] [#b0b5c8]{$time}[]
tilelogger-history-tile = [#a4b8ff]📜 Tile History:[] [#ffd37f]({$x}, {$y})[] [#6e7080]|[] [#b0b5c8]{$time}[]


tilelogger-server = Server

commands-rollback-preview-description = Show a read-only rollback plan
commands-history-success = History inspection size: { $size } (0 = off).
tilelogger-error-limit = [scarlet]History size must be between 1 and { $max }.[]
tilelogger-error-coordinates = [scarlet]Coordinates are outside the current map.[]
tilelogger-error-selection = [scarlet]Select both corners on this map with /tl select first.[]
tilelogger-error-duration = [scarlet]Duration is outside the supported range.[]
tilelogger-error-session = [scarlet]The map/session changed or recording stopped. Start a live session and retry.[]
tilelogger-error-storage = [scarlet]TileLogger storage is unavailable: { $reason }[]
tilelogger-error-archive = [scarlet]Cannot open archive. The active history was preserved; see the server log.[]
tilelogger-error-lookup = [scarlet]Player lookup failed. Try again later.[]
tilelogger-error-mutations = [scarlet]At most { $max } buildings per operation. Select a smaller area.[]
tilelogger-error-protected = [scarlet]This operation would affect a protected core or construction block.[]
tilelogger-error-team = [scarlet]Specify a destination team.[]
tilelogger-error-footprint = [scarlet]The complete building footprint must fit the selection without affecting other buildings.[]
tilelogger-error-mode = [scarlet]Use r to open a read-only archive, or w to create a new live journal.[]
tilelogger-archive-open = [accent]Archive opened: { $status }. World changes are disabled.[]
tilelogger-session-started = [accent]New live history started and the map baseline recorded.[]
tilelogger-read-only = [gray]Read-only archive. Legacy archives have an ambiguous 16-bit clock.[]
tilelogger-rollback-preview = [accent]Preview: { $count } applicable; { $protected } protected; { $conflicts } conflicts; { $unsupported } unrestorable. No changes made.[]
tilelogger-rollback-broadcast = [accent]{ $caller } rolled back { $target }: { $count } applied; { $protected } protected; { $conflicts } conflicts; { $unsupported } unrestorable; { $failed } failed.[]
commands-tl-file-description = Open a read-only archive or start a new live history (server only)
commands-tl-file-mode-description = r = read-only archive; w = new live journal (existing files are never replaced)

tilelogger-action-baseline = Map baseline
tilelogger-action-build-started = Build started
tilelogger-action-build-completed = Build completed
tilelogger-action-destroy-started = Destroy started
tilelogger-action-destroy-completed = Destroy completed
tilelogger-action-rotate = Rotate
tilelogger-action-configure = Configure
tilelogger-action-pickup = Pickup
tilelogger-action-drop = Drop
tilelogger-action-admin-fill = Admin fill
tilelogger-action-legacy = Legacy snapshot
tilelogger-error-plan = [scarlet]The rollback area or configuration volume is too large. Select a smaller area.[]
tilelogger-error-payload = [scarlet]This rollback crosses a pickup or payload drop. Select an area or time range without payload moves.[]
