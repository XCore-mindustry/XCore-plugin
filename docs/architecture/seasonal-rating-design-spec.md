# Technical Design Specification: Seasonal Rating (Mini-PVP and HexedCore)

Status: decisions in section 10 accepted; phase 1 implemented (see 9.1), phases 2–5 pending.
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
│   ├── LadderTopCategoryProvider   the /top category of any ladder
│   └── LadderLeagueDisplay    the league icon next to a player's name
├── season/
│   ├── Season, SeasonStatus, SeasonRepository
│   ├── SeasonSchedule         length, time zone, notification thresholds
│   ├── SeasonResolver         the ladder's active season at a point in time
│   ├── SeasonResetPolicy      starting rating for a new season
│   └── SeasonLifecycleService tick: notifications, closing, opening
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

1. The season is resolved (phase 1: always season 1; phase 2: `SeasonResolver` from the match end time).
2. `PluginIdempotencyLedger.claim` on the operation `{ladder}:{matchId}:rating:{algorithm}` in the shared `rating` ledger. An unrated match is claimed and marked skipped, so it is also seen exactly once.
3. For each participant, `LadderStore.applyOnce` into `rating_standings` (the record is created with a lazy reset, see 5.2). The store applies a rating *delta* and clamps to the policy minimum, so it never overwrites a concurrent change.
4. `ledger.markCompleted`.

The result tells the mode whether it `claimed` the match (it then records history) and whether standings were `applied` (it then notifies players and refreshes icons). A settlement interrupted between two players is finished by a retry: the ledger lease is taken over and `applyOnce` skips the players already updated.

Mini-PVP gains the idempotency it lacked, and its settlement now runs off the game thread.

### 3.3. Three key decisions

- **The archive is free.** A past season is the same `rating_standings` documents that are no longer written to. A past season's leaderboard is the same query with a different `season`. There is no separate archive code or archive collection.
- **The reset is lazy.** Nothing is rewritten in bulk when a season rolls over. A player's record in the new season is created on their first match, from their latest rating, according to `SeasonResetPolicy`.
- **Closing happens exactly once.** The tick runs on every XCore server over all seasons in `rating_seasons`, regardless of which mode that server hosts. One server claims the transition through the ledger with a lease. The season closes even if the server hosting that mode is down.

## 4. Data (MongoDB)

### 4.1. `rating_seasons`

```
_id            "{ladder}:{number}"
ladder         "minipvp" | "hexed"
number         1, 2, 3...
name           optional display name
starts_at      epoch ms
ends_at        epoch ms (can be moved)
status         ACTIVE | CLOSING | ARCHIVED
sent_notices   ["7d", "3d", "24h", "1h"]   — thresholds already sent
prizes         [{place_from, place_to, kind, payload, description}]
podium         [{place, uuid, pid, nickname, rating, league, matches, wins,
                 discord_id, discord_username}]   — snapshot taken at close
summary        {participants, matches}
rescheduled    [{from, to, actor, at, reason}]
revision       for optimistic locking
```

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
prize        {kind, payload, description}
status       PENDING | GRANTED | DELIVERED | FAILED
granted_by, updated_at, note
```

### 4.4. Migration V5

- `players.pvp_rating/pvp_matches/pvp_wins` → `rating_standings {ladder: "minipvp", season: 1}`;
- `xcore_plugin_hexedcore_rating_players` → `rating_standings {ladder: "hexed", season: 1}`;
- only players with at least one rated match are copied; `merged:` accounts are skipped;
- existing standings are never overwritten (`$merge … whenMatched: keepExisting`), so the migration is safe to re-run;
- the old fields and collection are left untouched until the next release (rollback), then removed by a separate migration;
- the season 1 documents in `rating_seasons` are created in phase 2, together with the collection.

Until phases 3 and 4, Mini-PVP standings are mirrored back into `players.pvp_rating/pvp_matches/pvp_wins` after every settlement (`LegacyPvpRatingMirror`): the profile menu and the Discord bot still read them. The ladder is the source of truth.

## 5. Season lifecycle

```
ACTIVE ──(now >= ends_at)──> CLOSING ──(settlement grace elapsed)──> ARCHIVED
                                │                                        │
                                └── season N+1 is created as ACTIVE ─────┘
