commands-tl-description = [#a4b8ff]TileLogger info and tools[]
commands-tl-memory-description = Show TileLogger memory usage
commands-tl-select-description = Select rectangular area for rollback/fill
commands-tl-subnet-description = Check subnet allow/deny
commands-tl-subnet-subnet-description = IPv4 subnet (e.g. 1.2.3.4/24)

commands-tl-fill-description = Fill selected area with block
commands-tl-fill-block-description = Block name

commands-tl-file-description = Reset history using custom file (server only)
commands-tl-file-path-description = History file name/path
commands-tl-file-mode-description = Mode: w = write to file

commands-history-description = Show tile/player history
commands-history-size-description = Number of entries to show
commands-history-target-description = Player name/uuid/id
commands-history-x-description = Tile X coordinate
commands-history-y-description = Tile Y coordinate

commands-rollback-description = Rollback blocks
commands-rollback-target-description = Player name/uuid/id
commands-rollback-time-description = Time period (e.g. 10m, 2h, 1d)
commands-rollback-selection-description = Use selected area

tilelogger-info = {""}
    {""}[#a4b8ff]TileLogger[]
        {""}[#b0b5c8]Author:[] [#ffd37f] (Gorynych)[]
        {""}[#b0b5c8]Build:[]  [#a4b8ff]{$build}[]

tilelogger-memory = {""}
    {""}[#a4b8ff]▬▬▬ Memory Usage (MB) ▬▬▬[]
        {""}[#b0b5c8]JVM:[]     [#ffd37f]{$jvmUsed}[] [#6e7080]/[] [#b0b5c8]{$jvmMax}[]
        {""}[#b0b5c8]History:[] [#ffd37f]{$historyUsed}[] [#6e7080]/[] [#b0b5c8]{$historyCap}[]
        {""}[#b0b5c8]Players:[] [#ffd37f]{$playersUsed}[] [#6e7080]/[] [#b0b5c8]{$playersCap}[]
        {""}[#b0b5c8]Configs:[] [#ffd37f]{$configsUsed}[] [#6e7080]/[] [#b0b5c8]{$configsCap}[]

tilelogger-select-start = [#a4b8ff] Selection Mode:[] [#b0b5c8]Tap top-left corner.[]
tilelogger-select-pos1 = [#a4b8ff] Position 1 set.[] [#b0b5c8]Tap bottom-right corner.[]
tilelogger-select-done = [#98ff98]✔ Area selected.[] [#b0b5c8]Tiles:[] [#ffd37f]{$area}[]

tilelogger-subnet-accept = [#98ff98]✔ ACCEPTED[] [#b0b5c8]Subnet:[] [#ffd37f]{$subnet}[]
tilelogger-subnet-deny = [#ff8a8a]✖ DENIED[] [#b0b5c8]Subnet:[] [#ffd37f]{$subnet}[]

tilelogger-fill-success = [#98ff98]✔ Area filled[] [#b0b5c8]with[] {$block}

tilelogger-history-player = [#a4b8ff]📜 History:[] [#ffd37f]{$player}[] [#6e7080]|[] [#b0b5c8]{$time}[]
tilelogger-history-tile = [#a4b8ff]📜 Tile History:[] [#ffd37f]({$x}, {$y})[] [#6e7080]|[] [#b0b5c8]{$time}[]

tilelogger-rollback-broadcast = [#a4b8ff]Rollback:[] [#ffd37f]{$caller}[] [#b0b5c8]reverted actions of[] [#ffd37f]{$target}[]
    {""}[#b0b5c8]Affected tiles:[] [#ff8a8a]{$count}[]

tilelogger-server = Server
