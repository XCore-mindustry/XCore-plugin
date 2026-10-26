## Rating seasons as /season info, /season top and /stats show them. Ids start with "season-",
## the leagues with "league-": the names the game gives them.

season-default-name = Season { $number }
season-none = No rating seasons yet.
season-not-found = No such season for { $ladder }.
season-started = Started
season-ends = Ends
season-ended = Ended
season-wrapping-up = Wrapping up…
season-players = Players
season-count = `{ NUMBER($count, useGrouping: 1) }`
season-matches = Matches
season-prizes = Prizes
season-prize-badge = 🎖️ { $badge } badge
season-top-title = { $season } · Top
season-top-empty = Nobody has played this season yet.
season-top-wins = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } wins
season-top-footer = { $players ->
        [one] { NUMBER($players, useGrouping: 1) } player this season
       *[other] { NUMBER($players, useGrouping: 1) } players this season
    }

# A player's line in /stats

season-no-rated-matches = No rated matches this season
season-ends-in = ends { $when }
season-wins = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } wins
season-wins-rate = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } wins ({ $rate }%)
season-standing = { $league } `{ $rating }` · #{ $rank } of { NUMBER($participants, useGrouping: 1) } · { $games }
season-peak = peak `{ $rating }`
season-top-league = top league
season-next-league = `{ $left }` to { $league }

league-scrap = Scrap
league-copper = Copper
league-lead = Lead
league-graphite = Graphite
league-silicon = Silicon
league-titanium = Titanium
league-thorium = Thorium
league-plastanium = Plastanium
league-phase-fabric = Phase Fabric
league-surge-alloy = Surge Alloy
