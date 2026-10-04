# Technical Design Specification: Seasonal Rating (Mini-PVP and HexedCore)

Status: decisions in section 10 accepted; phases 1–5 implemented (see 9.1–9.5).
Affects: `XCore-plugin`, `aethercore-plugin` (HexedCore), `xcore-protocol`, `XCore-discord-bot`.

## 1. Goal

A seasonal rating for the two competitive modes, Mini-PVP and HexedCore:

- fixed-length seasons (3 months by default, configurable);
- an archive of past seasons in `/top`;
- seasonal statistics and history in `/stats`;
- season results published to a dedicated Discord channel: winners (nickname, pid, uuid, linked Discord account);
- "season ending soon" notifications in game and in Discord;
- head administrators can move the season end and assign prizes.

Architectural requirement: season logic is written once and knows nothing about game modes.

## 2. What the current codebase gets in the way of

| Problem | Where |
|---|---|
| Mini-PVP stores its rating as fields on `players` (`pvp_rating`, `pvp_matches`, `pvp_wins`), written asynchronously with no idempotency | `XCore-plugin/.../gamemode/pvp/rating/MiniPvPRatingSettler.java` |
| HexedCore stores its rating in a separate collection through `PluginPlayerStore` with a ledger and `applyOnce` — a different scheme | `aethercore-plugin/.../hexedcore/rating/HexedCoreRatingStore.java` |
| HexedCore carries copies of XCore's `RatingPolicy` and `PlacementEloCalculator`; `HexedLeague` is a wrapper around `RatingLeague` | `aethercore-plugin/.../hexedcore/rating/` |
| The HexedCore leaderboard falls back to a rating of `1200`, while the policy default is `1000` | `HexedCoreTopProvider.java:84` |
| `rankLabel` and `parseLongSafe` are duplicated across two leaderboard providers | `BuiltInTopCategoryProvider.java`, `HexedCoreTopProvider.java` |
| `/top` has no "season" dimension; the `MINI_PVP`/`HEXED` categories are hard-coded in a `switch` | `TopUiController.java:234`, `TopCategory.java`, `PlayerDataRepository.java` |
| `/stats` hard-codes the PvP fields in the profile model and has no extension point; HexedCore Elo is not shown in the profile | `PlayerProfileUiController.java` |
| Account merge is unaware of the HexedCore rating; the bot has a second, Python implementation of the merge | `AccountMergeService.java`, `mongo_store.py:796` |
| HexedCore already has a `season_id` field, but it is always `"default"` and never used | `HexedCoreRatingStore.java` |
| The badge catalogue is duplicated in Java and Python | `player/Badge.java`, `badges.py` |

Conclusion: refactor first (phase 1), then build seasons.

## 3. Architecture

Everything shared lives in `org.xcore.plugin.rating` in XCore-plugin; HexedCore already depends on it as an API.

