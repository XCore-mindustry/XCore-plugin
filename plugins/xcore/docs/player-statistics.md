# Materialized player statistics

`games_v2` is authoritative. `player_totals` is a rebuildable projection owned by the plugin.
`ParticipantOutcomes` implements policy v1; `PlayerTotals` is the shared live/replay fold.
Reconnects are unioned, each owner gets at most one game, and `games = wins + losses + left`.
Participation requires max(60 seconds, 10% of match duration). A player still present at the
end also needs 25%. Valid Hexed placement bypasses time thresholds, but not match exclusion.
Survival win rate uses completed results; PvP, Hexed, Event and overall rates use all counted
participation. Stored outcomes are historical policy snapshots; rebuild recomputes from raw data.

## Rollout

Defaults deliberately keep legacy readers and the materialized writer disabled. Install the
updated plugin on **every** server and update the Discord bot before enabling the writer.
MongoDB must be a replica set. Standalone deployments cannot run the materialized writer.
Never enable it on only one server while others still write without revision invalidation.

Server `xcore.toml`:

```toml
[statistics]
enabled = true
readEnabled = false
historyOwner = "map"
```

`historyOwner = "map"` makes the map lifecycle authoritative, including Mini-PvP. Its separate
rating report remains in `rating_matches`; it does not produce a second game in `games_v2`.
Set `historyOwner = "external"` on HexedCore: round settlement owns its history and generic map
history is disabled. Update the satellite to use `recordMatchAsync`. Do not toggle owner in the
middle of a match. A plugin-owned record must carry its own stable match ID.
While `enabled = false`, `historyOwner` is ignored and the legacy rule holds: a plugin-owned
record is stored only when no map-lifecycle game is running, and no game starts on `PlayEvent`.
Set `historyOwner = "external"` on HexedCore in the same change that sets `enabled = true`.

Migration V8 creates indexes and marks the initial backfill due. An enabled plugin performs the
scan off the game thread; no historical game is rewritten by the migration. After all old
writers and their queued writes have stopped, run `stats-rebuild all` for the final catch-up.
`stats-rebuild <uuid>` queues one player. Multiple servers share leases in MongoDB.
`stats-audit` is read-only and reports normalization/outcome reasons; run against a restored
backup before changing production policy. It prints counts, not player identifiers.

Wait until `player_totals` has no `rebuild.dirty: true` documents and inspect errors/old leases.
Compare sampled profiles against rebuild. Then set `statistics.readEnabled = true` on game
servers, `STATS_MATERIALIZED=true` for the Discord bot, and `XCORE_STATS_MATERIALIZED=true` for
the web service. Restart the corresponding applications. A missing document means the player
has no games yet, since every stored game upserts its owners in the same transaction. A pending
document shows recalculation; a ready dirty one shows the previous snapshot with an updating notice.
Database errors remain errors. The legacy query paths exist only for this rollout/rollback.

## Consistency and recovery

History insert, ownership redirects and incremental projection updates share a strict Mongo
transaction. Unique `match_id` plus the original payload hash protects retries even after
more than ten matches. Reusing an ID for different input is an error. `recent` is only for UI.
Out-of-order matches mark the projection dirty to preserve chronological streaks.

A match write that fails on a network, election or capacity error is retried twice, after 5 and
30 seconds. A replay of a commit whose reply was lost lands on the unique `match_id` and counts as
a duplicate. After the last attempt the match ID is logged and `xcore_stats_writes_total{result=failed}`
grows. A failed rebuild waits 30 seconds, doubling per attempt up to 30 minutes.

Rebuild claims an expiring lease, reads revision before history, and publishes by revision/token
CAS. Writes and both account merge implementations invalidate the same document. A new write,
merge or invalidation prevents stale rebuild publication. Expired workers cannot clear a new
worker's lease. Sources retain `redirect_uuid` for late snapshots and chained account merges.
Both merge implementations refuse a merge whose target already redirects back to the source;
the plugin checks this before its first write, so a refused merge changes nothing even without
a transaction.
Never delete those redirects as part of an ordinary reset. Changes/deletions to games made
outside the supported APIs must invalidate all affected owners before new readers resume.

Policy changes require increasing `ParticipantOutcomes.RULES_VERSION` and `stats-rebuild all`.
Workers never claim totals published under a newer rules version, so servers may be upgraded one
at a time; run the rebuild once at least one server has the new version.
Schema changes need an explicit reader migration; readers reject unknown ready schemas.
Projection counters use BSON int64. The web reader rejects integers outside JavaScript's safe
range instead of rounding them. Java/Python/TS tests share
`src/test/resources/fixtures/player-totals.v1.json`.

For rollback, switch readers back, disable the writer/worker and retain history and redirects.
Before enabling the writer again after any legacy writes, run a full rebuild. The legacy reader
will again show its known counting defects until materialized reading resumes. It has no
left, streak or play-time figures, so the profile, the bot and the website leave those lines out.

## Metrics

- `xcore_stats_writes_total{result}`: stored, duplicate, retried, failed.
- `xcore_stats_rebuilds_total{result}`: published, superseded, failed; `xcore_stats_rebuild_duration_seconds`.
- `xcore_stats_dirty_players` and `xcore_stats_oldest_dirty_seconds`, sampled once a minute
  by servers running the worker. A growing oldest age means the worker is stuck or failing.
  A player keeps their place in the queue while new matches arrive, so the age is real.

## Known limits

A process crash before the raw snapshot reaches Mongo can lose that snapshot; rebuild cannot
restore unrecorded history. StorageExecutor drains accepted work on graceful shutdown, but this
is not a durable disk outbox. Synchronous legacy persistence APIs remain for binary integration
compatibility; internal gameplay uses the async path. Storage errors/rejection are logged.

V1 outcomes use the final team, and do not prevent switching to a winning team. Team changes
are sampled every 5 seconds, so a switch and back inside that window is not recorded; games
stored before this change have no team timeline. Simultaneous opposing identities merged into one owner are
excluded rather than guessing a result. Historical invalid dates and missing placement stay
visible through audit reasons. The statistics policy does not alter Elo settlements.

## Local verification (2026-11-07)

- Plugin: 2,574 JUnit tests passed, including 11 replica-set projection integration tests
  (merge chains, refused cycles, rebuild backoff, queue order, newer rules left alone); shadowJar built.
- Discord bot: 1,901 tests passed, 2 skipped; Ruff checks passed.
- Web: 76 tests passed; TypeScript and ESLint passed.
- The classifier was also dry-run read-only over a restored backup in a temporary local
  container. Live production backfill and graphical client screenshots were not performed.