```

### 5.1. Closing

1. `ACTIVE → CLOSING` atomically (`findOneAndUpdate` on `status` and `revision`).
2. Season N+1 is created immediately (`starts_at` = season N's `ends_at`) so that new matches land in it.
3. A settlement grace window (5 minutes by default) for matches that finished before `ends_at` but have not been settled yet.
4. Finalisation under the ledger operation `season:{id}:finalize`: set `final_rank`, build `podium` (top N, 10 by default) with a snapshot of nickname, pid and Discord account, compute `summary`.
5. Create `rating_prize_grants` from `prizes` and run `PrizeHandler` for the automatic kinds.
6. `CLOSING → ARCHIVED`, emit `RatingSeasonEndedV1`.

A match belongs to a season by its end time. A match started in season N and finished after `ends_at` counts towards season N+1.

Podium eligibility: a minimum number of matches in the season (10 by default), so that first place cannot be taken by a player with one match and a carried-over rating.

### 5.2. Lazy reset

On a player's first match in season N, their latest record in that ladder is looked up:

- no record → `policy.defaultRating()`;
- a record exists → `SeasonResetPolicy.seed(previousRating)`.

Policy options: soft reset `default + (prev − default) × carry` (`carry = 0.5` by default), hard reset, no reset. Match and win counters always start from zero.

League shown for a player who has not played in the new season yet: derived from the computed starting rating, marked "uncalibrated".

### 5.3. Notifications

Thresholds come from config (`7d`, `3d`, `24h`, `1h` by default).

- **Discord**: the event is published by the server that atomically added the threshold to `sent_notices` (`$addToSet` conditional on absence) — exactly once.
- **In game**: every server that has the ladder registered announces on its own when a threshold is crossed; plus a line on player join when less than the largest threshold remains.

### 5.4. Moving the season end

`ends_at` is changed atomically by `revision`. Rules:

- a date in the past is rejected — ending early is a separate `end-now` command;
- a season in `CLOSING`/`ARCHIVED` cannot be changed;
- thresholds that are in the future again are removed from `sent_notices`;
- an entry is appended to `rescheduled`, an audit record is written, and `RatingSeasonRescheduledV1` is emitted.

## 6. In-game interface

### 6.1. `/top`

SPI extension:

- `TopCategoryProvider.scopes(Localization)` → a list of `TopScope(id, label)`; by default a single unnamed scope (backward compatible for `PLAYTIME` and the legacy `HEXED`);
- `LeaderboardPageRequest.scopeId`.

One shared `LadderTopCategoryProvider(LadderDefinition)` serves both modes. The menu gets a "◀ Season 3 ▶" switcher above the list; for archived seasons the prize, if any, is shown next to the placement.

The `switch` on category id is removed from `TopUiController`: the viewer's own value comes from `LeaderboardPage.selfPrimaryValue`.

### 6.2. `/stats`

A `ProfileSectionProvider` SPI (registered the same way as `PlayerDisplayRegistry`) and a shared `LadderProfileSection`:

- current season: rating, league, progress to the next league, rank, matches / wins / win rate, peak;
- time left until the season ends;
- history: "Season 2 — #4, Titanium, 1642" and prizes received.

Only the PvP block is extracted from `PlayerProfileUiController`; the rest of the profile is left alone.

### 6.3. `/season` command

For players: the current season of this server's ladder, time remaining, prizes, own rank.

## 7. Discord

### 7.1. Protocol (`xcore-protocol`, new `rating` family)

Events (`xcore:evt:rating:*`, replayable):

| Message | Content |
|---|---|
| `RatingSeasonStartedV1` | ladder, season, startsAt, endsAt, prizes |
| `RatingSeasonEndingSoonV1` | ladder, season, endsAt, threshold |
| `RatingSeasonEndedV1` | ladder, season, podium[] (`PlayerRefV1`, `DiscordIdentityRefV1?`, rating, league, matches, wins, prize?), summary |
| `RatingSeasonRescheduledV1` | ladder, season, oldEndsAt, newEndsAt, actor, reason |

RPC (bot → any live server):

| Request | Purpose |
|---|---|
| `RatingSeasonRescheduleRequestV1` | extend / set a date / end now |
| `RatingSeasonPrizesSetRequestV1` | set the season's prize list |
| `RatingPrizeGrantUpdateRequestV1` | mark a prize as delivered |

### 7.2. Bot

- Channel `DISCORD_SEASONS_CHANNEL_ID` (0 = disabled): posts for season start, ending soon, results and reschedules. Winners with a linked Discord account are mentioned.
- Post deduplication: collection `discord_season_posts {season_id, kind, message_id}` — the only thing the bot writes itself.
- `/season info [ladder]`, `/season top [ladder] [season]` — read from Mongo through a single `rating_store.py` shared by all ladders.
- `/season extend|end-at|end-now`, `/season prize set|clear|list|delivered` — `DISCORD_GENERAL_ADMIN_ROLE_ID` only, over RPC.
- `/stats` shows per-ladder ratings instead of `pvp_rating`.

Rule: **only the plugin writes season state.** The bot sends commands and reads. Otherwise a second implementation appears, as happened with account merge.

## 8. Administration

Server console (any XCore server):

```
season list [ladder]
season info <ladder>
season extend <ladder> <duration>
season end-at <ladder> <datetime>
season end-now <ladder> confirm
season prize set <ladder> <places> <kind> <payload> [description]
season prize clear <ladder> [places]
season prize list <ladder> [season]
season prize delivered <season> <place>
```

Prizes are `SeasonPrize(place_from, place_to, kind, payload, description)` with one handler per kind:

| Kind | Delivery |
|---|---|
| `BADGE` | automatic, through the existing badge system (needs new badges, e.g. `season-champion`) |
| `CUSTOM` | free text (e.g. Nitro); status `PENDING`, an admin marks it `DELIVERED` |
| `DISCORD_ROLE` | later: the bot grants a role when a Discord account is linked |

Prizes are optional: a season with no `prizes` closes as usual.

Config (`xcore.toml`):

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
2. **Season core.** Model, repository, resolver, lifecycle, lazy reset, console commands, in-game notifications.
3. **Interface.** Scope in the leaderboard SPI and the season switcher; profile sections; `/season`.
4. **Protocol and bot.** The `rating` family, the channel, `/season`, the updated `/stats`.
5. **Prizes.** Model, handlers, commands, delivery tracking.

Each phase ships separately.

### 9.1. What phase 1 changed for players and operators

Ratings, leagues, notifications and commands are the same. The visible differences:

- The Mini-PVP `/top` lists only players with at least one rated match (it used to list everyone at 1000) and shows a league icon per row, like HexedCore's.
- Ladder leaderboards are read straight from the indexed `rating_standings` collection instead of the Redis leaderboard cache.
- A league icon is drawn from the ladder's cache; a joining player is shown in the starting league for the moment it takes to load their standing.

Rollout: V5 copies the ratings once, at the first start of the new XCore-plugin. A Mini-PVP or HexedCore server still running the old build after that keeps writing to the old fields, and those matches do not reach `rating_standings`. Restart both mode servers on the new builds together.

Known gap until phase 4: the Discord bot's own account merge does not merge `rating_standings`; the plugin's merge does.

## 10. Decisions

All five proposals were accepted.

1. **Season length.** 3 months, configurable.
2. **Rating reset.** Soft: `1000 + (old − 1000) × 0.5`; match counters start from zero.
3. **Seasons across the two modes.** A separate season per ladder with the same default schedule — they normally coincide, but one can be moved on its own.
4. **Who counts as a head administrator.** Seasons and prizes are managed only from the console and from Discord under `DISCORD_GENERAL_ADMIN_ROLE_ID`, with no in-game chat command.
5. **Account merge in the bot.** Moves to an RPC to the plugin, so there is one implementation.