```
org.xcore.plugin.rating
├── math/      EloMath, PlacementEloCalculator, TeamEloCalculator   (exists)
├── RatingLeague, RatingPolicy                                       (exists)
├── ladder/                                                          (phase 1, done)
│   ├── LadderDefinition       id, display-name key, RatingPolicy
│   ├── LadderService          mode registration (like TopCategoryRegistry), account merge
│   ├── Ladder                 runtime handle of one ladder: standings cache, settle(), top, rank
│   ├── LadderStore            rating_standings (MongoLadderStore, InMemoryLadderStore)
│   ├── LadderStanding         a player's row in one season of one ladder
│   ├── StandingMutation       uuid, rating delta, win, counter increments
│   ├── MatchSettlement        a finished match: mutations, or the reason it is unrated
│   └── LadderLeagueDisplay    the league icon next to a player's name
│   ├── StandingSeed           the rating a new standing starts from, and where it came from
│   └── LadderSeasons          SPI: everything the ladder needs to know about seasons
├── view/                                                            (phase 3, done)
│   ├── LadderViews            @Singleton factory + blocking loaders; what a mode registers
│   ├── LadderTopCategoryProvider   the /top category of any ladder, one scope per season
│   ├── LadderProfileSection   the /stats block of any ladder (ProfileSectionProvider)
│   ├── LadderProgress, SeasonOverview, SeasonCard   data read for, and words written for, one player
│   └── SeasonText, LadderProgressText   season titles, dates, time left; the lines of a progress block
├── season/                                                          (phase 2, done)
│   ├── Season, SeasonStatus   one season of one ladder; transitions are pure functions
│   ├── SeasonStore            rating_seasons (MongoSeasonStore, InMemorySeasonStore)
│   ├── SeasonSchedule         length, time zone, notification thresholds, grace, podium
│   ├── SeasonResetPolicy      starting rating for a new season
│   ├── SeasonResolver         this server's view of the open seasons; diffs produce SeasonChange
│   ├── SeasonLifecycleService tick: notifications, closing, opening; implements LadderSeasons
│   ├── SeasonFinalizer        final ranks, podium and summary of a closing season
│   ├── SeasonObserver         per-server reaction to a SeasonChange
│   └── SeasonAnnouncer        in-game announcements and icon refresh
└── prize/
    ├── SeasonPrize, PrizeGrant, PrizeGrantRepository
    └── PrizeHandler           SPI per prize kind
```

### 3.1. What stays in the modes

- **Mini-PVP**: `MiniPvPMatchTracker` plus the conversion of teams into `StandingMutation`s through `TeamEloCalculator`; the `minipvp` ladder definition (`MiniPvPLadder`).
- **HexedCore**: `HexedRoundResult`, the penalty exemption rules (`isExempt`), the conversion into `StandingMutation`s through `PlacementEloCalculator`; the `hexed` ladder definition (`HexedLadder`).
- Both: the match-history record and the wording of player notifications, because their content is mode-specific.

Removed: `hexedcore.rating.RatingPolicy`, `hexedcore.rating.PlacementEloCalculator`, `HexedLeague`, `HexedCoreRatingStore`, `HexedCoreTopProvider`, and the `MINI_PVP` branch of `BuiltInTopCategoryProvider`.

### 3.2. The single match-settlement path

`Ladder.settle(MatchSettlement)`:

1. The season is resolved from the match end time (`MatchSettlement.endedAt`) through `LadderSeasons.at`. An overdue season is rolled over right there, so a match is never written into a season that should already have ended.
2. `PluginIdempotencyLedger.claim` on the operation `{ladder}:{matchId}:rating:{algorithm}` in the shared `rating` ledger. An unrated match is claimed and marked skipped, so it is also seen exactly once.
3. For each participant, `LadderStore.applyOnce` into `rating_standings` (the record is created with a lazy reset, see 5.2). The store applies a rating *delta* and clamps to the policy minimum, so it never overwrites a concurrent change.
4. `ledger.markCompleted`.

The result tells the mode whether it `claimed` the match (it then records history) and whether standings were `applied` (it then notifies players and refreshes icons). A settlement interrupted between two players is finished by a retry: the ledger lease is taken over and `applyOnce` skips the players already updated.

Mini-PVP gains the idempotency it lacked, and its settlement now runs off the game thread.

### 3.3. Three key decisions

- **The archive is free.** A past season is the same `rating_standings` documents that are no longer written to. A past season's leaderboard is the same query with a different `season`. There is no separate archive code or archive collection.
- **The reset is lazy.** Nothing is rewritten in bulk when a season rolls over. A player's record in the new season is created on their first match, from their latest rating, according to `SeasonResetPolicy`.
- **Closing happens exactly once.** The tick runs on every XCore server over all seasons in `rating_seasons`, regardless of which mode that server hosts. Every transition is a compare-and-set on the season's `revision`, so exactly one server wins it; finalisation additionally runs under a ledger lease. The season closes even if the server hosting that mode is down.

### 3.4. The seam between ladders and seasons

The dependency is one-way: `rating.season` knows `rating.ladder`, not the reverse. The ladder asks its questions through `LadderSeasons` — which season is current, which season a match that ended at a given instant belongs to, what rating a new standing starts from — and `SeasonLifecycleService` answers them. `LadderSeasons.single()` is the season-less implementation (always season 1, no reset) used by tests.

A ladder caches standings for one season. When its season changes, the lifecycle service calls the ladder back and the cache is rebuilt for the new season.

## 4. Data (MongoDB)

### 4.1. `rating_seasons`

```
_id            "{ladder}:{number}"
ladder         "minipvp" | "hexed"
number         1, 2, 3...
name           optional display name
starts_at      date
ends_at        date (can be moved)
status         ACTIVE | CLOSING | ARCHIVED
sent_notices   ["7d", "3d", "24h", "1h"]   — thresholds already sent
prizes         [{place_from, place_to, kind, value, description}]   — kind is BADGE | CUSTOM
podium         [{place, uuid, pid, nickname, rating, league, matches, wins,
                 discord_id, discord_username}]   — snapshot taken at close
summary        {participants, matches}
matches        live counter of rated matches settled in the season
rescheduled    [{from, to, actor, at, reason}]
revision       for optimistic locking
created_at, updated_at
```

Indexes: unique `{ladder, number: -1}`; `{status}` for the tick, which reads only the open (`ACTIVE`/`CLOSING`) seasons.

`matches` is incremented with `$inc` outside the `revision` compare-and-set, so counting a match never makes a concurrent transition fail. It becomes `summary.matches` when the season is archived.

### 4.2. `rating_standings`

```
ladder, season, player_uuid          — unique key
rating, peak_rating
matches, wins
stats                {top3, disconnects, ...}   — additive counters only
seeded_from          {season, rating}   — where the starting rating came from (phase 2)
applied_operations   [operationId]      — last 64
final_rank           filled in when the season closes (phase 2)
created_at, updated_at
```

`stats` holds only counters that grow by a non-negative increment per match. That keeps the engine free of per-ladder knowledge: an account merge sums them without knowing what they mean. HexedCore's `best_placement` and `last_placement` did not fit and were dropped — nothing read them.

Indexes: unique `{ladder, season, player_uuid}`; `{ladder, season, rating: -1, player_uuid: 1}` for the leaderboard and rank lookups; `{player_uuid}` for account merges.

### 4.3. `rating_prize_grants`

```
_id          "{season_id}:{place}:{player_uuid}:{prize_index}"
season_id, place, player_uuid
prize        {kind, value, description}   — copied from the season, so later edits never change what a finished podium was promised
status       PENDING | GRANTED | DELIVERED | FAILED
granted_by, updated_at, note
```

`PENDING` — created, waiting (an automatic prize awaiting its handler, or a custom one awaiting a person); `GRANTED` — given by the plugin; `DELIVERED` — a person confirmed the hand-over; `FAILED` — the handler could not give it, kept for a person. `granted_by` is `system` or an actor key such as `discord_user:222`.

### 4.4. Migration V5

- `players.pvp_rating/pvp_matches/pvp_wins` → `rating_standings {ladder: "minipvp", season: 1}`;
- `xcore_plugin_hexedcore_rating_players` → `rating_standings {ladder: "hexed", season: 1}`;
- only players with at least one rated match are copied; `merged:` accounts are skipped;
- existing standings are never overwritten (`$merge … whenMatched: keepExisting`), so the migration is safe to re-run;
- the old fields and collection are left untouched until the next release (rollback), then removed by a separate migration;
- there is no migration for `rating_seasons`: season 1 of a ladder is created the first time a server registers that ladder (`LadderSeasons.open`, an idempotent insert), with `starts_at` = now and `ends_at` from the schedule. A ladder added later gets its season the same way.

Until phases 3 and 4, Mini-PVP standings are mirrored back into `players.pvp_rating/pvp_matches/pvp_wins` after every settlement (`LegacyPvpRatingMirror`): the profile menu and the Discord bot still read them. The ladder is the source of truth.

## 5. Season lifecycle

```
ACTIVE ──(now >= ends_at)──> CLOSING ──(now >= ends_at + grace)──> ARCHIVED
                                │                                        │
                                └── season N+1 is created as ACTIVE ─────┘
```

### 5.1. Closing

1. `ACTIVE → CLOSING` atomically (an update filtered by `_id` and `revision`).
2. Season N+1 is created immediately (`starts_at` = season N's `ends_at`) so that new matches land in it.
3. A settlement grace window (5 minutes by default) for matches that finished before `ends_at` but have not been settled yet.
4. Finalisation under the ledger operation `season:{id}:finalize`: set `final_rank`, build `podium` (top N, 10 by default) with a snapshot of nickname, pid and Discord account, compute `summary`.
5. Create `rating_prize_grants` from `prizes` (one per podium place and prize that covers it) and run `PrizeHandler` for the automatic kinds. This happens before step 6, so a crash in between is retried; creating a grant twice and delivering a non-pending grant are both no-ops.
6. `CLOSING → ARCHIVED`, emit `RatingSeasonEndedV1` (phase 4).

The season end is aligned to midnight in the configured time zone: `ends_at` = `starts_at` + length, truncated to the start of that day. If a season is closed so late that its successor's default end is already in the past, the successor runs a full length from now instead.

A server that crashes between steps 1 and 2 leaves a `CLOSING` season with no successor; the next tick on any server creates it. Steps 4–6 are retried the same way.

A match that ended before `ends_at` but is settled during the grace window is written into the `CLOSING` season. One settled after the season is archived goes into the current season: the final ranks are already fixed.

A match belongs to a season by its end time. A match started in season N and finished after `ends_at` counts towards season N+1.

Podium eligibility: a minimum number of matches in the season (10 by default), so that first place cannot be taken by a player with one match and a carried-over rating.

### 5.2. Lazy reset

On a player's first match in season N, their latest record in that ladder is looked up:

- no record → `policy.defaultRating()`;
- a record exists → `SeasonResetPolicy.seed(previousRating)`.

Policy options: soft reset `default + (prev − default) × carry` (`carry = 0.5` by default), hard reset, no reset. Match and win counters always start from zero.

The standing records where its starting rating came from in `seeded_from {season, rating}`, written only when the document is created.

A player who has not played in the new season yet has no standing in it: they are absent from the season's `/top` and are drawn with the starting league icon. Showing their carried-over rating as "uncalibrated" belongs to the profile work in phase 3.

### 5.3. Notifications

Thresholds come from config (`7d`, `3d`, `24h`, `1h` by default).

- **Cluster-wide, exactly once**: the server whose compare-and-set adds the threshold to `sent_notices` claims it. That is where the Discord event is published in phase 4.
- **In game**: every server compares consecutive snapshots of the open seasons (`SeasonResolver`) and reacts to what changed — a new threshold in `sent_notices`, or a new season number. The announcement is made on the server whose mode follows the ladder (`SeasonAnnouncer.follow`), so the Mini-PVP season is not announced on a survival server. A player who joins when less than the largest threshold remains gets the same line.
- If several thresholds are due at once (a server was down, or the end was moved closer), they are claimed together and announced once.

A server that sees a ladder for the first time announces nothing: a restart does not replay old notifications.

### 5.4. Moving the season end

`ends_at` is changed atomically by `revision`. Rules:

- a date in the past is rejected — ending early is a separate `end-now` command;
- a season in `CLOSING`/`ARCHIVED` cannot be changed;
- thresholds that are in the future again are removed from `sent_notices`;
- an entry is appended to `rescheduled`, an audit record is written (`NOTE`, target `season:{id}`, with the old and new end), and `RatingSeasonRescheduledV1` is emitted (phase 4);
- `end-now` sets `ends_at` to the current instant; the next tick closes the season as usual.

## 6. In-game interface

Season-aware presentation lives in `rating.view`, which depends on both `ladder` and `season`; the dependency between those two stays one-way (`season → ladder`). A mode builds its views from `LadderViews` and registers them where it wants them.

### 6.1. `/top` — done

SPI extension:

- `TopCategoryProvider.scopes()` (blocking, newest first, empty = no switcher) → `TopScope(id, current, attributes)`; `formatScope(scope, local)` words a scope. A category without scopes (`PLAYTIME`, the legacy `HEXED`) behaves as before;
- `LeaderboardPageRequest.scopeId` (nullable = the category's current scope);
- `TopCategoryProvider.formatValue(String, Localization)` words a bare value, so the viewer's own value comes from `LeaderboardPage.selfPrimaryValue` and the `switch` on category id is gone from `TopUiController`.

One shared `LadderTopCategoryProvider` serves both modes: a scope is a season number, a past season is the same leaderboard read with another season number (an unknown or future number falls back to the running season). The menu shows a "◀ Season 3 ▶" switcher under the category tabs once a ladder has more than one season.

The page is read off the game thread: `TopUiController` starts the load with `Async.supply`, keeps showing the old page meanwhile, and dispatches `TopEvent.Loaded` back; a result for a dialog that has moved on is dropped. Turning a page patches the list, switching category or season redraws the dialog.

Prizes next to a placement are not shown in game (see the gaps in 9.5).

### 6.2. `/stats` — done

A `ProfileSectionProvider` SPI (`integration.profile`, registered through `ProfileSectionRegistry` like `PlayerDisplayRegistry`) and a shared `LadderProfileSection`:

- current season: rating, league, progress to the next league, rank, matches / wins / win rate, peak;
- time left until the season ends (or "results are being tallied" while it closes);
- history of the last three seasons: "Season 2 — #4, Titanium, 1642 ELO", from `final_rank` stored on the standing;
- a mode may append its own facts with `withDetail` (Mini-PVP shows the legacy pre-season rating).

Sections are read together with the match statistics, off the game thread (`PlayerMenu.loadDetails`, shared by opening a profile and inspecting another player). Only the PvP block was extracted from `PlayerProfileUiController`; the rest of the profile is untouched. A player who never played a ladder sees its section only on a server that hosts the mode (`showUnplayed`).

### 6.3. `/season` command — done

For players: opens a dialog with a card per ladder this server hosts (every registered ladder on a server that hosts none) — the season and its end date, time left, number of players, the viewer's own league / rating / rank / progress, the previous season's top three — and a button that opens `/top` on that ladder. The card does not list prizes (see 9.5).

## 7. Discord

### 7.1. Protocol (`xcore-protocol`, new `rating` family, 0.8.0)

Phase 4 ships the season events and the two RPCs below marked *(phase 4)*; phase 5 adds the prize messages (protocol 0.9.0): `prizes` on `RatingSeasonEndingSoonV1` and on each podium entry, and the two prize RPCs. Field names follow the shared types `SeasonRefV1` (ladder, season, name, startsAt, endsAt), `SeasonPodiumEntryV1` and `SeasonSummaryV1`.

Events (`xcore:evt:rating:*`, replayable):

| Message | Content |
|---|---|
| `RatingSeasonStartedV1` | ladder, season, startsAt, endsAt |
| `RatingSeasonEndingSoonV1` | ladder, season, endsAt, threshold |
| `RatingSeasonEndedV1` | ladder, season, podium[] (`PlayerRefV1`, `DiscordIdentityRefV1?`, rating, league, matches, wins, prize?), summary |
| `RatingSeasonRescheduledV1` | ladder, season, oldEndsAt, newEndsAt, actor, reason |

RPC (bot → any live server):

| Request | Purpose |
|---|---|
| `RatingSeasonRescheduleRequestV1` *(phase 4)* | extend / set a date / end now |
| `RatingAccountsMergeRequestV1` *(phase 4)* | move one account's standings to another after a bot-side account merge |
| `RatingSeasonPrizesSetRequestV1` *(phase 5)* | `add` one prize or `remove` the prizes within a range of places; the answer is the season's resulting prize list. Operations instead of a whole list, so two admins cannot overwrite each other |
| `RatingPrizeGrantUpdateRequestV1` *(phase 5)* | mark a place's waiting prizes as delivered, with a note |

### 7.2. Bot

- Channel `DISCORD_SEASONS_CHANNEL_ID` (0 = disabled): posts for season start, ending soon, results and reschedules. Winners with a linked Discord account are mentioned.
- Post deduplication: collection `discord_season_posts {season_id, kind, message_id}` — the only thing the bot writes itself.
- `/season info [ladder]`, `/season top [ladder] [season]` — read from Mongo through a single `rating_store.py` shared by all ladders.
- `/season extend|end-at|end-now`, `/season prize set|clear|list|delivered` — `DISCORD_GENERAL_ADMIN_ROLE_ID` only, over RPC.
- `/stats` shows per-ladder ratings instead of `pvp_rating`.

Rule: **only the plugin writes season state.** The bot sends commands and reads. Otherwise a second implementation appears, as happened with account merge.

A server refuses an RPC with a reply carrying `status=error`, `error_code` (`REJECTED` for a request the lifecycle refuses, `FAILED` for anything unexpected) and `error_message`; the bot raises `RpcRejected` and does not ask another server.

## 8. Administration

Server console (any XCore server):

```
season list [ladder]
season info <ladder>
season extend <ladder> <duration> [reason]
season end-at <ladder> <datetime> [reason]
season end-now <ladder> confirm [reason]
season prize set <ladder> <places> <kind> <value>
season prize clear <ladder> <places>
season prize list <ladder> [season]
season prize delivered <ladder> <season> <place> [note]
```

`<duration>` is `90m`, `12h`, `7d`, `2w`, `1mo`; `<datetime>` is `2027-01-01` or `2027-01-01T18:00` in the season time zone. `<places>` is `1` or `1-3`. `prize clear` removes the prizes that lie entirely within the places given. The console takes the prize value only; the description is set from Discord.

Prizes are `SeasonPrize(placeFrom, placeTo, kind, value, description)` with one `PrizeHandler` per kind:

| Kind | Delivery |
|---|---|
| `BADGE` | automatic, through the existing badge system; `value` is the badge id (system badges are refused). The `season-champion` badge ships with phase 5 |
| `CUSTOM` | free text (e.g. Nitro); status `PENDING`, an admin marks it `DELIVERED` |
| `DISCORD_ROLE` | later: the bot grants a role when a Discord account is linked |

Prizes are optional: a season with no `prizes` closes as usual.

Config (the shared `secrets.toml`, not the server-local `xcore.toml`: every server must compute the same schedule):

```toml
[rating.seasons]
length = "3mo"
timezone = "UTC"
notice_thresholds = ["7d", "3d", "24h", "1h"]
settlement_grace = "5m"
podium_size = 10
podium_min_matches = 10
reset = "soft"        # soft | hard | none
reset_carry = 0.5
```

## 9. Work order

1. **Refactor with no behaviour change — done.** Remove the copies in HexedCore; introduce the ladder engine (`LadderService`, `Ladder`, `LadderStore`); port Mini-PVP and HexedCore onto it; migration V5; the shared `LadderTopCategoryProvider` and `LadderLeagueDisplay`; account merge over `rating_standings`. Release XCore-plugin, then bump the dependency in HexedCore.
2. **Season core — done.** Model, repository, resolver, lifecycle, lazy reset, console commands, in-game notifications.
3. **Interface — done.** Scope in the leaderboard SPI and the season switcher; profile sections; `/season`.
4. **Protocol and bot — done.** The `rating` family, the channel, `/season`, the updated `/stats`, rating merge over RPC.
5. **Prizes — done.** Model, handlers, commands, delivery tracking.

Each phase ships separately.

### 9.1. What phase 1 changed for players and operators

Ratings, leagues, notifications and commands are the same. The visible differences:

- The Mini-PVP `/top` lists only players with at least one rated match (it used to list everyone at 1000) and shows a league icon per row, like HexedCore's.
- Ladder leaderboards are read straight from the indexed `rating_standings` collection instead of the Redis leaderboard cache.
- A league icon is drawn from the ladder's cache; a joining player is shown in the starting league for the moment it takes to load their standing.

Rollout: V5 copies the ratings once, at the first start of the new XCore-plugin. A Mini-PVP or HexedCore server still running the old build after that keeps writing to the old fields, and those matches do not reach `rating_standings`. Restart both mode servers on the new builds together.

Known gap until phase 4: the Discord bot's own account merge does not merge `rating_standings`; the plugin's merge does.

### 9.2. What phase 2 changed for players and operators

- Season 1 of each ladder starts when the first server with the new build registers that ladder, and ends one season length later, at midnight in the configured time zone. Move it with `season end-at` if a different date is wanted.
- When a season ends, the next one starts at once. Ratings are soft-reset on a player's first match of the new season; match and win counters start from zero.
- Players on the mode's server see "season ends in …" at each threshold and on join inside the last threshold, and "season N has started" with the reset rule at rollover.
- `/top` and the league icons show the current season only. Past seasons are kept in `rating_standings` with `final_rank` and in `rating_seasons` with the podium; the interface to browse them is phase 3 (9.3).
- Console: `season list`, `season info`, `season extend`, `season end-at`, `season end-now`.

Known gaps:

- The legacy mirror `players.pvp_rating/pvp_matches/pvp_wins` (read by `/stats` and the bot until phases 3 and 4) keeps a player's previous-season values until their first match of the new season.
- `summary.matches` of season 1 counts only matches settled after phase 2 was deployed.
- An account merge after a season is archived moves the standings but does not recompute `final_rank` or the stored podium.
- No protocol events are published yet. The places where each transition is won exactly once (`close`, `claimNotices`, `archive`, `move` in `SeasonLifecycleService`) are where phase 4 publishes them.

### 9.3. What phase 3 changed for players and operators

- `/top` gets a season switcher as soon as a ladder has a second season; past seasons are browsable. The leaderboard now loads off the game thread, so the dialog appears a moment after the command and a page turn keeps the old page on screen until the new one arrives.
- `/stats` shows a rating block per ladder instead of the fixed MiniPvP card: league, progress, matches, wins, rank, peak, time left in the season and the last three seasons. The block appears on the server that hosts the mode, and elsewhere only for players who have played it. The profile's statistics and these blocks are read in one off-thread step.
- `/season` (alias `/seasons`) is new.
- Bundles: new `season-*`, `top-menu-scope-*`, `ladder-profile-*` and `season-menu-*` keys in `en`, `ru`, `uk_UA`; the unused `player-stats-pvp-rating` and `player-stats-pvp-summary` keys are removed. Other locales fall back to English.
- API for other modes: `LadderViews.topCategory/profileSection`, `ProfileSectionRegistry`, `TopCategoryProvider.scopes`. `LadderTopCategoryProvider` moved from `rating.ladder` to `rating.view` and is built through `LadderViews`; `MiniPvPLadder` and `HexedLadder` take `LadderViews` instead of `PlayerDataRepository`.

Known gaps:

- HexedCore's section and league names use XCore's `rating_league_*` texts; the `hexed_league_*` ones are used only by HexedCore's own menus.
- The legacy `players.pvp_*` mirror is still written for the bot until phase 4; `/stats` no longer reads it except for the "legacy" figure.
- Only the last three seasons are listed in a profile and the previous season's top three in `/season`; the full archive is in `/top`.
- A profile for a player of another mode's server shows a block only when that player has a standing; there is no per-server hiding beyond that.

### 9.4. What phase 4 changed for players and operators

- Every season transition is published once per network, however many servers run the mode: `started` (when a season opens after another), `ending-soon` (the most urgent threshold that became due), `ended` (podium and summary of the archived season) and `rescheduled` (every move of the end, from the console or the bot). The publisher is `SeasonTransportPublisher`, a `SeasonEvents` listener of `SeasonLifecycleService`; a failing listener never blocks a transition.
- Head admins (`DISCORD_GENERAL_ADMIN_ROLE_ID`) can `extend`, `end-at` and `end-now` a season from Discord. The request goes to any live server (the bot tries up to three); the actor is recorded in the season's audit trail like a console actor.
- The bot posts the announcements to `DISCORD_SEASONS_CHANNEL_ID`, once per season and kind (`discord_season_posts`), mentioning podium winners who linked Discord. `/season info|top` read Mongo; `/stats` shows each ladder's rating and place for the current season.
- The bot's account merge now also moves `rating_standings` through `RatingAccountsMergeRequestV1`. If no server answers, the merge waits in `rating_merge_pending` and is retried every minute; the merge embed says which of the cases happened.
- Console `season extend` shares its implementation with the bot's RPC (`SeasonLifecycleService.extend`).

Known gaps:

- Event delivery is at-most-once from the plugin: a server that dies right after winning a transition loses its event. The bot's dedup protects against replays, not against a lost event; a reconcile pass over `rating_seasons` is the follow-up if this ever matters.
- The first season of a ladder, created when a server first registers the ladder, publishes no `started` event.
- `season end-now` publishes `ended` and then `started` of the next season, not `rescheduled`.
- `extend` is relative to the end read just before the compare-and-set; two simultaneous extends can both apply against the same base and one wins.
- The legacy `players.pvp_*` mirror is still written; nothing but the merge embed reads it now, so it can be removed once phase 5 is out.
- `PROTOCOL_SURFACE.md` is not regenerated for the new family.

### 9.5. What phase 5 changed for players and operators

- A season can carry prizes for places or ranges of places. They are added and removed on the running season from the console (`season prize set|clear|list`) or from Discord by head admins (`/season prize ...`); both go through `SeasonLifecycleService.addPrize/removePrizes`, which edit the season by compare-and-set and write an audit record.
- `BADGE` prizes are validated when they are set (the badge must exist and not be a system badge) and are unlocked automatically when the season is archived, on every server at once (`PlayerBadgeInventoryChangedCommandV1`). The new `season-champion` badge is an achievement badge a player can select like the manual ones.
- `CUSTOM` prizes (a gift code, a sticker pack) create a `PENDING` grant per winner. The winner's Discord account is in the `ended` post and in `season prize list`; an admin hands the prize over and records it with `season prize delivered` or `/season prize delivered`.
- The ending-soon and results posts list the prizes, so players see what they play for.

Known gaps:

- Delivery is by place, not per winner: `delivered` settles every waiting grant of that place. Two players sharing a range each get their own grant, but they are marked together.
- The in-game `/season` card, `/top` and the winner's profile do not show prizes yet.
- A prize edited after the season has ended changes nothing: the grants were created from the prizes at archive time.
- Prizes can only be edited while the season is running; once it is closing they are final. A new season starts with no prizes, so they have to be set again each season.
- The legacy `players.pvp_*` mirror is still written, now only for the merge embed. Removing it is a separate migration.

## 10. Decisions

All five proposals were accepted.

1. **Season length.** 3 months, configurable.
2. **Rating reset.** Soft: `1000 + (old − 1000) × 0.5`; match counters start from zero.
3. **Seasons across the two modes.** A separate season per ladder with the same default schedule — they normally coincide, but one can be moved on its own.
4. **Who counts as a head administrator.** Seasons and prizes are managed only from the console and from Discord under `DISCORD_GENERAL_ADMIN_ROLE_ID`, with no in-game chat command.
5. **Account merge in the bot.** Moves to an RPC to the plugin, so there is one implementation.
